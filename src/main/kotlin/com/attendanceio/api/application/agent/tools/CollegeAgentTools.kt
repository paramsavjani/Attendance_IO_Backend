package com.attendanceio.api.application.agent.tools

import com.attendanceio.api.application.agent.actions.AgentCollegeQueryAppAction
import com.attendanceio.api.config.AgentProperties
import com.attendanceio.api.model.agent.AgentCalendarEntry
import com.attendanceio.api.model.agent.AgentCampusEvent
import com.attendanceio.api.model.agent.AgentClubDetail
import com.attendanceio.api.model.agent.AgentClubMember
import com.attendanceio.api.model.agent.AgentClubSummary
import com.attendanceio.api.model.agent.AgentFacultyDetail
import com.attendanceio.api.model.agent.AgentFacultySummary
import com.attendanceio.api.model.agent.AgentListResult
import com.attendanceio.api.model.agent.AgentPlacementStat
import com.attendanceio.api.model.agent.AgentRecruiter
import org.springframework.ai.chat.model.ToolContext
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam
import org.springframework.stereotype.Component
import java.time.LocalDate

/** Institute knowledge base: clubs & committees, campus events, faculty, the academic calendar and placements. Read-only. */
@Component
class CollegeAgentTools(
    private val college: AgentCollegeQueryAppAction,
    private val support: AgentToolSupport,
    private val properties: AgentProperties
) {
    @Tool(
        name = "find_clubs",
        description = "SBG clubs, committees and organisations: what they do, contacts and who leads them. query matches names, short names (cult, HMC, CMC, GDG, EHC, DebSoc) or activities; type = club | committee | organisation. Omit both for all."
    )
    fun findClubs(
        @ToolParam(description = "Name, nickname or activity, e.g. 'cultural', 'HMC', 'dance', 'coding'", required = false) query: String?,
        @ToolParam(description = "club | committee | organisation", required = false) type: String?,
        toolContext: ToolContext
    ): AgentListResult<AgentClubSummary> =
        support.recorded(toolContext, "find_clubs", mapOf("query" to query, "type" to type)) {
            college.findClubs(query, type, properties.maxToolRows)
        }

    @Tool(
        name = "get_club",
        description = "One club/committee in full, including every member with designation, roll number, phone and email. Use for 'convener of X', 'contact of HMC', 'members of GDG'. designation filters members."
    )
    fun getClub(
        @ToolParam(description = "clubId from find_clubs, or the club's name / short name") club: String,
        @ToolParam(description = "Only members whose designation contains this, e.g. 'convener'", required = false) designation: String?,
        toolContext: ToolContext
    ): AgentClubDetail? =
        support.recorded(toolContext, "get_club", mapOf("club" to club, "designation" to designation)) {
            college.getClub(club, designation)
        }

    @Tool(
        name = "find_club_member",
        description = "Which clubs/committees a student is in and their role, by name or roll number."
    )
    fun findClubMember(
        @ToolParam(description = "Part of a name, or a roll number") nameOrRollNumber: String,
        toolContext: ToolContext
    ): AgentListResult<AgentClubMember> =
        support.recorded(toolContext, "find_club_member", mapOf("nameOrRollNumber" to nameOrRollNumber)) {
            college.findClubMembers(nameOrRollNumber, properties.maxToolRows)
        }

    @Tool(
        name = "get_campus_events",
        description = "Public campus events (SBG calendar) with organiser, venue and IST time. Default: next 30 days; pass from/to for another window; club or query to narrow."
    )
    fun getCampusEvents(
        @ToolParam(description = "Organising club/committee name or short name", required = false) club: String?,
        @ToolParam(description = "Start date (YYYY-MM-DD), default today", required = false) from: LocalDate?,
        @ToolParam(description = "End date (YYYY-MM-DD), default from + 30 days", required = false) to: LocalDate?,
        @ToolParam(description = "Text in the event name or venue, e.g. 'garba', 'LT1'", required = false) query: String?,
        toolContext: ToolContext
    ): AgentListResult<AgentCampusEvent> =
        support.recorded(toolContext, "get_campus_events", mapOf("club" to club, "from" to from, "to" to to, "query" to query)) {
            college.upcomingEvents(club, from, to, query, properties.maxToolRows)
        }

    @Tool(
        name = "find_faculty",
        description = "Faculty directory: name, category, degree, office, phone, email, research interests. query = name or topic ('machine learning'); category = faculty | adjunct | professor of practice | distinguished."
    )
    fun findFaculty(
        @ToolParam(description = "Name or research area", required = false) query: String?,
        @ToolParam(description = "faculty | adjunct | professor of practice | distinguished | institute professor", required = false) category: String?,
        toolContext: ToolContext
    ): AgentListResult<AgentFacultySummary> =
        support.recorded(toolContext, "find_faculty", mapOf("query" to query, "category" to category)) {
            college.findFaculty(query, category, properties.maxToolRows)
        }

    @Tool(
        name = "get_faculty",
        description = "One faculty member's biography, specialization, courses taught, research and profile link."
    )
    fun getFaculty(
        @ToolParam(description = "facultyId from find_faculty, or the person's full name") faculty: String,
        toolContext: ToolContext
    ): AgentFacultyDetail? =
        support.recorded(toolContext, "get_faculty", mapOf("faculty" to faculty)) {
            college.getFaculty(faculty)
        }

    @Tool(
        name = "get_institute_calendar",
        description = "Official DAU academic calendar per year/term: registration, add/drop, exams, breaks, results, convocation. Use for 'when do end-sems start', 'last date to drop'. query filters event text."
    )
    fun getInstituteCalendar(
        @ToolParam(description = "Academic year like 2026-27; default = latest published", required = false) academicYear: String?,
        @ToolParam(description = "Autumn | Winter | Summer", required = false) term: String?,
        @ToolParam(description = "Text in the event, e.g. 'exam', 'registration', 'break'", required = false) query: String?,
        toolContext: ToolContext
    ): AgentListResult<AgentCalendarEntry> =
        support.recorded(toolContext, "get_institute_calendar", mapOf("academicYear" to academicYear, "term" to term, "query" to query)) {
            college.academicCalendar(academicYear, term, query)
        }

    @Tool(
        name = "get_placement_stats",
        description = "Official placement figures by season (2023-24 to 2025-26) and level (UG | PG | ALL): highest/average/median package, offers, companies, stipends, placed count, sector and city split, recruiters. Each row names its source and basis."
    )
    fun getPlacementStats(
        @ToolParam(description = "Season like 2024-25 (or a year like 2025); omit for all seasons", required = false) season: String?,
        @ToolParam(description = "UG | PG; omit for both plus overall", required = false) level: String?,
        toolContext: ToolContext
    ): AgentListResult<AgentPlacementStat> =
        support.recorded(toolContext, "get_placement_stats", mapOf("season" to season, "level" to level)) {
            college.placementStats(season, level)
        }

    @Tool(
        name = "list_placement_recruiters",
        description = "Companies named as recruiters in DAU's placement brochure/audit report with seasons. For where alumni work use list_alumni_companies."
    )
    fun listPlacementRecruiters(
        @ToolParam(description = "Name fragment, e.g. 'gold' for Goldman Sachs; omit for all", required = false) query: String?,
        toolContext: ToolContext
    ): AgentListResult<AgentRecruiter> =
        support.recorded(toolContext, "list_placement_recruiters", mapOf("query" to query)) {
            college.recruiters(query, 60)
        }
}
