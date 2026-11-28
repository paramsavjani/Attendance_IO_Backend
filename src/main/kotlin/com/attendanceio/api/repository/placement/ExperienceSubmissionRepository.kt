package com.attendanceio.api.repository.placement

import com.attendanceio.api.model.placement.DMExperienceSubmission
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
interface ExperienceSubmissionRepository : JpaRepository<DMExperienceSubmission, Long> {
    fun findByStudentIdOrderByCreatedAtDesc(studentId: Long): List<DMExperienceSubmission>
    fun countByStudentIdAndCreatedAtAfter(studentId: Long, after: LocalDateTime): Long
}
