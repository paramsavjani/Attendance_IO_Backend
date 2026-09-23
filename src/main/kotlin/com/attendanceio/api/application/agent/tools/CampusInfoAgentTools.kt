package com.attendanceio.api.application.agent.tools

import com.attendanceio.api.application.agent.actions.AgentCampusInfoQueryAppAction
import com.attendanceio.api.config.AgentProperties
import com.attendanceio.api.model.agent.AgentListResult
import com.attendanceio.api.model.college.DMCampusService
import com.attendanceio.api.model.college.DMInstituteCommittee
import com.attendanceio.api.model.college.DMProgram
import com.attendanceio.api.model.college.DMScholarship
import com.attendanceio.api.model.college.DMStaffContact
import org.springframework.ai.chat.model.ToolContext
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam
import org.springframework.stereotype.Component
import java.time.LocalDate

/** Institute knowledge base, part two: contacts, holidays, curriculum, committees, scholarships, placement events, services, programmes. Read-only. */
@Component
class CampusInfoAgentTools(
    private val info: AgentCampusInfoQueryAppAction,
    private val support: AgentToolSupport,
    private val properties: AgentProperties
) {
    @Tool(
        name = "get_holidays",
        description = "Official DAU holiday list for a year (name, date, weekday). Use for 'is X a holiday', 'holidays in October'. from/to narrow the window."
    )
    fun getHolidays(
        @ToolParam(description = "Calendar year, default = current year", required = false) year: Int?,
        @ToolParam(description = "Start date YYYY-MM-DD", required = false) from: LocalDate?,
        @ToolParam(description = "End date YYYY-MM-DD", required = false) to: LocalDate?,
        toolContext: ToolContext
    ): AgentListResult<Map<String, Any?>> =
        support.recorded(toolContext, "get_holidays", mapOf("year" to year, "from" to from, "to" to to)) { info.holidays(year, from, to) }

    @Tool(
        name = "find_staff_contacts",
        description =
            "Official office/staff contacts — wardens, hostel office, Dean of Students, medical centre, counsellor, " +
                "registrar, placement, security, library, IT — with designation, name, room, phones, email. query = role or " +
                "name; category = dean-students | hostel | medical | counselling | security | registrar | placement | " +
                "academics | library | sports | administration | it-support | director.",
    )
    fun findStaffContacts(
        @ToolParam(description = "Role, office or name, e.g. 'women warden', 'hostel supervisor', 'ambulance'", required = false) query: String?,
        @ToolParam(description = "Category filter, see description", required = false) category: String?,
        toolContext: ToolContext
    ): AgentListResult<DMStaffContact> =
        support.recorded(toolContext, "find_staff_contacts", mapOf("query" to query, "category" to category)) {
            info.findContacts(query, category, properties.maxToolRows)
        }

    @Tool(
        name = "list_programmes",
        description = "Degree programmes DAU offers with degree, level, duration, intake, admission route, eligibility, fees. Use for 'which courses can I do', 'how long is M.Des'."
    )
    fun listProgrammes(
        @ToolParam(description = "Name fragment, e.g. 'data science', 'ICT'", required = false) query: String?,
        @ToolParam(description = "UG | PG | Doctoral | Dual", required = false) level: String?,
        toolContext: ToolContext
    ): AgentListResult<DMProgram> =
        support.recorded(toolContext, "list_programmes", mapOf("query" to query, "level" to level)) { info.listProgrammes(query, level) }

    @Tool(
        name = "get_curriculum",
        description = "Semester-wise courses of one programme (code, title, credits, L-T-P-C, type), elective pools and credit rules. programme accepts short names (ICT, CSAI, MnC, EVD, ECE-AI, BS-MS IT, MTech, MSc DS, MDes, PhD). For this term's live subjects use list_subjects."
    )
    fun getCurriculum(
        @ToolParam(description = "Programme name or short name, e.g. 'ICT', 'B.Tech. CS & AI', 'MSc IT'") programme: String,
        @ToolParam(description = "Semester number to show; omit for all", required = false) semester: Int?,
        @ToolParam(description = "Course title/code fragment to find, e.g. 'data structures', 'IT-214'", required = false) course: String?,
        toolContext: ToolContext
    ): Map<String, Any?>? =
        support.recorded(toolContext, "get_curriculum", mapOf("programme" to programme, "semester" to semester, "course" to course)) {
            info.curriculum(programme, semester, course)
        }

    @Tool(
        name = "find_institute_committees",
        description = "Institute committees/cells (Anti-Ragging, ICC, Grievance, Academic Council, BoG, BoS, IQAC): purpose, how to reach, members with contacts. Student-run committees are under find_clubs."
    )
    fun findInstituteCommittees(
        @ToolParam(description = "Committee name or topic, e.g. 'ragging', 'harassment', 'grievance'", required = false) query: String?,
        toolContext: ToolContext
    ): AgentListResult<DMInstituteCommittee> =
        support.recorded(toolContext, "find_institute_committees", mapOf("query" to query)) { info.findCommittees(query, properties.maxToolRows) }

    @Tool(
        name = "find_scholarships",
        description = "Scholarships, fellowships and fee waivers: eligibility, benefit, awards, duration, how to apply. programme filters (B.Tech, M.Sc, BS-MS, M.Des)."
    )
    fun findScholarships(
        @ToolParam(description = "Name or keyword, e.g. 'merit', 'means', 'Cybage', 'HEST'", required = false) query: String?,
        @ToolParam(description = "Programme filter, e.g. 'B.Tech', 'M.Sc', 'BS-MS', 'M.Des'", required = false) programme: String?,
        toolContext: ToolContext
    ): AgentListResult<DMScholarship> =
        support.recorded(toolContext, "find_scholarships", mapOf("query" to query, "programme" to programme)) {
            info.findScholarships(query, programme, properties.maxToolRows)
        }

    @Tool(
        name = "get_placement_events",
        description = "Company sessions, drives and visits posted by the placement cell, newest first. company / from / to narrow. Not an exhaustive recruiter list."
    )
    fun getPlacementEvents(
        @ToolParam(description = "Company name fragment", required = false) company: String?,
        @ToolParam(description = "Start date YYYY-MM-DD", required = false) from: LocalDate?,
        @ToolParam(description = "End date YYYY-MM-DD", required = false) to: LocalDate?,
        toolContext: ToolContext
    ): AgentListResult<Map<String, Any?>> =
        support.recorded(toolContext, "get_placement_events", mapOf("company" to company, "from" to from, "to" to to)) {
            info.placementEvents(company, from, to, properties.maxToolRows)
        }

    @Tool(
        name = "find_campus_services",
        description =
            "Campus services, facilities, hostel procedures and rules: medical timings, mediclaim, library, sports, " +
                "Wi-Fi, security, laundry, courier, TV card, activity room, air cooler, parents' visit, lost & found, railway " +
                "concession, passport, hostel rules. Returns summary, timings, location, contact, fee, steps, rules. query = " +
                "topic; category = medical | library | sports | hostel | food | it-wifi | security | transport | mail-courier " +
                "| laundry | facility | procedure | rule | insurance.",
    )
    fun findCampusServices(
        @ToolParam(description = "Topic, e.g. 'laundry', 'doctor timings', 'visitor', 'wifi', 'railway concession'", required = false) query: String?,
        @ToolParam(description = "Category filter, see description", required = false) category: String?,
        toolContext: ToolContext
    ): AgentListResult<DMCampusService> =
        support.recorded(toolContext, "find_campus_services", mapOf("query" to query, "category" to category)) {
            info.findServices(query, category, properties.maxToolRows)
        }
}
