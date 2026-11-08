package com.attendanceio.api.repository.poll

import com.attendanceio.api.model.poll.DMPoll
import com.attendanceio.api.model.poll.DMPollOption
import com.attendanceio.api.model.poll.DMPollReport
import com.attendanceio.api.model.poll.DMPollVote
import com.attendanceio.api.model.student.DMStudent
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDateTime

@Component
class PollRepositoryAppAction(
    private val pollRepository: PollRepository,
    private val pollOptionRepository: PollOptionRepository,
    private val pollVoteRepository: PollVoteRepository,
    private val pollReportRepository: PollReportRepository
) {
    /** Totals for a batch of polls, gathered in a fixed number of queries. */
    data class Tallies(
        val votesByOption: Map<Long, Int>,
        val votersByPoll: Map<Long, Int>,
        val myChoices: Map<Long, List<Long>>,
        val reportedByMe: Set<Long>
    )

    @Transactional(readOnly = true)
    fun findById(pollId: Long): DMPoll? = pollRepository.findById(pollId).orElse(null)

    @Transactional(readOnly = true)
    fun findFeed(subjectIds: Collection<Long>, studentId: Long, endedAfter: Instant, limit: Int): List<DMPoll> {
        if (subjectIds.isEmpty()) return emptyList()
        return pollRepository.findFeed(subjectIds, studentId, endedAfter, PageRequest.of(0, limit))
    }

    @Transactional(readOnly = true)
    fun countCreatedSince(creatorId: Long, since: LocalDateTime): Int =
        pollRepository.countByCreatorIdAndCreatedAtAfter(creatorId, since).toInt()

    @Transactional(readOnly = true)
    fun optionsFor(pollIds: Collection<Long>): Map<Long, List<DMPollOption>> {
        if (pollIds.isEmpty()) return emptyMap()
        return pollOptionRepository.findByPollIdInOrderByPositionAsc(pollIds).groupBy { it.poll?.id ?: 0L }
    }

    @Transactional(readOnly = true)
    fun talliesFor(pollIds: Collection<Long>, studentId: Long): Tallies {
        if (pollIds.isEmpty()) return Tallies(emptyMap(), emptyMap(), emptyMap(), emptySet())
        val votesByOption = pollVoteRepository.countByOption(pollIds)
            .associate { (it[1] as Long) to (it[2] as Long).toInt() }
        val votersByPoll = pollVoteRepository.countVoters(pollIds)
            .associate { (it[0] as Long) to (it[1] as Long).toInt() }
        val myChoices = pollVoteRepository.findChoices(studentId, pollIds)
            .groupBy({ it[0] as Long }, { it[1] as Long })
        val reported = pollReportRepository.findReportedPollIds(studentId, pollIds).toSet()
        return Tallies(votesByOption, votersByPoll, myChoices, reported)
    }

    @Transactional
    fun create(poll: DMPoll, optionTexts: List<String>): DMPoll {
        val saved = pollRepository.save(poll)
        pollOptionRepository.saveAll(optionTexts.mapIndexed { index, text ->
            DMPollOption().apply {
                this.poll = saved
                this.text = text
                this.position = index
            }
        })
        return saved
    }

    /** Replaces the student's whole ballot, so switching options is a single atomic step. */
    @Transactional
    fun replaceBallot(poll: DMPoll, student: DMStudent, options: List<DMPollOption>) {
        val pollId = poll.id ?: return
        val studentId = student.id ?: return
        pollVoteRepository.deleteBallot(pollId, studentId)
        pollVoteRepository.flush()
        pollVoteRepository.saveAll(options.map { option ->
            DMPollVote().apply {
                this.poll = poll
                this.option = option
                this.student = student
            }
        })
    }

    @Transactional
    fun clearBallot(pollId: Long, studentId: Long) {
        pollVoteRepository.deleteBallot(pollId, studentId)
    }

    @Transactional
    fun save(poll: DMPoll): DMPoll = pollRepository.save(poll)

    @Transactional
    fun delete(poll: DMPoll) {
        val pollId = poll.id ?: return
        pollVoteRepository.deleteByPollId(pollId)
        pollReportRepository.deleteByPollId(pollId)
        pollOptionRepository.deleteByPollId(pollId)
        pollRepository.delete(poll)
    }

    @Transactional(readOnly = true)
    fun hasReported(pollId: Long, studentId: Long): Boolean =
        pollReportRepository.existsByPollIdAndStudentId(pollId, studentId)

    /** Records the report and returns how many distinct classmates have now reported the poll. */
    @Transactional
    fun addReport(report: DMPollReport): Int {
        pollReportRepository.save(report)
        pollReportRepository.flush()
        return pollReportRepository.countByPollId(report.poll?.id ?: 0L).toInt()
    }
}
