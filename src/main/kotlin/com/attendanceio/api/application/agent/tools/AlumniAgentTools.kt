package com.attendanceio.api.application.agent.tools

import com.attendanceio.api.application.agent.actions.AgentAlumniQueryAppAction
import com.attendanceio.api.model.agent.AgentAlumniCompany
import com.attendanceio.api.model.agent.AgentAlumnus
import com.attendanceio.api.model.agent.AgentListResult
import org.springframework.ai.chat.model.ToolContext
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam
import org.springframework.stereotype.Component

/** Alumni directory (AlmaConnect export): who works where, with LinkedIn links. Read-only like everything else. */
@Component
class AlumniAgentTools(
    private val alumni: AgentAlumniQueryAppAction,
    private val support: AgentToolSupport
) {
    @Tool(
        name = "search_alumni",
        description = "Find alumni (graduates of DA-IICT) by name, company, role, city, batch or course, with their LinkedIn " +
            "and AlmaConnect profile links and the company's average package. Every filter is optional; combine them: " +
            "company='Google' + linkedinOnly=true for 'people at Google I can contact', city='Gujarat' for everyone in " +
            "Gujarat (regions like Gujarat, NCR, Bay Area expand to their cities; several cities can be comma-separated), " +
            "batch=2019 for the 2019 graduating batch, query='data scientist' to match titles. Results come best-paying company first."
    )
    fun searchAlumni(
        @ToolParam(description = "Free text matched against name, company, job title/headline or city, e.g. 'product manager', 'Shastri'", required = false) query: String?,
        @ToolParam(description = "Company name, e.g. 'Google', 'Amazon', 'Sprinklr'", required = false) company: String?,
        @ToolParam(description = "Graduation year, e.g. 2019", required = false) batch: Int?,
        @ToolParam(description = "Degree: B.Tech., M.Tech., M.Sc., M.Des., M.S., B.E., Ph.D.", required = false) course: String?,
        @ToolParam(description = "City or region, e.g. 'Bangalore', 'Gujarat', 'Ahmedabad, Surat', 'Bay Area'", required = false) city: String?,
        @ToolParam(description = "true = only people with a LinkedIn link", required = false) linkedinOnly: Boolean?,
        @ToolParam(description = "Max rows (default 15, max 30)", required = false) limit: Int?,
        toolContext: ToolContext
    ): AgentListResult<AgentAlumnus> =
        support.recorded(
            toolContext, "search_alumni",
            mapOf("query" to query, "company" to company, "batch" to batch, "course" to course, "city" to city, "linkedinOnly" to linkedinOnly, "limit" to limit)
        ) {
            alumni.search(query, company, batch, course, city, linkedinOnly == true, (limit ?: 15).coerceIn(1, 30))
        }

    @Tool(
        name = "list_alumni_companies",
        description = "Companies where alumni work, with how many alumni are there and the average package (LPA, null when unknown). " +
            "Use for 'where do most alumni work', 'highest paying companies', 'which companies hire from DAU', or to check a company " +
            "name before search_alumni. sortBy='count' (default) or 'lpa'."
    )
    fun listAlumniCompanies(
        @ToolParam(description = "Name fragment, e.g. 'micro' matches Microsoft; omit for all", required = false) query: String?,
        @ToolParam(description = "'count' = most alumni first (default), 'lpa' = best paying first", required = false) sortBy: String?,
        @ToolParam(description = "Max rows (default 15, max 30)", required = false) limit: Int?,
        toolContext: ToolContext
    ): AgentListResult<AgentAlumniCompany> =
        support.recorded(toolContext, "list_alumni_companies", mapOf("query" to query, "sortBy" to sortBy, "limit" to limit)) {
            alumni.companies(query, sortBy, (limit ?: 15).coerceIn(1, 30))
        }
}
