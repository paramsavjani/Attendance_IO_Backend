package com.attendanceio.api.model.alumni

import com.attendanceio.api.model.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.math.BigDecimal

/** An employer of at least one alumnus. Salary is per company, not per person, so it lives here. */
@Entity
@Table(name = "alumni_company")
class DMAlumniCompany : BaseEntity() {
    @Column(name = "name", nullable = false, unique = true, length = 200)
    var name: String = ""

    /** Average package in LPA as reported by AlmaConnect; null when the salary is not known. */
    @Column(name = "avg_lpa", precision = 6, scale = 1)
    var avgLpa: BigDecimal? = null

    /** Denormalised for sorting company lists without a join; refreshed by the sync. */
    @Column(name = "alumni_count", nullable = false)
    var alumniCount: Int = 0
}
