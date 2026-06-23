package com.attendanceio.api.application.search.actions

import com.attendanceio.api.repository.search.SearchHistoryRepositoryAppAction
import org.springframework.stereotype.Component

@Component
class ClearSearchHistoryAppAction(
    private val searchHistoryRepositoryAppAction: SearchHistoryRepositoryAppAction
) {
    fun execute(studentId: Long): Int {
        val all = searchHistoryRepositoryAppAction.findAllActiveByStudentId(studentId)
        all.forEach { it.isDeleted = true }
        searchHistoryRepositoryAppAction.saveAll(all)
        return all.size
    }
}
