package com.attendanceio.api.repository.timetable

import com.attendanceio.api.model.timetable.DMStudentTimetable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface StudentTimetableRepository : JpaRepository<DMStudentTimetable, Long> {
    fun findByStudentId(studentId: Long): List<DMStudentTimetable>
    fun findByStudentIdAndSemesterId(studentId: Long, semesterId: Long): List<DMStudentTimetable>
    fun findByStudentIdAndSemesterIdAndDayId(studentId: Long, semesterId: Long, dayId: Long): List<DMStudentTimetable>
    
    @Modifying
    @Query("DELETE FROM DMStudentTimetable st WHERE st.student.id = :studentId")
    fun deleteAllByStudentId(@Param("studentId") studentId: Long)
    
    @Modifying
    @Query("DELETE FROM DMStudentTimetable st WHERE st.student.id = :studentId AND st.semester.id = :semesterId")
    fun deleteAllByStudentIdAndSemesterId(@Param("studentId") studentId: Long, @Param("semesterId") semesterId: Long)
}
