package com.attendanceio.api.repository.timetable

import com.attendanceio.api.model.timetable.DMStudentTutorialTimetable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface StudentTutorialTimetableRepository : JpaRepository<DMStudentTutorialTimetable, Long> {
    
    /**
     * Find all tutorial timetable entries for a student in a specific semester
     */
    @Query("""
        SELECT tt FROM DMStudentTutorialTimetable tt 
        JOIN FETCH tt.subject 
        JOIN FETCH tt.day 
        LEFT JOIN FETCH tt.slot 
        WHERE tt.student.id = :studentId AND tt.semester.id = :semesterId
        ORDER BY tt.day.id, COALESCE(tt.slot.id, 999), tt.customStartTime
    """)
    fun findByStudentIdAndSemesterId(
        @Param("studentId") studentId: Long,
        @Param("semesterId") semesterId: Long
    ): List<DMStudentTutorialTimetable>
    
    /**
     * Delete all tutorial timetable entries for a student in a specific semester
     */
    fun deleteByStudentIdAndSemesterId(studentId: Long, semesterId: Long)
}
