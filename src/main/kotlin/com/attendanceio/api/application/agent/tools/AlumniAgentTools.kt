package com.attendanceio.api.application.agent.tools

import com.attendanceio.api.application.agent.actions.AgentAlumniQueryAppAction
import com.attendanceio.api.config.AgentProperties
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
    private val support: AgentToolSupport,
    private val properties: AgentProperties
) {
    @Tool(
        name = "search_alumni",
        description =
            "DAU alumni by name, company, role, city, batch or course, with LinkedIn (when present) and the company's " +
                "average package. Filters combine (company + linkedinOnly for people to contact); city accepts regions " +
                "(Gujarat, NCR, Bay Area) and comma-separated cities; batch = graduation year. Best-paying company first. " +
                "page=1, 2, … only if the user asks for more.",
    )
    fun searchAlumni(
        @ToolParam(description = "Free text matched against name, company, job title/headline or city, e.g. 'product manager', 'Shastri'", required = false) query: String?,
        @ToolParam(description = "Company name, e.g. 'Google', 'Amazon', 'Sprinklr'", required = false) company: String?,
        @ToolParam(description = "Graduation year, e.g. 2019", required = false) batch: Int?,
        @ToolParam(description = "Degree: B.Tech., M.Tech., M.Sc., M.Des., M.S., B.E., Ph.D.", required = false) course: String?,
        @ToolParam(description = "City or region, e.g. 'Bangalore', 'Gujarat', 'Ahmedabad, Surat', 'Bay Area'", required = false) city: String?,
        @ToolParam(description = "true = only people with a LinkedIn link", required = false) linkedinOnly: Boolean?,
        @ToolParam(description = "0 (default) = first page of results; 1 = the next page, and so on. Only use when the user asks for more.", required = false) page: Int?,
        toolContext: ToolContext
    ): AgentListResult<AgentAlumnus> =
        support.recorded(
            toolContext, "search_alumni",
            mapOf("query" to query, "company" to company, "batch" to batch, "course" to course, "city" to city, "linkedinOnly" to linkedinOnly, "page" to page)
        ) {
            alumni.search(query, company, batch, course, city, linkedinOnly == true, page ?: 0, properties.alumniRowsPerAnswer)
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
