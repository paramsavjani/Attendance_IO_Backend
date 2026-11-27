package com.attendanceio.api.application.placement

import com.attendanceio.api.model.college.DMPlacementExperience
import com.attendanceio.api.model.placement.ExperienceCompany
import com.attendanceio.api.model.placement.ExperienceListResponse
import com.attendanceio.api.model.placement.ExperienceSummary
import com.attendanceio.api.repository.college.PlacementExperienceRepository
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicReference

/**
 * Seniors' interview experiences, shared by /api/placements/experiences and the assistant. A few dozen documents
 * that only change on a re-import, so they are read once and kept for a few minutes, like the placement list.
 */
@Component
class PlacementExperienceQueryAppAction(private val repository: PlacementExperienceRepository) {
    private data class Snapshot(val at: Long, val rows: List<DMPlacementExperience>)

    private val cache = AtomicReference<Snapshot?>(null)

    companion object {
        private const val CACHE_MS = 5 * 60 * 1000L
        private val VERDICT_ORDER = mapOf("Selected" to 0, "Not shared" to 1, "Not selected" to 2)
    }

    /** Selected first (most useful to read), then the longest, most detailed ones. */
    fun all(): List<DMPlacementExperience> {
        val now = System.currentTimeMillis()
        cache.get()?.takeIf { now - it.at < CACHE_MS }?.let { return it.rows }
        val fresh = repository.findAll().sortedWith(
            compareBy<DMPlacementExperience> { it.company.lowercase() }
                .thenBy { VERDICT_ORDER[it.verdict] ?: 3 }
                .thenByDescending { it.words }
        )
        cache.set(Snapshot(now, fresh))
        return fresh
    }

    fun find(id: String): DMPlacementExperience? = all().firstOrNull { it.id == id }

    /** How many experiences each company has, by its placement_companies id. */
    fun countsByCompany(): Map<String, Int> = all().groupingBy { it.companyId }.eachCount()

    /** Every experience without its body (or one company's), plus one row per company for the browse screen. */
    fun list(companyId: String?): ExperienceListResponse {
        val rows = all().filter { companyId == null || it.companyId == companyId }
        val companies = rows.groupBy { it.companyId }.map { (id, list) ->
            ExperienceCompany(id, list.first().company, list.first().inPlacements, list.size, list.count { it.verdict == "Selected" })
        }.sortedWith(compareByDescending<ExperienceCompany> { it.count }.thenBy { it.name.lowercase() })
        return ExperienceListResponse(rows.size, companies, rows.map { it.toSummary() })
    }

    /** For the assistant: one company's experiences, or every one that mentions a topic. */
    fun search(company: String?, topic: String?): List<DMPlacementExperience> {
        val c = company?.let { PlacementQueryAppAction.norm(it) }?.takeIf { it.isNotEmpty() }
        val t = topic?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
        return all()
            .filter { e -> c == null || PlacementQueryAppAction.norm(e.company).contains(c) || PlacementQueryAppAction.norm(e.companyId).contains(c) || c.contains(PlacementQueryAppAction.norm(e.company)) }
            .filter { e -> t == null || e.topics.any { it.lowercase().contains(t) } || e.body.lowercase().contains(t) }
    }

    private fun DMPlacementExperience.toSummary() = ExperienceSummary(
        id = id, companyId = companyId, company = company, inPlacements = inPlacements, season = season, offerType = offerType,
        role = role, batch = batch, verdict = verdict, rounds = rounds, topics = topics, summary = summary, readMinutes = readMinutes
    )
}
