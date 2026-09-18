package com.attendanceio.api.application.agent.tools

import com.attendanceio.api.application.agent.actions.AgentCatalogAppAction
import com.attendanceio.api.model.agent.AgentListResult
import com.attendanceio.api.model.agent.AgentSemesterSummary
import com.attendanceio.api.model.agent.AgentSubjectSummary
import org.springframework.ai.chat.model.ToolContext
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam
import org.springframework.stereotype.Component

/** Reference data: which semesters and subjects exist, so the model can resolve what the user means. */
@Component
class CatalogAgentTools(
    private val catalog: AgentCatalogAppAction,
    private val support: AgentToolSupport
) {
    @Tool(
        name = "list_semesters",
        description = "All semesters with id, year, type and which one is current. SUMMER = the July–November term, " +
            "WINTER = the January–May term. Use when the user says 'last semester', 'winter sem', or asks about a specific term."
    )
    fun listSemesters(toolContext: ToolContext): List<AgentSemesterSummary> =
        support.recorded(toolContext, "list_semesters", emptyMap()) { catalog.listSemesters() }

    @Tool(
        name = "list_subjects",
        description = "Subjects of a semester (current by default) with code, name, room and whether the caller is enrolled. " +
            "Use to resolve a nickname ('DSA', 'signals') to a subject code, or for 'which subjects do I have'. " +
            "Set onlyMine=true for the caller's own subjects."
    )
    fun listSubjects(
        @ToolParam(description = "Semester id from list_semesters; omit for the current semester", required = false) semesterId: Long?,
        @ToolParam(description = "true = only subjects the caller is enrolled in", required = false) onlyMine: Boolean?,
        toolContext: ToolContext
    ): AgentListResult<AgentSubjectSummary> =
        support.recorded(toolContext, "list_subjects", mapOf("semesterId" to semesterId, "onlyMine" to onlyMine)) {
            val callerStudentId = support.caller(toolContext).studentId
            val all = catalog.listSubjects(semesterId, callerStudentId)
            val items = if (onlyMine == true) all.filter { it.enrolledByMe == true } else all
            AgentListResult(items = items, totalCount = items.size)
        }
}
