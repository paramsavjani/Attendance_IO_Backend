package com.attendanceio.api.repository.student

import com.attendanceio.api.model.student.DMStudentSubject
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface StudentSubjectRepository : JpaRepository<DMStudentSubject, Long> {
    fun findByStudentId(studentId: Long): List<DMStudentSubject>
    fun findBySubjectId(subjectId: Long): List<DMStudentSubject>
    fun findByStudentIdAndSubjectId(studentId: Long, subjectId: Long): DMStudentSubject?
    
    @Modifying
    @Query("DELETE FROM DMStudentSubject ss WHERE ss.student.id = :studentId")
    fun deleteAllByStudentId(@Param("studentId") studentId: Long)
    
    @Modifying
    @Query("DELETE FROM DMStudentSubject ss WHERE ss.student.id = :studentId AND ss.subject.semester.id = :semesterId")
    fun deleteAllByStudentIdAndSemesterId(@Param("studentId") studentId: Long, @Param("semesterId") semesterId: Long)
}

