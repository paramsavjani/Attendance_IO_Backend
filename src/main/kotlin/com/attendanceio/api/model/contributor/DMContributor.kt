package com.attendanceio.api.model.contributor

import com.attendanceio.api.model.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Enumerated
import jakarta.persistence.EnumType
import jakarta.persistence.Table

@Entity
@Table(name = "contributor")
class DMContributor : BaseEntity() {
    @Column(name = "name", nullable = false)
    var name: String = ""

    @Enumerated(EnumType.STRING)
    @Column(name = "type_of_help", nullable = false)
    var typeOfHelp: ContributorType = ContributorType.IDEA
}

enum class ContributorType {
    IDEA,
    TESTER
}

