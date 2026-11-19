package com.attendanceio.api.application.placement

import com.attendanceio.api.model.college.DMPlacementCompany
import com.attendanceio.api.model.college.PlacementSelection
import com.attendanceio.api.model.placement.PlacedStudent
import com.attendanceio.api.model.placement.PlacementCompanyOffers
import com.attendanceio.api.model.placement.PlacementCompanyCard
import com.attendanceio.api.model.placement.PlacementListResponse
import com.attendanceio.api.model.placement.PlacementSeasonSummary
import com.attendanceio.api.model.placement.PlacementTopRecruiter
import com.attendanceio.api.repository.college.PlacementCompanyRepository
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicReference

/**
 * The current placement season (companies, their drives and who they selected), shared by the
 * /api/placements endpoints and the assistant's placement tools. The collection is small (under a
 * hundred companies) and only changes on a re-import, so it is read once and kept for a few minutes.
 */
@Component
class PlacementQueryAppAction(
    private val repository: PlacementCompanyRepository
) {
    private data class Snapshot(val at: Long, val companies: List<DMPlacementCompany>)

    private val cache = AtomicReference<Snapshot?>(null)

    companion object {
        private const val CACHE_MS = 5 * 60 * 1000L

        /** What students type for a programme → the portal's label, compared without spaces or dots. */
        private val PROGRAMME_ALIASES = mapOf(
            "btech" to "btech", "ug" to "btech", "ict" to "btech", "cs" to "btech", "csai" to "btech", "evd" to "btech", "ece" to "btech",
            "mnc" to "btechmnc", "btechmnc" to "btechmnc",
            "mtech" to "mtech", "mtechec" to "mtechec", "ec" to "mtechec", "vlsi" to "mtechec",
            "mscit" to "mscit", "it" to "mscit", "mscds" to "mscds", "ds" to "mscds", "mscaa" to "mscaa", "aa" to "mscaa",
            "mdes" to "mdes", "des" to "mdes", "design" to "mdes"
        )

        fun norm(s: String): String = s.lowercase().replace(Regex("[^a-z0-9]"), "")
    }

    fun all(): List<DMPlacementCompany> {
        val now = System.currentTimeMillis()
        cache.get()?.takeIf { now - it.at < CACHE_MS }?.let { return it.companies }
        val fresh = repository.findAll().sortedBy { it.name.lowercase() }
        cache.set(Snapshot(now, fresh))
        return fresh
    }

    fun list(): PlacementListResponse {
        val companies = all()
        val cards = companies
            .sortedWith(compareByDescending<DMPlacementCompany> { it.lastActivity ?: "" }.thenBy { it.name.lowercase() })
            .map { it.toCard() }
        return PlacementListResponse(companies.firstOrNull()?.season, summary(companies), cards)
    }

    fun summary(companies: List<DMPlacementCompany> = all()): PlacementSeasonSummary {
        val selections = companies.flatMap { c -> c.selections.map { c to it } }
        val withCtc = selections.filter { it.second.ctcLpa != null }
        val best = withCtc.maxByOrNull { it.second.ctcLpa!! }
        fun avg(rows: List<Pair<DMPlacementCompany, PlacementSelection>>) =
            rows.takeIf { it.isNotEmpty() }?.let { r -> Math.round(r.sumOf { it.second.ctcLpa!! } / r.size * 100) / 100.0 }
        val sorted = withCtc.map { it.second.ctcLpa!! }.sorted()
        val median = if (sorted.isEmpty()) null else if (sorted.size % 2 == 1) sorted[sorted.size / 2] else (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2
        fun byCompany(kindPpo: Boolean) = companies.mapNotNull { c ->
            val rows = c.selections.filter { (it.kind == "PPO") == kindPpo }
            if (rows.isEmpty()) null else PlacementCompanyOffers(
                company = c.name,
                offers = rows.size,
                ctc = rows.mapNotNull { it.ctc }.filter { it.any(Char::isDigit) }.distinct().joinToString(" / ").ifEmpty { null },
                stipend = rows.mapNotNull { it.stipend }.distinct().joinToString(" / ").ifEmpty { null }
            )
        }.sortedWith(compareByDescending<PlacementCompanyOffers> { it.offers }.thenBy { it.company })
        val campus = selections.filter { it.second.kind != "PPO" }
        return PlacementSeasonSummary(
            companiesOnPortal = companies.count { it.onPortal },
            companiesWithSelections = companies.count { it.selectedCount > 0 },
            companiesWithNoSelection = companies.count { it.resultDeclared && it.selectedCount == 0 },
            offers = selections.size,
            ppos = selections.size - campus.size,
            campusSelections = campus.size,
            studentsSelected = selections.mapNotNull { it.second.studentId }.distinct().size,
            highestCtcLpa = best?.second?.ctcLpa,
            highestCtcCompany = best?.first?.name,
            averageCtcLpa = avg(withCtc),
            medianCtcLpa = median,
            averagePpoCtcLpa = avg(withCtc.filter { it.second.kind == "PPO" }),
            averageCampusCtcLpa = avg(withCtc.filter { it.second.kind != "PPO" }),
            offersWithCtc = withCtc.size,
            resultsUpTo = selections.mapNotNull { it.second.announcedOn }.maxOrNull(),
            updatedAt = companies.mapNotNull { it.updatedAt }.maxOrNull(),
            topRecruiters = companies.filter { it.selectedCount > 0 }.sortedByDescending { it.selectedCount }.take(6)
                .map { PlacementTopRecruiter(it.id, it.name, it.selectedCount) },
            pposByCompany = byCompany(true),
            campusByCompany = byCompany(false),
            campusByOfferType = campus.groupingBy { (it.second.offerType ?: "Not stated").trim() }.eachCount(),
            coverage = "Selections cover the B.Tech 2023 batch as announced by the placement cell; PPO packages are as reported by students. " +
                "Company packages are the advertised figures on the placement portal, not individual offer letters. " +
                "A package given as a range counts at its minimum. Averages exclude offers with no package figure."
        )
    }

    /** By id, exact name or alias, then the shortest name containing the text. */
    fun find(nameOrId: String): DMPlacementCompany? {
        val all = all()
        val n = norm(nameOrId)
        if (n.isEmpty()) return null
        all.firstOrNull { it.id == nameOrId.trim().lowercase() }?.let { return it }
        all.firstOrNull { norm(it.name) == n || it.aliases.any { a -> norm(a) == n } }?.let { return it }
        val hits = all.filter { c -> norm(c.name).contains(n) || c.aliases.any { norm(it).contains(n) } || (n.length >= 4 && n.contains(norm(c.name))) }
        return hits.minByOrNull { it.name.length }
    }

    fun search(query: String?, programme: String?, offerType: String?, minLpa: Double?, sortBy: String?): List<DMPlacementCompany> {
        val q = query?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
        val p = programme?.let { PROGRAMME_ALIASES[norm(it)] ?: norm(it) }?.takeIf { it.isNotEmpty() }
        val t = offerType?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
        return all()
            .filter { c -> p == null || c.programmes.any { norm(it) == p } }
            .filter { c -> t == null || c.offerTypes.any { it.lowercase().startsWith(t.take(3)) } }
            .filter { c -> minLpa == null || (c.maxCtcLpa ?: 0.0) >= minLpa }
            .filter { c ->
                q == null || q.split(Regex("\\s+")).all { w ->
                    (listOf(c.name) + c.aliases + c.roles + c.locations +
                        c.drives.flatMap { d -> d.profiles.mapNotNull { it.description } + d.jdFiles.map { it.text } })
                        .joinToString(" ").lowercase().contains(w)
                }
            }
            .sortedWith(
                when (sortBy?.lowercase()) {
                    "ctc", "package", "lpa" -> compareByDescending<DMPlacementCompany> { it.maxCtcLpa ?: -1.0 }.thenBy { it.name }
                    "selected", "selections", "offers" -> compareByDescending<DMPlacementCompany> { it.selectedCount }.thenBy { it.name }
                    else -> compareByDescending<DMPlacementCompany> { it.lastActivity ?: "" }.thenBy { it.name }
                }
            )
    }

    /** Offers held by students whose name or roll number matches, optionally at one company. */
    fun placedStudents(query: String?, company: String?): List<PlacedStudent> {
        val q = query?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
        val c = company?.let { find(it) }
        return all()
            .filter { c == null || it.id == c.id }
            .flatMap { co -> co.selections.map { co to it } }
            .filter { (_, s) -> q == null || (s.studentId ?: "").contains(q) || q.split(Regex("\\s+")).all { w -> (s.name ?: "").lowercase().contains(w) } }
            .sortedWith(compareBy<Pair<DMPlacementCompany, PlacementSelection>> { it.second.name ?: "" }.thenBy { it.second.announcedOn ?: "" })
            .map { (co, s) -> PlacedStudent(s.studentId, s.name, co.id, co.name, s.kind, s.role, s.ctc, s.ctcLpa, s.stipend, s.offerType, s.announcedOn) }
    }

    private fun DMPlacementCompany.toCard() = PlacementCompanyCard(
        id = id,
        name = name,
        offerTypes = offerTypes,
        programmes = programmes,
        roles = roles.take(4),
        maxCtcLpa = maxCtcLpa,
        minCtcLpa = minCtcLpa,
        minCpi = minCpi,
        selectedCount = selectedCount,
        ppoCount = ppoCount,
        resultDeclared = resultDeclared || selectedCount > 0,
        lastActivity = lastActivity,
        onPortal = onPortal
    )
}
