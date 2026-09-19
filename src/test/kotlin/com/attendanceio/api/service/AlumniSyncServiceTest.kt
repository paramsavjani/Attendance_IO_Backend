package com.attendanceio.api.service

import com.attendanceio.api.model.alumni.AlumniJson
import com.attendanceio.api.model.alumni.DMAlumni
import com.attendanceio.api.model.alumni.DMAlumniCompany
import com.attendanceio.api.repository.alumni.AlumniCompanyRepository
import com.attendanceio.api.repository.alumni.AlumniRepository
import com.attendanceio.api.repository.alumni.AlumniRepositoryAppAction
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.cache.concurrent.ConcurrentMapCacheManager
import org.springframework.core.io.ClassPathResource
import org.springframework.transaction.support.TransactionTemplate
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AlumniSyncServiceTest {
    private val mapper = JsonMapper.builder().addModule(KotlinModule.Builder().build()).build()

    /** In-memory stand-in for the repository layer: remembers what the sync wrote and hands it back next time. */
    private class FakeRepo : AlumniRepositoryAppAction(
        Mockito.mock(AlumniRepository::class.java),
        Mockito.mock(AlumniCompanyRepository::class.java)
    ) {
        val companies = mutableListOf<DMAlumniCompany>()
        val alumni = mutableListOf<DMAlumni>()
        var companyWrites = 0
        var alumniWrites = 0

        override fun allCompanies(): List<DMAlumniCompany> = companies.toList()
        override fun allAlumni(): List<DMAlumni> = alumni.toList()
        override fun saveCompanies(companies: Collection<DMAlumniCompany>): List<DMAlumniCompany> {
            companyWrites += companies.size
            companies.forEach { c -> if (c.id == null) { c.id = this.companies.size + 1L; this.companies += c } }
            return companies.toList()
        }
        override fun saveAlumni(alumni: Collection<DMAlumni>): List<DMAlumni> {
            alumniWrites += alumni.size
            alumni.forEach { a -> if (a.id == null) { a.id = this.alumni.size + 1L; this.alumni += a } }
            return alumni.toList()
        }
    }

    private fun service(repo: FakeRepo) =
        AlumniSyncService(repo, mapper, Mockito.mock(TransactionTemplate::class.java), ConcurrentMapCacheManager())

    /** The real export is NOT in the repo (it is private); tests run on the small fixture in src/test/resources. */
    @Test
    fun `alumni json fixture parses and is well formed`() {
        val data = ClassPathResource("data/alumni.json").inputStream.use { mapper.readValue(it, AlumniJson::class.java) }
        val alumni = data.companies.flatMap { it.alumni }
        assertEquals(3, data.companies.size)
        assertEquals(5, alumni.size)
        assertEquals(alumni.size, alumni.map { it.almaconnectUrl }.toSet().size, "almaconnect url must be unique")
        assertTrue(alumni.all { it.name.isNotBlank() && it.almaconnectUrl.startsWith("https://") })
        assertTrue(data.companies.all { it.avgLpa == null || it.avgLpa!! > BigDecimal.ZERO })
    }

    @Test
    fun `sync inserts everything into an empty database`() {
        val repo = FakeRepo()
        service(repo).syncFromJsonFile()

        assertEquals(3, repo.companies.size)
        assertEquals(5, repo.alumni.size)
        val wayfair = repo.companies.first { it.name == "Wayfair" }
        assertEquals(0, wayfair.avgLpa!!.compareTo(BigDecimal("48.9")))
        assertEquals(3, wayfair.alumniCount)
        val ravi = repo.alumni.first { it.name == "Ravi Kishore Pillalamarri" }
        assertNull(ravi.linkedinUrl, "blank linkedin becomes null")
        assertEquals(2008, ravi.batch)
        assertEquals("Wayfair", ravi.company.name)
        assertTrue(repo.alumni.any { it.linkedinUrl != null })
    }

    @Test
    fun `second sync against a matching database writes nothing`() {
        val repo = FakeRepo()
        service(repo).syncFromJsonFile()
        repo.companyWrites = 0
        repo.alumniWrites = 0

        service(repo).syncFromJsonFile()

        assertEquals(0, repo.companyWrites)
        assertEquals(0, repo.alumniWrites)
    }

    @Test
    fun `changed rows are rewritten, unchanged ones are not`() {
        val repo = FakeRepo()
        service(repo).syncFromJsonFile()
        repo.alumni.first { it.name == "Anjali Shastri" }.city = "Somewhere else"
        repo.companies.first { it.name == "Atlassian" }.avgLpa = null
        repo.companyWrites = 0
        repo.alumniWrites = 0

        service(repo).syncFromJsonFile()

        assertEquals(1, repo.companyWrites)
        assertEquals(1, repo.alumniWrites)
        assertEquals("Austin", repo.alumni.first { it.name == "Anjali Shastri" }.city)
    }
}
