package com.attendanceio.api.application.agent.tools

import com.attendanceio.api.application.agent.actions.AgentAnalyticsQueryAppAction
import com.attendanceio.api.model.agent.AgentGroupAverage
import com.attendanceio.api.model.agent.AgentOverallAnalytics
import com.attendanceio.api.model.agent.AgentSubjectClassStats
import org.springframework.ai.chat.model.ToolContext
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam
import org.springframework.stereotype.Component

/** "How is everyone doing" tools: whole institute, a subject's class, or a batch/programme. */
@Component
class AnalyticsAgentTools(
    private val analytics: AgentAnalyticsQueryAppAction,
    private val support: AgentToolSupport
) {
    @Tool(
        name = "get_overall_analytics",
        description = "Institute-wide attendance for a semester (current by default): total students, average percentage, " +
            "how many are above 70% / below 60%, and a histogram by range. Use for 'what is the average attendance overall'."
    )
    fun getOverallAnalytics(
        @ToolParam(description = "Semester id from list_semesters; omit for the current semester", required = false) semesterId: Long?,
        toolContext: ToolContext
    ): AgentOverallAnalytics? =
        support.recorded(toolContext, "get_overall_analytics", mapOf("semesterId" to semesterId)) {
            analytics.overall(semesterId)
        }

    @Tool(
        name = "get_subject_class_stats",
        description = "Class-wide attendance for ONE subject: enrolled count, average percentage, above-75/below-60 counts, a " +
            "per-batch breakdown (byBatch: 2023/2024/2025… students often share a subject), and the top and bottom students. " +
            "Uses the institute's official figures when published (past semesters), else app data. Use for 'average attendance " +
            "in CT303', 'which batch is doing best in DSA', 'who has the best attendance in CP1001', 'how is the 2024 batch doing " +
            "in CP1001' (pass batchPrefix='2024' to restrict everything to that batch)."
    )
    fun getSubjectClassStats(
        @ToolParam(description = "Subject code or name") subject: String,
        @ToolParam(description = "Semester id; omit for the current semester", required = false) semesterId: Long?,
        @ToolParam(description = "Roll-number prefix to restrict the group, e.g. '2024' (batch) or '202401' (batch + programme)", required = false) batchPrefix: String?,
        @ToolParam(description = "How many top/bottom students to include (default 5, max 20)", required = false) topN: Int?,
        toolContext: ToolContext
    ): AgentSubjectClassStats? =
        support.recorded(
            toolContext, "get_subject_class_stats",
            mapOf("subject" to subject, "semesterId" to semesterId, "batchPrefix" to batchPrefix, "topN" to topN)
        ) {
            analytics.subjectClassStats(subject, semesterId, batchPrefix, (topN ?: 5).coerceIn(1, 20))
        }

    @Tool(
        name = "get_group_average",
        description = "Average, median and distribution of overall semester attendance for a GROUP of students chosen by roll-number " +
            "prefix: '2024' = everyone admitted in 2024, '202401' = the 2024 batch of programme 01, '2023010' = a narrower slice. " +
            "Omit the prefix for the whole institute. Use for 'average attendance of the 2023 batch', 'compare 2024 vs 2025 batch'."
    )
    fun getGroupAverage(
        @ToolParam(description = "Roll-number prefix defining the group; omit for everyone", required = false) batchPrefix: String?,
        @ToolParam(description = "Semester id; omit for the current semester", required = false) semesterId: Long?,
        toolContext: ToolContext
    ): AgentGroupAverage? =
        support.recorded(toolContext, "get_group_average", mapOf("batchPrefix" to batchPrefix, "semesterId" to semesterId)) {
            analytics.groupAverage(batchPrefix, semesterId)
        }
}
