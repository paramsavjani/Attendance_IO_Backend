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
        description = "DAU's official holiday list for a calendar year (from the institute circular): holiday name, date and weekday. " +
            "Use for 'is 14 Jan a holiday', 'holidays in October', 'next holiday'. Pass from/to to narrow the window."
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
        description = "Official contacts of DAU offices and staff a student may need: Dean of Students office, hostel wardens and " +
            "supervisors, medical centre / doctors, counsellor, registrar, placement office, security, library, sports, IT support, " +
            "director. Returns designation, name, room, phone numbers (office + mobile where listed) and email. query matches a role " +
            "or name ('warden', 'doctor', 'registrar', 'Sharma'); category = dean-students | hostel | medical | counselling | " +
            "security | registrar | placement | academics | library | sports | administration | it-support | director."
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
        description = "Degree programmes DAU offers (B.Tech. ICT / CS&AI / MnC / EVD / ECE-AI, BS-MS IT / DS&AI, M.Tech. ICT, M.Sc. IT / DS / " +
            "Agri-Analytics, M.Des., Ph.D.) with degree, level, duration, intake, admission route, eligibility, fees where published, and a short " +
            "description. Use for 'which courses can I do here', 'how long is M.Des', 'how do I get into M.Tech'."
    )
    fun listProgrammes(
        @ToolParam(description = "Name fragment, e.g. 'data science', 'ICT'", required = false) query: String?,
        @ToolParam(description = "UG | PG | Doctoral | Dual", required = false) level: String?,
        toolContext: ToolContext
    ): AgentListResult<DMProgram> =
        support.recorded(toolContext, "list_programmes", mapOf("query" to query, "level" to level)) { info.listProgrammes(query, level) }

    @Tool(
        name = "get_curriculum",
        description = "Semester-wise course structure of one programme from its official page: course code, title, credits, L-T-P-C and " +
            "type per semester, plus elective pools and credit rules. Use for 'what subjects are in sem 3 of ICT', 'credits of DSA', " +
            "'electives in MnC', 'total credits for B.Tech'. programme accepts short names (ICT, CSAI, MnC, EVD, ECE-AI, BS-MS IT, " +
            "MTech, MSc DS, MDes, PhD). For the subjects actually running this term use list_subjects instead."
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
        description = "Institute-level committees and cells with purpose, how a student reaches them, and members with designation/" +
            "phone/email: Anti-Ragging Committee & Squad, Internal Complaints Committee (ICC), Grievance Redressal, Academic Council, " +
            "Board of Governors, Board of Studies, IQAC. Use for 'whom do I report ragging to', 'ICC contact', 'who is on the academic " +
            "council'. Student-run committees (Cultural, Sports, HMC…) are under find_clubs instead."
    )
    fun findInstituteCommittees(
        @ToolParam(description = "Committee name or topic, e.g. 'ragging', 'harassment', 'grievance'", required = false) query: String?,
        toolContext: ToolContext
    ): AgentListResult<DMInstituteCommittee> =
        support.recorded(toolContext, "find_institute_committees", mapOf("query" to query)) { info.findCommittees(query, properties.maxToolRows) }

    @Tool(
        name = "find_scholarships",
        description = "Scholarships, fellowships and fee waivers published by DAU: name, sponsor, programmes, eligibility (rank/percentage/" +
            "income limits), benefit amount, number of awards, duration, continuation condition, how to apply. Use for 'scholarships for " +
            "B.Tech', 'MCM scholarship eligibility', 'is there a fee waiver for M.Sc'."
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
        description = "Company sessions, drives, visits and offer announcements posted by DAU's placement cell, with dates. Use for 'did " +
            "Injala come to campus', 'recent placement sessions', 'when was the Google drive'. Newest first; company narrows to one " +
            "company; from/to narrow the dates. Not an exhaustive recruiter list — for that use list_placement_recruiters."
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
        description = "Campus services, facilities, hostel procedures and rules as records: medical centre & doctor timings, ambulance, " +
            "mediclaim, library/resource centre, sports complex, Wi-Fi/IT, security, laundry, courier & post, TV card, activity room, " +
            "air cooler policy, parents' visit / guest accommodation, lost & found, railway concession, passport procedure, hostel " +
            "rules & disciplinary guidelines. Each record has a summary, timings, location, contact, fee, steps and key rules. " +
            "query is a plain topic word; category = medical | library | sports | hostel | food | it-wifi | security | transport | " +
            "mail-courier | laundry | facility | procedure | rule | insurance."
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
