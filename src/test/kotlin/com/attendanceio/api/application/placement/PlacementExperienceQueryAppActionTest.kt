package com.attendanceio.api.application.placement

import com.attendanceio.api.model.college.DMPlacementExperience
import com.attendanceio.api.repository.college.PlacementExperienceRepository
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlacementExperienceQueryAppActionTest {
    private fun exp(id: String, companyId: String, company: String, verdict: String, words: Int, topics: List<String> = emptyList(), body: String = "") =
        DMPlacementExperience(id, companyId, true, company, "2025-26", "Internship", verdict = verdict, topics = topics, summary = "s", body = body, words = words)

    private val action = PlacementExperienceQueryAppAction(mock(PlacementExperienceRepository::class.java).also {
        `when`(it.findAll()).thenReturn(listOf(
            exp("google-01", "google", "Google", "Not shared", 300, listOf("DFS")),
            exp("google-02", "google", "Google", "Selected", 100, listOf("Trees")),
            exp("wells-fargo-01", "wells-fargo", "Wells Fargo", "Not selected", 500, body = "Asked about SQL joins"),
            exp("bny-01", "bny", "BNY", "Selected", 200, listOf("Puzzles"))
        ))
    })

    @Test
    fun `lists summaries per company, most shared first, without the text`() {
        val list = action.list(null)
        assertEquals(4, list.total)
        assertEquals(listOf("google", "bny", "wells-fargo"), list.companies.map { it.id })
        assertEquals(1, list.companies.first().selected)
        // Selected experiences come first within a company
        assertEquals(listOf("google-02", "google-01"), list.experiences.filter { it.companyId == "google" }.map { it.id })
        assertEquals(mapOf("google" to 2, "bny" to 1, "wells-fargo" to 1), action.countsByCompany())
    }

    @Test
    fun `filters by company and finds topics in tags or text`() {
        assertEquals(2, action.list("google").total)
        assertEquals(listOf("wells-fargo-01"), action.search(null, "sql").map { it.id })
        assertEquals(listOf("google-02"), action.search("google", "tree").map { it.id })
        assertEquals(listOf("wells-fargo-01"), action.search("Wells Fargo", null).map { it.id })
        assertNull(action.find("nope"))
    }
}
