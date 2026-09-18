package com.attendanceio.api.repository.agent

import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class AgentAnalyticsRepositoryAppAction(
    private val repository: AgentAnalyticsRepository
) {
    @Transactional(readOnly = true)
    fun semesterPercentagesForGroup(semesterId: Long, sidPrefix: String?): List<Double> =
        repository.semesterPercentagesForGroup(semesterId, sidPrefix)

    @Transactional(readOnly = true)
    fun countStudentsInGroup(semesterId: Long, sidPrefix: String?): Int =
        repository.countStudentsInGroup(semesterId, sidPrefix)

    @Transactional(readOnly = true)
    fun subjectStudentCounts(subjectId: Long, sidPrefix: String?): List<SubjectStudentCounts> =
        repository.subjectStudentCounts(subjectId, sidPrefix)
}
