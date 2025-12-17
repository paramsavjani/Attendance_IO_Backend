package com.attendanceio.api.repository.schedule

import com.attendanceio.api.model.schedule.DMSubjectSchedule
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface SubjectScheduleRepository : JpaRepository<DMSubjectSchedule, Long> {
    
    /**
     * Find all schedule entries for a given subject
     */
    fun findBySubjectId(subjectId: Long): List<DMSubjectSchedule>
    
    /**
     * Find all schedule entries for multiple subjects in a single query.
     * Eagerly fetches subject, day, and slot to avoid N+1 queries.
     */
    @Query("""
        SELECT ss FROM DMSubjectSchedule ss 
        JOIN FETCH ss.subject 
        JOIN FETCH ss.day 
        JOIN FETCH ss.slot 
        WHERE ss.subject.id IN :subjectIds
    """)
    fun findBySubjectIdIn(@Param("subjectIds") subjectIds: List<Long>): List<DMSubjectSchedule>
}

