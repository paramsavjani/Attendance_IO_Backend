package com.attendanceio.api.application.agent.actions

import com.attendanceio.api.model.agent.AgentListResult
import com.attendanceio.api.model.college.DMCampusService
import com.attendanceio.api.model.college.DMCurriculum
import com.attendanceio.api.model.college.DMHoliday
import com.attendanceio.api.model.college.DMInstituteCommittee
import com.attendanceio.api.model.college.DMPlacementEvent
import com.attendanceio.api.model.college.DMProgram
import com.attendanceio.api.model.college.DMScholarship
import com.attendanceio.api.model.college.DMStaffContact
import com.attendanceio.api.repository.college.CampusServiceRepository
import com.attendanceio.api.repository.college.CurriculumRepository
import com.attendanceio.api.repository.college.HolidayRepository
import com.attendanceio.api.repository.college.InstituteCommitteeRepository
import com.attendanceio.api.repository.college.PlacementEventRepository
import com.attendanceio.api.repository.college.ProgramRepository
import com.attendanceio.api.repository.college.ScholarshipRepository
import com.attendanceio.api.repository.college.StaffContactRepository
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Second half of the institute knowledge base: contacts, holidays, curriculum, committees,
 * scholarships, placement events, campus services and programmes. Same approach as
 * [AgentCollegeQueryAppAction]: collections are small, so filtering is a forgiving in-memory
 * text match, and results go back as the stored documents (already compact) inside
 * [AgentListResult] so the model knows the true count.
 */
@Component
class AgentCampusInfoQueryAppAction(
    private val holidays: HolidayRepository,
    private val contacts: StaffContactRepository,
    private val curricula: CurriculumRepository,
    private val committees: InstituteCommitteeRepository,
    private val scholarships: ScholarshipRepository,
    private val placementEvents: PlacementEventRepository,
    private val services: CampusServiceRepository,
    private val programs: ProgramRepository
) {
    companion object {
        private val IST: ZoneId = ZoneId.of("Asia/Kolkata")
        private val DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM yyyy")
        private val PROGRAMME_ALIASES = mapOf(
            "ict" to "b.tech. ict", "cs" to "cs & ai", "csai" to "cs & ai", "cs&ai" to "cs & ai", "mnc" to "mnc", "evd" to "evd", "ece" to "ece-ai",
            "it" to "it", "ds" to "ds", "dsai" to "ds & ai", "mtech" to "m.tech", "msc" to "m.sc", "mdes" to "m.des", "phd" to "ph.d", "bsms" to "bs-ms"
        )
    }

    // ---------- holidays ----------

    fun holidays(year: Int?, from: LocalDate?, to: LocalDate?): AgentListResult<Map<String, Any?>> {
        val all = holidays.findAll().sortedBy { it.srNo }
        val y = year ?: LocalDate.now(IST).year
        val rows = all.filter { it.year == y }.filter { h ->
            val d = h.date?.atZone(IST)?.toLocalDate()
            (from == null || (d != null && !d.isBefore(from))) && (to == null || (d != null && !d.isAfter(to)))
        }
        return AgentListResult(rows.map { it.toRow() }, rows.size, when {
            all.none { it.year == y } -> "No holiday list for $y. Years available: ${all.map { it.year }.distinct().sorted()}."
            rows.isEmpty() -> "No institute holidays in that window."
            else -> "Official DAU holiday circular for $y. Sundays and 2nd/4th Saturdays are not listed here; check the academic calendar for term breaks."
        })
    }

    private fun DMHoliday.toRow() = mapOf("holiday" to holiday, "date" to (date?.atZone(IST)?.let(DAY::format) ?: dateText), "day" to day)

    // ---------- staff contacts ----------

    fun findContacts(query: String?, category: String?, limit: Int): AgentListResult<DMStaffContact> {
        val q = query.clean()?.lowercase()
        val c = category.clean()?.lowercase()
        val all = contacts.findAll()
        val rows = all.filter { c == null || it.category == c || it.category.contains(c) }
            .filter { x -> q == null || q.split(Regex("\\s+")).all { w -> listOfNotNull(x.name, x.designation, x.office, x.category, x.notes, x.email).joinToString(" ").lowercase().contains(w) } }
            .sortedWith(compareBy({ it.category }, { it.designation }))
        return AgentListResult(rows.take(limit), rows.size, when {
            rows.isEmpty() -> "No contact matched. Categories: ${all.map { it.category }.distinct().sorted()}. Try a role word like 'warden', 'dean', 'registrar', 'doctor'."
            rows.size > limit -> "Showing $limit of ${rows.size}; narrow by category or role."
            else -> "Official contacts from the DAU website / hostel office pages; office numbers are 079-6826xxxx, extensions are the last 3 digits."
        })
    }

    // ---------- curriculum ----------

    fun curriculum(programme: String, semester: Int?, courseQuery: String?): Map<String, Any?>? {
        val doc = resolveProgramme(programme) ?: return null
        val q = courseQuery.clean()?.lowercase()
        val sems = doc.semesters
            .filter { semester == null || it.semester == semester }
            .map { s ->
                val courses = s.courses.filter { c -> q == null || listOfNotNull(c.code, c.title, c.type).joinToString(" ").lowercase().contains(q) }
                mapOf("semester" to s.semester, "label" to s.label,
                    "courses" to courses.map { c -> mapOf("code" to c.code, "title" to c.title, "credits" to c.credits, "ltpc" to c.ltpc, "type" to c.type) },
                    "semesterCredits" to courses.mapNotNull { it.credits }.sum().takeIf { courses.any { c -> c.credits != null } })
            }
            .filter { (it["courses"] as List<*>).isNotEmpty() }
        return mapOf(
            "programme" to doc.programme, "totalCredits" to doc.totalCredits, "structureNotes" to doc.structureNotes,
            "semesters" to sems, "electivePools" to if (q == null && semester == null) doc.electivePools else null,
            "source" to doc.source,
            "note" to when {
                sems.isEmpty() && semester != null -> "No semester $semester listed for ${doc.programme}; semesters available: ${doc.semesters.mapNotNull { it.semester }.distinct().sorted()}."
                sems.isEmpty() -> "No course matched '$courseQuery' in ${doc.programme}."
                else -> "From the official programme page; the live course offering for a term is in list_subjects. Credits follow L-T-P-C."
            }
        )
    }

    fun listProgrammes(query: String?, level: String?): AgentListResult<DMProgram> {
        val q = query.clean()?.lowercase()
        val l = level.clean()?.uppercase()
        val rows = programs.findAll()
            .filter { l == null || (it.level ?: "").uppercase().startsWith(l) }
            .filter { p -> q == null || listOfNotNull(p.name, p.degree, p.school, p.description, p.specialisations.joinToString(" ")).joinToString(" ").lowercase().contains(q) }
            .sortedWith(compareBy({ it.level }, { it.name }))
        return AgentListResult(rows, rows.size, if (rows.isEmpty()) "No programme matched." else "Programmes offered by DAU per its website; use get_curriculum for the semester-wise courses of one programme.")
    }

    private fun resolveProgramme(name: String): DMCurriculum? {
        val all = curricula.findAll()
        val raw = name.trim().lowercase()
        val needle = PROGRAMME_ALIASES[raw.replace(Regex("[^a-z&]"), "")] ?: raw
        all.firstOrNull { it.id == raw || it.programme.equals(name.trim(), true) }?.let { return it }
        val hits = all.filter { it.programme.lowercase().contains(needle) || needle.split(Regex("[\\s.]+")).filter { w -> w.isNotBlank() }.all { w -> it.programme.lowercase().contains(w) } }
        return hits.singleOrNull() ?: hits.minByOrNull { it.programme.length }
    }

    // ---------- committees ----------

    fun findCommittees(query: String?, limit: Int): AgentListResult<DMInstituteCommittee> {
        val q = query.clean()?.lowercase()
        val rows = committees.findAll().filter { c -> q == null || listOfNotNull(c.name, c.purpose, c.howToReach).joinToString(" ").lowercase().let { hay -> q.split(Regex("\\s+")).all(hay::contains) } }
            .sortedBy { it.name }
        return AgentListResult(rows.take(limit), rows.size, if (rows.isEmpty()) "No institute committee matched; try 'anti-ragging', 'ICC', 'grievance', 'academic council'." else "Institute-level bodies from official DAU notifications; members are staff/faculty, not students.")
    }

    // ---------- scholarships ----------

    fun findScholarships(query: String?, programme: String?, limit: Int): AgentListResult<DMScholarship> {
        val q = query.clean()?.lowercase()
        val p = programme.clean()?.lowercase()
        val rows = scholarships.findAll()
            .filter { s -> p == null || s.programmes.any { it.lowercase().contains(p) } || s.programmes.isEmpty() }
            .filter { s -> q == null || listOfNotNull(s.name, s.sponsor, s.eligibility, s.benefit).joinToString(" ").lowercase().let { hay -> q.split(Regex("\\s+")).all(hay::contains) } }
            .sortedBy { it.name }
        return AgentListResult(rows.take(limit), rows.size, if (rows.isEmpty()) "No scholarship matched." else "Scholarships as published on daiict.ac.in; amounts and criteria can change each year — point the user to the source page for the current notification.")
    }

    // ---------- placement events ----------

    fun placementEvents(company: String?, from: LocalDate?, to: LocalDate?, limit: Int): AgentListResult<Map<String, Any?>> {
        val c = company.clean()?.lowercase()
        val rows = placementEvents.findAll()
            .filter { e -> c == null || (e.company ?: "").lowercase().contains(c) || e.summary.lowercase().contains(c) }
            .filter { e ->
                val d = e.date?.atZone(IST)?.toLocalDate()
                (from == null || (d != null && !d.isBefore(from))) && (to == null || (d != null && !d.isAfter(to)))
            }
            .sortedByDescending { it.date }
        val items = rows.take(limit).map { e -> mapOf("company" to e.company, "type" to e.eventType, "date" to (e.date?.atZone(IST)?.let(DAY::format) ?: e.dateText), "summary" to e.summary, "roles" to e.roles.ifEmpty { null }, "ctc" to e.ctc) }
        return AgentListResult(items, rows.size, if (rows.isEmpty()) "No placement-cell event matched." else "From the placement cell's news posters — company sessions, drives and visits; not a complete list of every company that recruited.")
    }

    // ---------- campus services ----------

    fun findServices(query: String?, category: String?, limit: Int): AgentListResult<DMCampusService> {
        val q = query.clean()?.lowercase()
        val c = category.clean()?.lowercase()
        val all = services.findAll()
        val rows = all.filter { c == null || (it.category ?: "").contains(c) }
            .filter { s -> q == null || listOfNotNull(s.name, s.category, s.summary, s.pageTitle, s.rules.joinToString(" "), s.steps.joinToString(" ")).joinToString(" ").lowercase().let { hay -> q.split(Regex("\\s+")).all(hay::contains) } }
            .sortedWith(compareBy({ it.category }, { it.name }))
        return AgentListResult(rows.take(limit), rows.size, when {
            rows.isEmpty() -> "Nothing matched. Categories: ${all.mapNotNull { it.category }.distinct().sorted()}. Try a plain word like 'laundry', 'doctor', 'wifi', 'courier', 'visitor'."
            else -> "From the DAU website and hostel office pages; timings and fees are as published there."
        })
    }

    private fun String?.clean(): String? = this?.trim()?.takeIf { it.isNotBlank() }
}
