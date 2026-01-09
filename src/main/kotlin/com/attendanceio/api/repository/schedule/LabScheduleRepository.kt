package com.attendanceio.api.repository.schedule

import com.attendanceio.api.model.schedule.DMLabSchedule
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface LabScheduleRepository : JpaRepository<DMLabSchedule, Long> {
    
    /**
     * Find all lab schedule entries for a given subject
     */
    fun findBySubjectId(subjectId: Long): List<DMLabSchedule>
    
    /**
     * Find all lab schedule entries for multiple subjects in a single query.
     * Eagerly fetches subject, day, and slot to avoid N+1 queries.
     */
    @Query("""
        SELECT ls FROM DMLabSchedule ls 
        JOIN FETCH ls.subject 
        JOIN FETCH ls.day 
        JOIN FETCH ls.slot 
        WHERE ls.subject.id IN :subjectIds
    """)
    fun findBySubjectIdIn(@Param("subjectIds") subjectIds: List<Long>): List<DMLabSchedule>
}
