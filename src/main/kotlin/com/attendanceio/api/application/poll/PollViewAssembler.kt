package com.attendanceio.api.application.poll

import com.attendanceio.api.model.poll.DMPoll
import com.attendanceio.api.model.poll.PollOptionResponse
import com.attendanceio.api.model.poll.PollResponse
import com.attendanceio.api.model.poll.PollSubjectResponse
import com.attendanceio.api.repository.poll.PollRepositoryAppAction
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.ZoneId

/**
 * Builds what a student sees of a poll. Live totals are always shown, voted or not; who voted
 * for what is never exposed.
 */
@Component
class PollViewAssembler(
    private val pollRepositoryAppAction: PollRepositoryAppAction
) {
    fun toResponses(polls: List<DMPoll>, viewerId: Long, now: Instant = Instant.now()): List<PollResponse> {
        val pollIds = polls.mapNotNull { it.id }
        val options = pollRepositoryAppAction.optionsFor(pollIds)
        val tallies = pollRepositoryAppAction.talliesFor(pollIds, viewerId)

        return polls.mapNotNull { poll ->
            val pollId = poll.id ?: return@mapNotNull null
            val subject = poll.subject ?: return@mapNotNull null
            val isMine = poll.creator?.id == viewerId
            val myOptionIds = tallies.myChoices[pollId].orEmpty()
            val isOpen = poll.isOpen(now)

            PollResponse(
                id = pollId,
                subject = PollSubjectResponse(
                    id = subject.id ?: 0L,
                    code = subject.code,
                    name = subject.name,
                    color = subject.color
                ),
                question = poll.question,
                options = options[pollId].orEmpty().map { option ->
                    val optionId = option.id ?: 0L
                    PollOptionResponse(
                        id = optionId,
                        text = option.text,
                        votes = tallies.votesByOption[optionId] ?: 0
                    )
                },
                allowMultiple = poll.allowMultiple,
                anonymous = poll.anonymous,
                creatorName = if (poll.anonymous && !isMine) null else displayName(poll.creator?.name),
                isMine = isMine,
                totalVoters = tallies.votersByPoll[pollId] ?: 0,
                myOptionIds = myOptionIds,
                isOpen = isOpen,
                hidden = poll.hidden,
                reportedByMe = pollId in tallies.reportedByMe,
                createdAt = poll.createdAt?.atZone(ZoneId.systemDefault())?.toInstant()?.toEpochMilli() ?: now.toEpochMilli(),
                endsAt = poll.endsAt.toEpochMilli()
            )
        }
    }

    fun toResponse(poll: DMPoll, viewerId: Long): PollResponse = toResponses(listOf(poll), viewerId).first()

    companion object {
        /** First two words of the stored name, the way the home screen greets people. */
        fun displayName(name: String?): String {
            val words = name?.trim()?.split(Regex("\\s+"))?.filter { it.isNotEmpty() }.orEmpty()
            return if (words.isEmpty()) "A classmate" else words.take(2).joinToString(" ")
        }
    }
}
