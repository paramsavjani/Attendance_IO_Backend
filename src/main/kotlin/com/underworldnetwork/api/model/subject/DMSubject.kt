package com.underworldnetwork.api.model.subject

import com.underworldnetwork.api.model.BaseEntity
import com.underworldnetwork.api.model.semester.DMSemester
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

@Entity
@Table(name = "subjects")
class DMSubject : BaseEntity() {
    @Column(name = "code", nullable = false, length = 20)
    var code: String = ""

    @Column(name = "name", nullable = false, length = 100)
    var name: String = ""

    @ManyToOne
    @JoinColumn(name = "semester_id", nullable = false)
    var semester: DMSemester? = null
}

