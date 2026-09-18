package com.attendanceio.api.application.agent.actions

import com.attendanceio.api.application.analytics.actions.CalculateAnalyticsAppAction
import com.attendanceio.api.model.agent.AgentBatchAverage
import com.attendanceio.api.model.agent.AgentGroupAverage
import com.attendanceio.api.model.agent.AgentOverallAnalytics
import com.attendanceio.api.model.agent.AgentRangeCount
import com.attendanceio.api.model.agent.AgentStudentRank
import com.attendanceio.api.model.agent.AgentSubjectClassStats
import com.attendanceio.api.repository.agent.AgentAnalyticsRepositoryAppAction
import com.attendanceio.api.repository.attendance.InstituteAttendanceRepositoryAppAction
import org.springframework.stereotype.Component
import kotlin.math.roundToInt

/**
 * "How is the class / the batch doing" questions. Overall numbers reuse the Analytics page's
 * calculation; per-subject and per-group figures come from the agent's own aggregation queries.
 */
@Component
class AgentAnalyticsQueryAppAction(
    private val calculateAnalyticsAppAction: CalculateAnalyticsAppAction,
    private val instituteAttendanceRepositoryAppAction: InstituteAttendanceRepositoryAppAction,
    private val agentAnalyticsRepositoryAppAction: AgentAnalyticsRepositoryAppAction,
    private val catalog: AgentCatalogAppAction
) {
    fun overall(semesterId: Long?): AgentOverallAnalytics? {
        val semester = catalog.semester(semesterId) ?: return null
        val a = calculateAnalyticsAppAction.calculateForSemester(semester)
        return AgentOverallAnalytics(
            semesterLabel = catalog.label(semester),
            totalStudents = a.totalStudents,
            totalSubjects = a.totalSubjects,
            averagePercentage = a.averageAttendance,
            above70Percent = a.above70,
            below60Percent = a.below60,
            ranges = a.ranges.map { AgentRangeCount(it.range, it.count) },
            note = if (a.totalStudents == 0) "No attendance data for this semester yet." else null
        )
    }

    /**
     * Class-wide picture for one subject. Uses the institute's official figures when they exist
     * for that subject (published for past semesters), otherwise what students marked in the app.
     * [sidPrefix] narrows to a batch/programme by roll-number prefix.
     */
    fun subjectClassStats(subjectQuery: String, semesterId: Long?, sidPrefix: String?, topN: Int, belowPercent: Double? = null): AgentSubjectClassStats? {
        val subject = catalog.resolveSubject(subjectQuery, semesterId) ?: return null
        val subjectId = subject.id ?: return null
        val prefix = sidPrefix?.trim()?.takeIf { it.isNotBlank() }

        val official = instituteAttendanceRepositoryAppAction.findBySubjectIdAndIsOfficial(subjectId, true)
            .filter { prefix == null || (it.student?.sid ?: "").startsWith(prefix) }
        val ranks: List<AgentStudentRank>
        val basis: String
        val enrolled: Int
        val enrolledRolls: List<String>
        val note: String
        if (official.isNotEmpty()) {
            basis = "OFFICIAL"
            enrolled = official.size
            enrolledRolls = official.mapNotNull { it.student?.sid }
            ranks = official.mapNotNull { r ->
                val s = r.student ?: return@mapNotNull null
                if (r.totalClasses <= 0) return@mapNotNull null
                AgentStudentRank(s.id ?: 0, s.name ?: "", s.sid, r.presentClasses, r.totalClasses, pct(r.presentClasses, r.totalClasses))
            }
            val cutoff = official.mapNotNull { it.cutoffDate }.maxOrNull()
            note = "Institute's official figures" + (cutoff?.let { " as of $it" } ?: "") + "."
        } else {
            basis = "APP"
            val counts = agentAnalyticsRepositoryAppAction.subjectStudentCounts(subjectId, prefix)
            enrolled = counts.size
            enrolledRolls = counts.map { it.rollNumber }
            ranks = counts.filter { it.present + it.absent > 0 }
                .map { AgentStudentRank(it.studentId, it.name, it.rollNumber, it.present, it.present + it.absent, pct(it.present, it.present + it.absent)) }
            note = "Based on classes students marked in Attendance IO (present + absent; cancelled classes excluded). " +
                "Students who marked nothing are counted as enrolled but have no percentage."
        }
        val sorted = ranks.sortedByDescending { it.percentage }
        val enrolledByBatch = enrolledRolls.groupingBy { it.take(4) }.eachCount()
        val byBatch = ranks.groupBy { it.rollNumber.take(4) }
            .map { (batch, rows) ->
                AgentBatchAverage(batch, enrolledByBatch[batch] ?: rows.size, rows.size, rows.map { it.percentage }.average().round2())
            }
            .sortedByDescending { it.averagePercentage }
        return AgentSubjectClassStats(
            subjectId = subjectId,
            subjectCode = subject.code,
            subjectName = subject.name,
            semesterLabel = catalog.label(subject.semester),
            basis = basis,
            studentsEnrolled = enrolled,
            studentsWithData = ranks.size,
            averagePercentage = ranks.map { it.percentage }.average().takeIf { ranks.isNotEmpty() }?.round2(),
            above75Percent = ranks.count { it.percentage >= 75 },
            below60Percent = ranks.count { it.percentage < 60 },
            byBatch = byBatch,
            top = sorted.take(topN),
            bottom = sorted.takeLast(topN).reversed(),
            below = belowPercent?.let { limit -> sorted.filter { it.percentage < limit }.reversed().take(30) },
            note = note + (prefix?.let { " Restricted to roll numbers starting with $it." } ?: "")
        )
    }

    /** Average semester attendance of every student whose roll number starts with [sidPrefix] (null = whole institute). */
    fun groupAverage(sidPrefix: String?, semesterId: Long?): AgentGroupAverage? {
        val semester = catalog.semester(semesterId) ?: return null
        val sid = semester.id ?: return null
        val prefix = sidPrefix?.trim()?.takeIf { it.isNotBlank() }
        val percentages = agentAnalyticsRepositoryAppAction.semesterPercentagesForGroup(sid, prefix).sorted()
        val inGroup = agentAnalyticsRepositoryAppAction.countStudentsInGroup(sid, prefix)
        return AgentGroupAverage(
            groupDescription = prefix?.let { "students with roll number starting $it" } ?: "all students",
            semesterLabel = catalog.label(semester),
            studentsInGroup = inGroup,
            studentsWithData = percentages.size,
            averagePercentage = percentages.average().takeIf { percentages.isNotEmpty() }?.round2(),
            medianPercentage = percentages.median()?.round2(),
            above75Percent = percentages.count { it >= 75 },
            between60And75Percent = percentages.count { it >= 60 && it < 75 },
            below60Percent = percentages.count { it < 60 },
            note = when {
                percentages.isEmpty() -> "No attendance data for this group in this semester."
                percentages.size < inGroup -> "${inGroup - percentages.size} enrolled students have no attendance data and are excluded from the average."
                else -> null
            }
        )
    }

    private fun pct(present: Int, total: Int): Double = if (total <= 0) 0.0 else (present * 100.0 / total).round2()
    private fun Double.round2(): Double = (this * 100).roundToInt() / 100.0
    private fun List<Double>.median(): Double? =
        if (isEmpty()) null else if (size % 2 == 1) this[size / 2] else (this[size / 2 - 1] + this[size / 2]) / 2
}
