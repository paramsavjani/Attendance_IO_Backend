package com.attendanceio.api.application.poll.actions

import com.attendanceio.api.application.poll.PollAccess
import com.attendanceio.api.application.poll.PollRules
import com.attendanceio.api.application.poll.PollViewAssembler
import com.attendanceio.api.model.poll.PollFeedResponse
import com.attendanceio.api.repository.poll.PollRepositoryAppAction
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.LocalDateTime

@Component
class GetPollFeedAppAction(
    private val pollRepositoryAppAction: PollRepositoryAppAction,
    private val pollAccess: PollAccess,
    private val pollViewAssembler: PollViewAssembler
) {
    /** Open polls first (newest at the top), then recently ended ones (most recently ended first). */
    fun execute(studentId: Long, canWrite: Boolean): PollFeedResponse {
        val now = Instant.now()
        val subjectIds = pollAccess.currentSubjectIds(studentId)
        val polls = pollRepositoryAppAction.findFeed(
            subjectIds,
            studentId,
            now.minus(PollRules.ENDED_VISIBLE_FOR),
            PollRules.FEED_LIMIT
        )
        val responses = pollViewAssembler.toResponses(polls, studentId, now)
        val (open, ended) = responses.partition { it.isOpen }
        val createdToday = if (canWrite) pollRepositoryAppAction.countCreatedSince(studentId, LocalDateTime.now().minusDays(1)) else 0
        val blocked = canWrite && pollRepositoryAppAction.countHiddenBy(studentId) > PollRules.HIDDEN_POLLS_ALLOWED

        return PollFeedResponse(
            polls = open.sortedByDescending { it.createdAt } + ended.sortedByDescending { it.endsAt },
            unvotedOpenCount = open.count { !it.isMine && it.myOptionIds.isEmpty() },
            canCreate = canWrite && !blocked && subjectIds.isNotEmpty() && createdToday < PollRules.DAILY_CREATE_LIMIT,
            blocked = blocked,
            createdToday = createdToday,
            dailyLimit = PollRules.DAILY_CREATE_LIMIT
        )
    }
}
