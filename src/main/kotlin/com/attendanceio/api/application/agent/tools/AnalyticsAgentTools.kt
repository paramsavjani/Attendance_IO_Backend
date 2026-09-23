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
        description =
            "Class-wide attendance for ONE subject: enrolled, average %, above-75 / below-60 counts, per-batch breakdown " +
                "(byBatch), top and bottom students, and everyone below belowPercent. Official figures when published, else " +
                "app data. Use for 'average in CT303', 'which batch is best in DSA', 'who has the best attendance'; " +
                "batchPrefix restricts it to one batch.",
    )
    fun getSubjectClassStats(
        @ToolParam(description = "Subject code or name") subject: String,
        @ToolParam(description = "Semester id; omit for the current semester", required = false) semesterId: Long?,
        @ToolParam(description = "Roll-number prefix to restrict the group, e.g. '2024' (batch) or '202401' (batch + programme)", required = false) batchPrefix: String?,
        @ToolParam(description = "How many top/bottom students to include (default 5, max 20)", required = false) topN: Int?,
        @ToolParam(description = "Also list every student under this percentage, e.g. 60 or 75 (max 30 names)", required = false) belowPercent: Double?,
        toolContext: ToolContext
    ): AgentSubjectClassStats? =
        support.recorded(
            toolContext, "get_subject_class_stats",
            mapOf("subject" to subject, "semesterId" to semesterId, "batchPrefix" to batchPrefix, "topN" to topN, "belowPercent" to belowPercent)
        ) {
            analytics.subjectClassStats(subject, semesterId, batchPrefix, (topN ?: 5).coerceIn(1, 20), belowPercent)
        }

    @Tool(
        name = "get_group_average",
        description =
            "Average, median and distribution of semester attendance for a group by roll-number prefix: '2024' = admitted " +
                "2024, '202401' = programme 01 of that batch. Omit for the whole institute. Use for 'average of the 2023 " +
                "batch', '2024 vs 2025'.",
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
