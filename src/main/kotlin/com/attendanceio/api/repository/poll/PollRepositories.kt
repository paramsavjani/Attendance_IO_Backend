package com.attendanceio.api.repository.poll

import com.attendanceio.api.model.poll.DMPoll
import com.attendanceio.api.model.poll.DMPollOption
import com.attendanceio.api.model.poll.DMPollReport
import com.attendanceio.api.model.poll.DMPollVote
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface PollRepository : JpaRepository<DMPoll, Long> {
    fun findBySubjectIdOrderByCreatedAtDesc(subjectId: Long): List<DMPoll>
    fun findByCreatedByIdOrderByCreatedAtDesc(studentId: Long): List<DMPoll>
}

@Repository
interface PollOptionRepository : JpaRepository<DMPollOption, Long> {
    fun findByPollIdOrderByDisplayOrderAsc(pollId: Long): List<DMPollOption>
}

@Repository
interface PollVoteRepository : JpaRepository<DMPollVote, Long> {
    fun findByPollIdAndStudentId(pollId: Long, studentId: Long): List<DMPollVote>
    fun findByPollId(pollId: Long): List<DMPollVote>

    @Modifying
    @Query("DELETE FROM DMPollVote pv WHERE pv.poll.id = :pollId AND pv.student.id = :studentId")
    fun deleteByPollIdAndStudentId(@Param("pollId") pollId: Long, @Param("studentId") studentId: Long)

    @Modifying
    @Query("DELETE FROM DMPollVote pv WHERE pv.poll.id = :pollId")
    fun deleteByPollId(@Param("pollId") pollId: Long)
}

@Repository
interface PollReportRepository : JpaRepository<DMPollReport, Long> {
    fun countByPollId(pollId: Long): Long
    fun existsByPollIdAndStudentId(pollId: Long, studentId: Long): Boolean

    @Modifying
    @Query("DELETE FROM DMPollReport pr WHERE pr.poll.id = :pollId")
    fun deleteByPollId(@Param("pollId") pollId: Long)
}
