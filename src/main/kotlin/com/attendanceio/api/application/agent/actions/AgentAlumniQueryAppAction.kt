package com.attendanceio.api.application.agent.actions

import com.attendanceio.api.model.agent.AgentAlumniCompany
import com.attendanceio.api.model.agent.AgentAlumnus
import com.attendanceio.api.model.agent.AgentListResult
import com.attendanceio.api.model.alumni.DMAlumni
import com.attendanceio.api.repository.alumni.AlumniRepositoryAppAction
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * Alumni directory for the agent, on top of the same repository the /api/alumni endpoints use.
 * Adds the two things a chat needs that the REST API leaves to the client: resolving a company
 * typed by name, and searching several cities at once ("in Gujarat").
 */
@Component
class AgentAlumniQueryAppAction(
    private val alumniRepositoryAppAction: AlumniRepositoryAppAction
) {
    companion object {
        /** Regions students ask about by name; expanded to the cities in the data. */
        val REGION_CITIES: Map<String, List<String>> = mapOf(
            "gujarat" to listOf("Ahmedabad", "Gandhinagar", "Surat", "Vadodara", "Rajkot", "Anand", "Bhavnagar", "Jamnagar"),
            "ncr" to listOf("Delhi", "New Delhi", "Gurgaon", "Gurugram", "Noida", "Faridabad", "Ghaziabad"),
            "delhi ncr" to listOf("Delhi", "New Delhi", "Gurgaon", "Gurugram", "Noida", "Faridabad", "Ghaziabad"),
            "bay area" to listOf("San Francisco", "San Jose", "Mountain View", "Sunnyvale", "Palo Alto", "Menlo Park", "Cupertino", "Santa Clara", "Redwood City"),
            "usa" to emptyList(), // handled by the model: ask for a city
        )
    }

    @Transactional(readOnly = true) // company is lazy; map it while the session is open
    fun search(
        query: String?,
        company: String?,
        batch: Int?,
        course: String?,
        city: String?,
        linkedinOnly: Boolean,
        limit: Int
    ): AgentListResult<AgentAlumnus> {
        val companyId = company?.trim()?.takeIf { it.isNotEmpty() }?.let { name ->
            resolveCompanyId(name) ?: return AgentListResult(emptyList(), 0, "No company named '$name' in the alumni directory. Try list_alumni_companies with a shorter name.")
        }
        val cities = expandCities(city)
        if (cities.isEmpty()) {
            val page = alumniRepositoryAppAction.search(query, companyId, batch, course, null, linkedinOnly, 0, limit)
            return AgentListResult(page.content.map { it.toAgent() }, page.totalElements.toInt(), truncationNote(page.totalElements, limit))
        }
        // One query per city, merged and re-ranked by company pay, then batch.
        var total = 0L
        val merged = cities.flatMap { c ->
            val page = alumniRepositoryAppAction.search(query, companyId, batch, course, c, linkedinOnly, 0, limit)
            total += page.totalElements
            page.content
        }.distinctBy { it.id }
            .sortedWith(compareByDescending<DMAlumni> { it.company.avgLpa ?: java.math.BigDecimal.ZERO }.thenByDescending { it.batch ?: 0 })
            .take(limit)
        return AgentListResult(merged.map { it.toAgent() }, total.toInt(), truncationNote(total, limit))
    }

    @Transactional(readOnly = true) // company is lazy; map it while the session is open
    fun companies(query: String?, sortBy: String?, limit: Int): AgentListResult<AgentAlumniCompany> {
        val page = alumniRepositoryAppAction.searchCompanies(query, if (sortBy.equals("lpa", true)) "lpa" else "count", 0, limit)
        return AgentListResult(
            page.content.map { AgentAlumniCompany(it.name, it.alumniCount, it.avgLpa) },
            page.totalElements.toInt(),
            truncationNote(page.totalElements, limit)
        )
    }

    /** Exact name first, otherwise the best-populated company containing the text. */
    private fun resolveCompanyId(name: String): Long? {
        val page = alumniRepositoryAppAction.searchCompanies(name, "count", 0, 5)
        val exact = page.content.firstOrNull { it.name.equals(name, ignoreCase = true) }
        return (exact ?: page.content.firstOrNull())?.id
    }

    /** "Gujarat" → its cities; "Ahmedabad, Surat" → both; a plain city → itself. */
    private fun expandCities(city: String?): List<String> {
        val raw = city?.trim()?.takeIf { it.isNotEmpty() } ?: return emptyList()
        return raw.split(',', '/', ';').map { it.trim() }.filter { it.isNotEmpty() }
            .flatMap { REGION_CITIES[it.lowercase()] ?: listOf(it) }
            .distinct()
    }

    private fun truncationNote(total: Long, limit: Int): String? =
        if (total > limit) "Showing $limit of $total matches; narrow the search (company, batch, city) to see others." else null

    private fun DMAlumni.toAgent() = AgentAlumnus(
        name = name,
        title = title ?: headline,
        company = company.name,
        companyAvgLpa = company.avgLpa,
        city = city,
        batch = batch,
        course = course,
        linkedinUrl = linkedinUrl,
        almaconnectUrl = almaconnectUrl
    )
}
