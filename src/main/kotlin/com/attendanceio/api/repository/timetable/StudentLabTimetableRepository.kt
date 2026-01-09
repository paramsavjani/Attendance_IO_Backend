package com.attendanceio.api.repository.timetable

import com.attendanceio.api.model.timetable.DMStudentLabTimetable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface StudentLabTimetableRepository : JpaRepository<DMStudentLabTimetable, Long> {
    
    /**
     * Find all lab timetable entries for a student in a specific semester
     */
    @Query("""
        SELECT lt FROM DMStudentLabTimetable lt 
        JOIN FETCH lt.subject 
        JOIN FETCH lt.day 
        LEFT JOIN FETCH lt.slot 
        WHERE lt.student.id = :studentId AND lt.semester.id = :semesterId
        ORDER BY lt.day.id, COALESCE(lt.slot.id, 999), lt.customStartTime
    """)
    fun findByStudentIdAndSemesterId(
        @Param("studentId") studentId: Long,
        @Param("semesterId") semesterId: Long
    ): List<DMStudentLabTimetable>
    
    /**
     * Delete all lab timetable entries for a student in a specific semester
     */
    fun deleteByStudentIdAndSemesterId(studentId: Long, semesterId: Long)
}
