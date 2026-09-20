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
        description = "Student clubs, committees and organisations at DAU (from the Student Body Government): name, type, what they do, " +
            "email/Instagram and who leads them. query matches names, short names (cult, HMC, CMC, GDG, EHC, DebSoc, SPC) and " +
            "activities ('robotics', 'garba', 'hackathon'). type = club | committee | organisation to filter. Omit both to list all."
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
        description = "One club/committee in full: description, key activities, all links, and the current members with designation, " +
            "roll number, phone and email. Use for 'who is the convener of X', 'contact of the hostel committee', 'members of GDG'. " +
            "designation filters members (convener, core, mentor…)."
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
        description = "Which clubs/committees a student is part of, and their role there, by name or roll number. " +
            "Use for 'is Rahul in any committee', 'what does 202401195 do in SBG'."
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
        description = "Public campus events from the SBG calendar (club sessions, fests, screenings, workshops, competitions) with " +
            "organiser, venue and IST timings. Defaults to the next 30 days from today; pass from/to for another window " +
            "('this weekend', 'in October'). club narrows to one organiser; query matches the event name or venue."
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
        description = "Faculty directory of DAU: name, designation category, degree, office phone, email and research interests. " +
            "query matches a name or a topic ('machine learning', 'VLSI', 'signal processing'); category = faculty | adjunct | " +
            "professor of practice | distinguished. Use get_faculty for one person's biography and courses."
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
        description = "One faculty member in full: biography, specialization, courses they teach, research and profile link."
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
        description = "The official DAU academic calendar: registration, add/drop, class start, mid-sem and end-sem exams, breaks, " +
            "result dates, convocation — per term (Autumn / Winter / Summer) and academic year (e.g. 2026-27). Use for 'when do " +
            "end-sems start', 'last date to drop a course', 'when is Diwali break'. query filters the event text."
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
        description = "Official DAU placement figures by season (2023-24, 2024-25, 2025-26) and level (UG | PG | ALL): highest / " +
            "average / median package, offers, companies, stipends, students placed, sector and city split, salary heads, " +
            "prominent recruiters and notable offers. Each row names its source document and basis. Use for 'average package', " +
            "'highest CTC', 'how many got placed', 'which sectors hire'."
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
        description = "Companies named as recruiters in DAU's official placement brochure and audit report, with the seasons they " +
            "appear in. Use for 'does Google come to campus', 'which companies recruit from DAU'. For where alumni actually " +
            "work, use list_alumni_companies instead."
    )
    fun listPlacementRecruiters(
        @ToolParam(description = "Name fragment, e.g. 'gold' for Goldman Sachs; omit for all", required = false) query: String?,
        toolContext: ToolContext
    ): AgentListResult<AgentRecruiter> =
        support.recorded(toolContext, "list_placement_recruiters", mapOf("query" to query)) {
            college.recruiters(query, 60)
        }
}
