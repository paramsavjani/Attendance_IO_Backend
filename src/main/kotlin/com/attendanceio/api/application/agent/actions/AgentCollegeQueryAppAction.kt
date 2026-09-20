package com.attendanceio.api.application.agent.actions

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
import com.attendanceio.api.model.college.DMClub
import com.attendanceio.api.model.college.DMFaculty
import com.attendanceio.api.repository.college.AcademicCalendarRepository
import com.attendanceio.api.repository.college.CampusEventRepository
import com.attendanceio.api.repository.college.ClubRepository
import com.attendanceio.api.repository.college.FacultyRepository
import com.attendanceio.api.repository.college.PlacementRecruiterRepository
import com.attendanceio.api.repository.college.PlacementStatRepository
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Institute knowledge base (clubs, campus events, faculty, academic calendar, placements) shaped
 * for the chat agent. The collections are tiny (a few hundred documents in all), so matching is
 * done in memory with a forgiving text match rather than pushed to Mongo: "cult" must find the
 * Cultural Committee and "HMC" the Hostel Management Committee.
 */
@Component
class AgentCollegeQueryAppAction(
    private val clubRepository: ClubRepository,
    private val campusEventRepository: CampusEventRepository,
    private val facultyRepository: FacultyRepository,
    private val academicCalendarRepository: AcademicCalendarRepository,
    private val placementStatRepository: PlacementStatRepository,
    private val placementRecruiterRepository: PlacementRecruiterRepository
) {
    companion object {
        private val IST: ZoneId = ZoneId.of("Asia/Kolkata")
        private val EVENT_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM yyyy HH:mm")
        private val LEAD_ROLES = listOf("convener", "chief election commissioner", "chair", "secretary", "treasurer")
        /** Short names students actually use, mapped to words in the official names. */
        private val CLUB_ALIASES = mapOf(
            "cult" to "cultural", "hmc" to "hostel management", "cmc" to "cafeteria", "spc" to "placement cell",
            "ehc" to "electronics hobby", "gdg" to "google developer", "dsc" to "google developer", "debsoc" to "debating",
            "pmmc" to "photography", "dadc" to "dadc", "khoj" to "theatres", "acad" to "academic", "ec" to "election",
            "ieee" to "ieee", "muse" to "muse", "headrush" to "headrush", "fest" to "annual festival", "synapse" to "annual festival",
            "sports" to "sports", "press" to "press", "cins" to "cyber", "cyber" to "cyber", "mstc" to "microsoft",
            "prog" to "programming", "quiz" to "headrush", "dance" to "dadc", "drama" to "theatres", "garba" to "khelaiya"
        )
    }

    // ---------- clubs ----------

    fun findClubs(query: String?, type: String?, limit: Int): AgentListResult<AgentClubSummary> {
        val wanted = type?.trim()?.lowercase()?.takeIf { it.isNotBlank() && it != "all" }
        val all = clubRepository.findAll().filter { wanted == null || it.type.equals(wanted, true) || (wanted == "organisation" && it.type == "organization") }
        val matched = query.clean()?.let { q -> all.filter { it.matches(q) } } ?: all
        val items = matched.sortedBy { it.name.lowercase() }.take(limit).map { it.toSummary() }
        val note = when {
            matched.isEmpty() && query != null -> "No club or committee matched '$query'. Call find_clubs without a query to see them all."
            matched.size > limit -> "Showing $limit of ${matched.size}."
            else -> null
        }
        return AgentListResult(items, matched.size, note)
    }

    fun getClub(clubIdOrName: String, designation: String?): AgentClubDetail? {
        val club = resolveClub(clubIdOrName) ?: return null
        val wanted = designation.clean()?.lowercase()
        val members = club.members
            .filter { wanted == null || (it.designation ?: "").lowercase().contains(wanted) }
            .map { it.toAgent() }
        return AgentClubDetail(
            clubId = club.id, name = club.name, type = club.type, tag = club.tag, description = club.description,
            keyActivities = club.keyActivities, email = club.email, website = club.website, instagram = club.instagram,
            linkedin = club.linkedin, youtube = club.youtube, members = members,
            note = if (members.isEmpty()) "No members listed" + (wanted?.let { " with designation '$it'" } ?: "") + "." else null
        )
    }

    /** Which clubs a student belongs to, by name or roll number. */
    fun findClubMembers(nameOrRoll: String, limit: Int): AgentListResult<AgentClubMember> {
        val q = nameOrRoll.clean()?.lowercase() ?: return AgentListResult(emptyList(), 0, "Give a name or roll number.")
        val hits = clubRepository.findAll().flatMap { club ->
            club.members.filter { m -> m.name.lowercase().contains(q) || (m.rollNumber ?: "").contains(q) }
                .map { m -> m.toAgent(club.name) }
        }
        return AgentListResult(hits.take(limit), hits.size, if (hits.isEmpty()) "Nobody named '$nameOrRoll' is listed in any club or committee." else null)
    }

    private fun resolveClub(idOrName: String): DMClub? {
        val all = clubRepository.findAll()
        all.firstOrNull { it.id == idOrName }?.let { return it }
        val q = idOrName.clean() ?: return null
        val exact = all.filter { it.name.equals(q, true) || (it.tag ?: "").equals(q, true) }
        if (exact.size == 1) return exact.first()
        val loose = all.filter { it.matches(q) }
        return loose.singleOrNull() ?: loose.minByOrNull { it.name.length }
    }

    private fun DMClub.matches(q: String): Boolean {
        val needle = q.lowercase()
        val alias = CLUB_ALIASES[needle]
        val hay = listOfNotNull(name, tag, description, keyActivities, email).joinToString(" ").lowercase()
        return hay.contains(needle) || (alias != null && hay.contains(alias)) || needle.split(Regex("\\s+")).all { hay.contains(it) }
    }

    private fun DMClub.toSummary() = AgentClubSummary(
        clubId = id, name = name, type = type, tag = tag, email = email, instagram = instagram, website = website,
        description = description?.let { if (it.length > 220) it.take(217) + "…" else it },
        memberCount = memberCount,
        leads = members.filter { m -> LEAD_ROLES.any { (m.designation ?: "").lowercase().startsWith(it) } }
            .map { "${it.name} (${it.designation})" }
    )

    private fun com.attendanceio.api.model.college.ClubMember.toAgent(club: String? = null) =
        AgentClubMember(name = name, rollNumber = rollNumber, designation = designation, phone = phone, email = email, club = club)

    // ---------- campus events ----------

    fun upcomingEvents(club: String?, from: LocalDate?, to: LocalDate?, query: String?, limit: Int): AgentListResult<AgentCampusEvent> {
        val start = (from ?: LocalDate.now(IST)).atStartOfDay(IST).toInstant()
        val end = (to ?: (from ?: LocalDate.now(IST)).plusDays(30)).plusDays(1).atStartOfDay(IST).toInstant()
        val clubQ = club.clean()?.lowercase()
        val q = query.clean()?.lowercase()
        val all = campusEventRepository.findByEndGreaterThanEqualAndStartLessThanEqualOrderByStartAsc(start, end)
            .filter { e -> clubQ == null || (e.club ?: "").lowercase().let { it.contains(clubQ) || CLUB_ALIASES[clubQ]?.let(it::contains) == true } }
            .filter { e -> q == null || e.name.lowercase().contains(q) || (e.venue ?: "").lowercase().contains(q) }
        val items = all.take(limit).map {
            AgentCampusEvent(
                name = it.name, organiser = it.club, venue = it.venue,
                start = EVENT_TIME.format(it.start.atZone(IST)), end = EVENT_TIME.format(it.end.atZone(IST)), type = it.eventType
            )
        }
        val window = "${from ?: LocalDate.now(IST)} to ${to ?: (from ?: LocalDate.now(IST)).plusDays(30)}"
        return AgentListResult(items, all.size, if (all.isEmpty()) "No public campus events listed for $window." else "Events between $window (IST).")
    }

    // ---------- faculty ----------

    fun findFaculty(query: String?, category: String?, limit: Int): AgentListResult<AgentFacultySummary> {
        val cat = category.clean()?.lowercase()
        val q = query.clean()?.lowercase()
        val all = facultyRepository.findAll().filter { cat == null || it.category.lowercase().contains(cat) }
        val matched = if (q == null) all else all.filter { f ->
            val hay = listOfNotNull(f.name, f.researchInterests, f.specialization, f.qualification, f.teaching?.joinToString(" ")).joinToString(" ").lowercase()
            q.split(Regex("\\s+")).all { hay.contains(it) }
        }
        val items = matched.sortedBy { it.name.lowercase() }.take(limit).map { it.toSummary() }
        return AgentListResult(items, matched.size, when {
            matched.isEmpty() -> "No faculty matched '$query'. Try a surname or a research area (e.g. 'machine learning')."
            matched.size > limit -> "Showing $limit of ${matched.size}; narrow the query for a specific person."
            else -> null
        })
    }

    fun getFaculty(facultyIdOrName: String): AgentFacultyDetail? {
        val all = facultyRepository.findAll()
        val f = all.firstOrNull { it.id == facultyIdOrName }
            ?: all.filter { it.name.equals(facultyIdOrName.trim(), true) }.singleOrNull()
            ?: all.filter { it.name.lowercase().contains(facultyIdOrName.trim().lowercase()) }.singleOrNull()
            ?: return null
        return AgentFacultyDetail(
            facultyId = f.id, name = f.name, category = f.category, qualification = f.qualification, email = f.email, phone = f.phone,
            office = f.office, specialization = f.specialization ?: f.researchInterests, biography = f.biography, teaching = f.teaching,
            research = f.research, profileUrl = f.profileUrl
        )
    }

    private fun DMFaculty.toSummary() = AgentFacultySummary(
        facultyId = id, name = name, category = category, qualification = qualification, email = email, phone = phone, office = office,
        researchInterests = researchInterests ?: specialization
    )

    // ---------- academic calendar ----------

    fun academicCalendar(academicYear: String?, term: String?, query: String?): AgentListResult<AgentCalendarEntry> {
        val all = academicCalendarRepository.findAll()
        val year = academicYear.clean() ?: all.mapNotNull { it.academicYear }.maxOrNull()
        val t = term.clean()?.lowercase()
        val q = query.clean()?.lowercase()
        val rows = all
            .filter { it.academicYear == year }
            .filter { t == null || it.term.lowercase().startsWith(t) }
            .filter { q == null || it.event.lowercase().contains(q) }
            .sortedWith(compareBy({ it.startDate ?: Instant.MAX }, { it.srNo }))
        val items = rows.map { AgentCalendarEntry(it.academicYear, it.termLabel, it.event, it.dateText, it.days) }
        return AgentListResult(items, rows.size, when {
            rows.isEmpty() -> "Nothing in the $year calendar" + (t?.let { " for term '$it'" } ?: "") + (q?.let { " matching '$it'" } ?: "") + ". Years available: ${all.mapNotNull { it.academicYear }.distinct().sorted()}."
            else -> "Official DAU academic calendar $year. Terms: Autumn = Jul–Dec, Winter = Jan–May, Summer = May–Jul."
        })
    }

    // ---------- placements ----------

    fun placementStats(season: String?, level: String?): AgentListResult<AgentPlacementStat> {
        val s = season.clean()?.let(::normaliseSeason)
        val l = level.clean()?.uppercase()
        val rows = (if (s != null) placementStatRepository.findBySeasonOrderByLevelAsc(s) else placementStatRepository.findAll())
            .filter { l == null || it.level == l || it.level == "ALL" }
            .sortedWith(compareByDescending<com.attendanceio.api.model.college.DMPlacementStat> { it.season }.thenBy { it.level })
        val items = rows.map {
            AgentPlacementStat(
                season = it.season, level = it.level, audited = it.audited, asOf = it.asOf, note = it.note, metrics = it.metrics,
                salaryHeads = it.salaryHeads, offersBySector = it.offersBySector, offersByLocation = it.offersByLocation,
                prominentRecruiters = it.prominentRecruiters, notableOffers = (it.brightSparks.orEmpty() + it.starRecruits.orEmpty()).ifEmpty { null },
                source = it.source
            )
        }
        val seasons = placementStatRepository.findAll().map { it.season }.distinct().sortedDescending()
        return AgentListResult(items, rows.size, if (rows.isEmpty()) "No placement figures for that season/level. Seasons available: $seasons." else
            "Figures come from different official documents (audited IPRS report, placement brochure, website chart) and can differ in basis; quote the source and basis alongside a number.")
    }

    fun recruiters(query: String?, limit: Int): AgentListResult<AgentRecruiter> {
        val q = query.clean()?.lowercase()
        val all = placementRecruiterRepository.findAll().filter { q == null || it.name.lowercase().contains(q) }.sortedBy { it.name.lowercase() }
        return AgentListResult(all.take(limit).map { AgentRecruiter(it.name, it.seasons) }, all.size,
            if (all.isEmpty()) "No recruiter matching '$query' in the published lists." else "Recruiters named in the official placement brochure / audit report; not an exhaustive list of every company.")
    }

    private fun normaliseSeason(s: String): String {
        val m = Regex("(20\\d{2})\\D*(\\d{2,4})?").find(s) ?: return s
        val start = m.groupValues[1].toInt()
        val endGiven = m.groupValues[2]
        val end = when {
            endGiven.isEmpty() -> start + 1
            endGiven.length == 2 -> 2000 + endGiven.toInt()
            else -> endGiven.toInt()
        }
        return if (end == start + 1) "$start-${end % 100}" else "${start - 1}-${start % 100}"
    }

    private fun String?.clean(): String? = this?.trim()?.takeIf { it.isNotBlank() }
}
