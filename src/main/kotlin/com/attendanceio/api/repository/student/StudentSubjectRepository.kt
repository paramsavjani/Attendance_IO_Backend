package com.attendanceio.api.repository.student

import com.attendanceio.api.model.student.DMStudentSubject
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface StudentSubjectRepository : JpaRepository<DMStudentSubject, Long> {
    fun findByStudentId(studentId: Long): List<DMStudentSubject>
    fun findBySubjectId(subjectId: Long): List<DMStudentSubject>
    fun findByStudentIdAndSubjectId(studentId: Long, subjectId: Long): DMStudentSubject?
}

