package com.attendanceio.api.application.agent

import com.attendanceio.api.application.agent.actions.AgentAlumniQueryAppAction
import com.attendanceio.api.model.alumni.DMAlumni
import com.attendanceio.api.model.alumni.DMAlumniCompany
import com.attendanceio.api.repository.alumni.AlumniCompanyRepository
import com.attendanceio.api.repository.alumni.AlumniRepository
import com.attendanceio.api.repository.alumni.AlumniRepositoryAppAction
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AgentAlumniQueryAppActionTest {
    private fun company(id: Long, name: String, lpa: String?) = DMAlumniCompany().apply { this.id = id; this.name = name; this.avgLpa = lpa?.let(::BigDecimal) }
    private fun alumnus(id: Long, name: String, company: DMAlumniCompany, city: String, batch: Int) =
        DMAlumni().apply { this.id = id; this.name = name; this.company = company; this.city = city; this.batch = batch; this.almaconnectUrl = "ac/$id" }

    private val google = company(1, "Google", "36.3")
    private val tcs = company(2, "TCS", null)
    private val rows = listOf(
        alumnus(1, "A", tcs, "Ahmedabad", 2015),
        alumnus(2, "B", google, "Gandhinagar", 2019),
        alumnus(3, "C", google, "Bangalore", 2018),
        alumnus(4, "D", tcs, "Surat", 2020)
    )

    private inner class FakeRepo : AlumniRepositoryAppAction(Mockito.mock(AlumniRepository::class.java), Mockito.mock(AlumniCompanyRepository::class.java)) {
        val citiesQueried = mutableListOf<String?>()
        override fun search(query: String?, companyId: Long?, batch: Int?, course: String?, city: String?, linkedinOnly: Boolean, page: Int, size: Int): Page<DMAlumni> {
            citiesQueried += city
            val hits = rows.filter { (city == null || it.city.equals(city, true)) && (companyId == null || it.company.id == companyId) }
            return PageImpl(hits.take(size), org.springframework.data.domain.PageRequest.of(page, size), hits.size.toLong())
        }
        override fun searchCompanies(query: String?, sort: String, page: Int, size: Int): Page<DMAlumniCompany> {
            val hits = listOf(google, tcs).filter { query == null || it.name.contains(query, true) }
            return PageImpl(hits)
        }
    }

    @Test
    fun `a region expands to its cities and results are merged best-paying first`() {
        val repo = FakeRepo()
        val result = AgentAlumniQueryAppAction(repo).search(null, null, null, null, "Gujarat", false, 0, 15)

        assertTrue(repo.citiesQueried.containsAll(listOf("Ahmedabad", "Gandhinagar", "Surat")))
        assertEquals(listOf("B", "D", "A"), result.items.map { it.name }) // Google first, then TCS by newest batch
        assertEquals("Google", result.items.first().company)
        assertEquals(3, result.totalCount)
    }

    @Test
    fun `results are paged and the note tells the model to stop`() {
        val action = AgentAlumniQueryAppAction(FakeRepo())
        val first = action.search(null, null, null, null, "Gujarat", false, 0, 2)
        assertEquals(listOf("B", "D"), first.items.map { it.name })
        assertTrue(first.note!!.contains("1–2 of 3") && first.note!!.contains("page=1"), first.note)
        val second = action.search(null, null, null, null, "Gujarat", false, 1, 2)
        assertEquals(listOf("A"), second.items.map { it.name })
        assertTrue(second.note!!.contains("no more"), second.note)
    }

    @Test
    fun `company is resolved by name and an unknown one is reported not guessed`() {
        val repo = FakeRepo()
        val action = AgentAlumniQueryAppAction(repo)

        assertEquals(listOf("B", "C"), action.search(null, "goog", null, null, null, false, 0, 15).items.map { it.name })
        val unknown = action.search(null, "Nowhere Inc", null, null, null, false, 0, 15)
        assertTrue(unknown.items.isEmpty())
        assertTrue(unknown.note!!.contains("Nowhere Inc"))
    }
}
