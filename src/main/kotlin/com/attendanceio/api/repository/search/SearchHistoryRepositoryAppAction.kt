package com.attendanceio.api.repository.search

import com.attendanceio.api.model.search.DMSearchHistory
import org.springframework.stereotype.Component

@Component
class SearchHistoryRepositoryAppAction(
    private val searchHistoryRepository: SearchHistoryRepository
) {
    fun findRecentByStudentId(studentId: Long, limit: Int = 10): List<DMSearchHistory> =
        searchHistoryRepository
            .findByStudentIdAndIsDeletedFalseOrderByCreatedAtDesc(studentId)
            .take(limit)

    fun findByStudentIdAndViewedStudentId(studentId: Long, viewedStudentId: Long): DMSearchHistory? =
        searchHistoryRepository.findByStudentIdAndViewedStudentIdAndIsDeletedFalse(studentId, viewedStudentId)

    fun findAllActiveByStudentId(studentId: Long): List<DMSearchHistory> =
        searchHistoryRepository.findByStudentIdAndIsDeletedFalse(studentId)

    fun findById(id: Long): DMSearchHistory? =
        searchHistoryRepository.findById(id).orElse(null)

    fun save(history: DMSearchHistory): DMSearchHistory =
        searchHistoryRepository.save(history)

    fun saveAll(histories: List<DMSearchHistory>): List<DMSearchHistory> =
        searchHistoryRepository.saveAll(histories)
}
