package com.attendanceio.api.model.alumni

import com.attendanceio.api.model.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

@Entity
@Table(
    name = "alumni",
    indexes = [
        Index(name = "idx_alumni_company", columnList = "company_id"),
        Index(name = "idx_alumni_batch", columnList = "batch"),
        Index(name = "idx_alumni_name", columnList = "name")
    ]
)
class DMAlumni : BaseEntity() {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    var company: DMAlumniCompany = DMAlumniCompany()

    @Column(name = "name", nullable = false, length = 100)
    var name: String = ""

    /** Free-text line from the profile, e.g. "SDE II at Atlassian". */
    @Column(name = "headline", length = 255)
    var headline: String? = null

    @Column(name = "title", length = 150)
    var title: String? = null

    @Column(name = "city", length = 100)
    var city: String? = null

    /** Graduation year. */
    @Column(name = "batch")
    var batch: Int? = null

    /** Normalised degree: B.Tech., M.Tech., M.Sc., M.Des., M.S., B.E., Ph.D. */
    @Column(name = "course", length = 50)
    var course: String? = null

    @Column(name = "linkedin_url", length = 255)
    var linkedinUrl: String? = null

    /** The only stable identifier in the source data; the sync upserts on it. */
    @Column(name = "almaconnect_url", nullable = false, unique = true, length = 255)
    var almaconnectUrl: String = ""
}
