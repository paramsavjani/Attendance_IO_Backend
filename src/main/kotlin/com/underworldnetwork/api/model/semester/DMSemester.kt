package com.underworldnetwork.api.model.semester

import com.underworldnetwork.api.model.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Enumerated
import jakarta.persistence.EnumType
import jakarta.persistence.Table

@Entity
@Table(name = "semesters")
class DMSemester : BaseEntity() {
    @Column(name = "year", nullable = false)
    var year: Int = 0

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    var type: SemesterType = SemesterType.SUMMER

    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = false
}
