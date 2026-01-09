package com.attendanceio.api.repository.schedule

import com.attendanceio.api.model.schedule.DMTutorialSchedule
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface TutorialScheduleRepository : JpaRepository<DMTutorialSchedule, Long> {
    
    /**
     * Find all tutorial schedule entries for a given subject
     */
    fun findBySubjectId(subjectId: Long): List<DMTutorialSchedule>
    
    /**
     * Find all tutorial schedule entries for multiple subjects in a single query.
     * Eagerly fetches subject, day, and slot to avoid N+1 queries.
     */
    @Query("""
        SELECT ts FROM DMTutorialSchedule ts 
        JOIN FETCH ts.subject 
        JOIN FETCH ts.day 
        JOIN FETCH ts.slot 
        WHERE ts.subject.id IN :subjectIds
    """)
    fun findBySubjectIdIn(@Param("subjectIds") subjectIds: List<Long>): List<DMTutorialSchedule>
}
