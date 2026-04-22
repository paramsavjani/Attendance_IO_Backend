package com.attendanceio.api.repository.search

import com.attendanceio.api.model.search.DMSearchHistory
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface SearchHistoryRepository : JpaRepository<DMSearchHistory, Long> {

    fun findByStudentIdAndIsDeletedFalseOrderByCreatedAtDesc(studentId: Long): List<DMSearchHistory>

    fun findByStudentIdAndIsDeletedFalse(studentId: Long): List<DMSearchHistory>

    /** Used for deduplication before inserting a new record */
    fun findByStudentIdAndViewedStudentIdAndIsDeletedFalse(
        studentId: Long,
        viewedStudentId: Long
    ): DMSearchHistory?
}
