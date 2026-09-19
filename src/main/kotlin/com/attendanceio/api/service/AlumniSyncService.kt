package com.attendanceio.api.service

import com.attendanceio.api.model.alumni.AlumniJson
import com.attendanceio.api.model.alumni.DMAlumni
import com.attendanceio.api.model.alumni.DMAlumniCompany
import com.attendanceio.api.repository.alumni.AlumniRepositoryAppAction
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.cache.CacheManager
import org.springframework.context.event.EventListener
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate
import tools.jackson.databind.ObjectMapper

/**
 * Optional one-off loader: if `data/alumni.json` is on the classpath (it is deliberately NOT in
 * the repo — the alumni export is private and lives only in the database), it is diffed into
 * `alumni_company` / `alumni` on startup: companies matched by name, alumni by AlmaConnect URL,
 * only new or changed rows written. Without the file this is a no-op.
 */
@Component
class AlumniSyncService(
    private val alumniRepositoryAppAction: AlumniRepositoryAppAction,
    private val mapper: ObjectMapper,
    private val transactionTemplate: TransactionTemplate,
    private val cacheManager: CacheManager
) {
    private val log = LoggerFactory.getLogger(AlumniSyncService::class.java)

    @EventListener(ApplicationReadyEvent::class)
    fun onApplicationReady() {
        try {
            transactionTemplate.execute { syncFromJsonFile() }
            cacheManager.getCache("alumniFilters")?.clear()
        } catch (e: Exception) {
            log.error("Failed to sync alumni data", e)
        }
    }

    fun syncFromJsonFile() {
        val resource = ClassPathResource("data/alumni.json")
        if (!resource.exists()) {
            log.debug("No data/alumni.json on the classpath; alumni come from the database only")
            return
        }
        val data: AlumniJson = resource.inputStream.use { mapper.readValue(it, AlumniJson::class.java) }
        if (data.companies.isEmpty()) return

        // Companies: match on name (case-insensitive), refresh salary and count.
        val companiesByName = alumniRepositoryAppAction.allCompanies().associateBy { it.name.lowercase() }.toMutableMap()
        val changedCompanies = mutableListOf<DMAlumniCompany>()
        for (json in data.companies) {
            val name = json.name.trim()
            if (name.isEmpty()) continue
            val existing = companiesByName[name.lowercase()]
            val avgLpa = json.avgLpa?.takeIf { it.signum() > 0 }
            val count = json.alumni.size
            if (existing == null) {
                val created = DMAlumniCompany().apply { this.name = name; this.avgLpa = avgLpa; this.alumniCount = count }
                companiesByName[name.lowercase()] = created
                changedCompanies += created
            } else if (!sameLpa(existing.avgLpa, avgLpa) || existing.alumniCount != count) {
                existing.avgLpa = avgLpa
                existing.alumniCount = count
                changedCompanies += existing
            }
        }
        if (changedCompanies.isNotEmpty()) alumniRepositoryAppAction.saveCompanies(changedCompanies)

        // Alumni: match on AlmaConnect URL, the one stable id in the export.
        val alumniByUrl = alumniRepositoryAppAction.allAlumni().associateBy { it.almaconnectUrl }
        val changedAlumni = mutableListOf<DMAlumni>()
        for (companyJson in data.companies) {
            val company = companiesByName[companyJson.name.trim().lowercase()] ?: continue
            for (json in companyJson.alumni) {
                val url = json.almaconnectUrl.trim()
                if (url.isEmpty() || json.name.isBlank()) continue
                val row = alumniByUrl[url] ?: DMAlumni().apply { almaconnectUrl = url }
                val before = snapshot(row)
                row.company = company
                row.name = json.name.trim()
                row.headline = json.headline?.trim()?.takeIf { it.isNotEmpty() }
                row.title = json.title?.trim()?.takeIf { it.isNotEmpty() }
                row.city = json.city?.trim()?.takeIf { it.isNotEmpty() }
                row.batch = json.batch
                row.course = json.course?.trim()?.takeIf { it.isNotEmpty() }
                row.linkedinUrl = json.linkedinUrl?.trim()?.takeIf { it.isNotEmpty() }
                if (row.id == null || snapshot(row) != before) changedAlumni += row
            }
        }
        if (changedAlumni.isNotEmpty()) alumniRepositoryAppAction.saveAlumni(changedAlumni)

        log.info(
            "Alumni sync: {} companies / {} alumni in file; wrote {} companies, {} alumni",
            data.companies.size, data.companies.sumOf { it.alumni.size }, changedCompanies.size, changedAlumni.size
        )
    }

    private fun sameLpa(a: java.math.BigDecimal?, b: java.math.BigDecimal?) =
        if (a == null || b == null) a == null && b == null else a.compareTo(b) == 0

    /** Field tuple used to detect whether an existing row actually changed. */
    private fun snapshot(a: DMAlumni) = listOf(
        a.company.name, a.name, a.headline, a.title, a.city, a.batch, a.course, a.linkedinUrl
    )
}
