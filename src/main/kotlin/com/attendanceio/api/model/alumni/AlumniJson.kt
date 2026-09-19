package com.attendanceio.api.model.alumni

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.math.BigDecimal

/** Shape of `resources/data/alumni.json`, grouped by company like the AlmaConnect export. */
@JsonIgnoreProperties(ignoreUnknown = true)
data class AlumniJson(val companies: List<AlumniCompanyJson> = emptyList())

@JsonIgnoreProperties(ignoreUnknown = true)
data class AlumniCompanyJson(
    val name: String,
    val avgLpa: BigDecimal? = null,
    val alumni: List<AlumniRecordJson> = emptyList()
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class AlumniRecordJson(
    val name: String,
    val headline: String? = null,
    val title: String? = null,
    val city: String? = null,
    val batch: Int? = null,
    val course: String? = null,
    val linkedinUrl: String? = null,
    val almaconnectUrl: String
)
