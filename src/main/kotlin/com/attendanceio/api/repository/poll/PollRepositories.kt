package com.attendanceio.api.repository.poll

import com.attendanceio.api.model.poll.DMPoll
import com.attendanceio.api.model.poll.DMPollOption
import com.attendanceio.api.model.poll.DMPollReport
import com.attendanceio.api.model.poll.DMPollVote
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.Instant
import java.time.LocalDateTime

@Repository
interface PollRepository : JpaRepository<DMPoll, Long> {
    /** Polls for the given subjects still worth showing: open, or ended after [endedAfter]. */
    @Query(
        """
        SELECT p FROM DMPoll p
        WHERE p.subject.id IN :subjectIds
          AND p.deletedAt IS NULL
          AND (p.hidden = false OR p.creator.id = :studentId)
          AND COALESCE(p.closedAt, p.closesAt) > :endedAfter
        ORDER BY p.createdAt DESC
        """
    )
    fun findFeed(
        @Param("subjectIds") subjectIds: Collection<Long>,
        @Param("studentId") studentId: Long,
        @Param("endedAfter") endedAfter: Instant,
        pageable: Pageable
    ): List<DMPoll>

    fun countByCreatorIdAndCreatedAtAfter(creatorId: Long, after: LocalDateTime): Long

    fun countByCreatorIdAndHiddenTrue(creatorId: Long): Long
}

@Repository
interface PollOptionRepository : JpaRepository<DMPollOption, Long> {
    fun findByPollIdInOrderByPositionAsc(pollIds: Collection<Long>): List<DMPollOption>
}

@Repository
interface PollVoteRepository : JpaRepository<DMPollVote, Long> {
    /** Rows of [pollId, optionId, votes]. */
    @Query("SELECT v.poll.id, v.option.id, COUNT(v) FROM DMPollVote v WHERE v.poll.id IN :pollIds AND v.removedAt IS NULL GROUP BY v.poll.id, v.option.id")
    fun countByOption(@Param("pollIds") pollIds: Collection<Long>): List<Array<Any>>

    /** Rows of [pollId, distinct voters]. */
    @Query("SELECT v.poll.id, COUNT(DISTINCT v.student.id) FROM DMPollVote v WHERE v.poll.id IN :pollIds AND v.removedAt IS NULL GROUP BY v.poll.id")
    fun countVoters(@Param("pollIds") pollIds: Collection<Long>): List<Array<Any>>

    /** Rows of [pollId, optionId] the student picked. */
    @Query("SELECT v.poll.id, v.option.id FROM DMPollVote v WHERE v.student.id = :studentId AND v.poll.id IN :pollIds AND v.removedAt IS NULL")
    fun findChoices(@Param("studentId") studentId: Long, @Param("pollIds") pollIds: Collection<Long>): List<Array<Any>>

    /** Retires the student's current ballot. The rows stay as history. */
    @Modifying
    @Query("UPDATE DMPollVote v SET v.removedAt = :now WHERE v.poll.id = :pollId AND v.student.id = :studentId AND v.removedAt IS NULL")
    fun retireBallot(@Param("pollId") pollId: Long, @Param("studentId") studentId: Long, @Param("now") now: Instant): Int
}

@Repository
interface PollReportRepository : JpaRepository<DMPollReport, Long> {
    fun existsByPollIdAndStudentId(pollId: Long, studentId: Long): Boolean

    fun countByPollId(pollId: Long): Long

    @Query("SELECT r.poll.id FROM DMPollReport r WHERE r.student.id = :studentId AND r.poll.id IN :pollIds")
    fun findReportedPollIds(@Param("studentId") studentId: Long, @Param("pollIds") pollIds: Collection<Long>): List<Long>
}
