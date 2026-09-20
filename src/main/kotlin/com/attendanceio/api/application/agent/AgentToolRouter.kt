package com.attendanceio.api.application.agent

import com.attendanceio.api.application.agent.tools.AlumniAgentTools
import com.attendanceio.api.application.agent.tools.AnalyticsAgentTools
import com.attendanceio.api.application.agent.tools.CampusInfoAgentTools
import com.attendanceio.api.application.agent.tools.CatalogAgentTools
import com.attendanceio.api.application.agent.tools.CollegeAgentTools
import com.attendanceio.api.application.agent.tools.MyAttendanceAgentTools
import com.attendanceio.api.application.agent.tools.PlanningAgentTools
import com.attendanceio.api.application.agent.tools.StudentAgentTools
import com.attendanceio.api.model.agent.AgentMessageRole
import org.springframework.stereotype.Component

/**
 * Picks which tool groups a turn is offered. The tool schemas are two thirds of every model
 * call's fixed cost (~6k tokens for all 37), and most questions need one group, so the groups
 * whose vocabulary does not appear anywhere in the thread are left out. Matching is deliberately
 * generous and looks at every user message of the thread, so a follow-up ("and his phone?") keeps
 * the groups its thread started with; when nothing matches at all, everything is offered.
 */
@Component
class AgentToolRouter(
    myAttendanceTools: MyAttendanceAgentTools,
    studentTools: StudentAgentTools,
    catalogTools: CatalogAgentTools,
    analyticsTools: AnalyticsAgentTools,
    planningTools: PlanningAgentTools,
    alumniTools: AlumniAgentTools,
    collegeTools: CollegeAgentTools,
    campusInfoTools: CampusInfoAgentTools
) {
    enum class Group { ATTENDANCE, ALUMNI, COLLEGE, CAMPUS }

    data class Selection(val groups: Set<Group>, val toolObjects: List<Any>, val fallback: Boolean)

    private val objects: Map<Group, List<Any>> = mapOf(
        Group.ATTENDANCE to listOf(myAttendanceTools, studentTools, catalogTools, analyticsTools, planningTools),
        Group.ALUMNI to listOf(alumniTools),
        Group.COLLEGE to listOf(collegeTools),
        Group.CAMPUS to listOf(campusInfoTools)
    )

    private companion object {
        fun words(vararg w: String) = Regex("(?i)\\b(" + w.joinToString("|") + ")")

        val ATTENDANCE = words(
            "attendance", "attend", "present", "absent", "bunk", "skip", "miss", "missed", "lecture", "class", "classes", "timetable",
            "time ?table", "subject", "semester", "sem\\b", "average", "batch", "compare", "percent", "%", "criteria", "official",
            "lab", "tutorial", "unmarked", "mark", "trend", "improv", "week", "yesterday", "today", "tomorrow", "schedule", "room",
            "top \\d", "bottom \\d", "below", "above", "my ", "\\bme\\b", "\\bi\\b", "kitna", "kitni", "kaunsa", "konsa", "lag(ta|ti)",
            "20\\d{7}", "ct\\d{3}", "cs\\d{3}", "it\\d{3}", "cp\\d{4}", "el\\d{3}", "hm\\d{3}", "sc\\d{3}", "ds\\d{3}", "ic-?\\d{3}", "pc-?\\d{3}"
        )
        val ALUMNI = words(
            "alumn", "senior", "graduate", "linkedin", "referral", "package", "lpa", "compan", "works? at", "working", "job", "career",
            "hire", "hiring", "recruit", "startup", "bangalore", "bengaluru", "hyderabad", "pune", "mumbai", "gurgaon", "noida", "bay area",
            "abroad", "20(0|1|2)\\d batch", "batch of", "data scientist", "engineer", "manager", "sde", "role", "network"
        )
        val COLLEGE = words(
            "club", "committee", "commit", "convener", "convenor", "core", "member", "sbg", "cult", "hmc", "cmc", "spc", "ehc", "gdg", "dsc",
            "debsoc", "pmmc", "dadc", "khoj", "ieee", "muse", "headrush", "khelaiya", "synapse", "concours", "i\\.?fest", "tarang", "event",
            "fest", "garba", "night", "session", "workshop", "screening", "hackathon", "competition", "faculty", "professor", "prof\\b",
            "\\bdr\\b", "teach", "teacher", "sir\\b", "ma'?am", "research", "calendar", "exam", "end[- ]?sem", "mid[- ]?sem", "in[- ]?sem",
            "registration", "add[- ]?drop", "drop", "break", "vacation", "result", "convocation", "placement", "placed", "ctc", "package",
            "recruit", "highest", "median", "stipend", "offer", "chhutti", "chutti", "khel", "sports", "cultural"
        )
        val CAMPUS = words(
            "holiday", "warden", "hostel", "supervisor", "contact", "phone", "number", "mobile", "email", "reach", "dean", "registrar",
            "director", "office", "medical", "doctor", "nurse", "ambulance", "hospital", "counsel", "library", "resource centre", "sports",
            "gym", "wifi", "wi-fi", "internet", "laundry", "courier", "post", "parcel", "tv card", "activity room", "parents", "guest",
            "lost", "found", "railway", "concession", "passport", "mediclaim", "insurance", "rule", "polic", "discipline", "fine",
            "curriculum", "course", "credit", "syllabus", "elective", "programme", "program", "b\\.?tech", "bs-?ms", "m\\.?tech", "m\\.?sc",
            "m\\.?des", "ph\\.?d", "ict\\b", "csai", "mnc", "evd", "ece", "scholarship", "fee", "waiver", "merit", "ragging", "icc", "harass",
            "grievance", "complain", "anti", "cafeteria", "mess", "canteen", "food", "security", "bank", "atm", "visit", "drive", "session",
            "placement", "committee", "chhutti", "chutti", "sec\\b", "sem ?\\d", "semester \\d"
        )
    }

    fun select(message: String, history: List<StoredAgentMessage>): Selection {
        val text = (history.filter { it.role == AgentMessageRole.USER }.map { it.content } + message).joinToString("\n")
        val groups = buildSet {
            if (ATTENDANCE.containsMatchIn(text)) add(Group.ATTENDANCE)
            if (ALUMNI.containsMatchIn(text)) add(Group.ALUMNI)
            if (COLLEGE.containsMatchIn(text)) add(Group.COLLEGE)
            if (CAMPUS.containsMatchIn(text)) add(Group.CAMPUS)
        }
        val fallback = groups.isEmpty()
        val chosen = if (fallback) Group.entries.toSet() else groups
        return Selection(chosen, chosen.flatMap { objects.getValue(it) }, fallback)
    }
}
