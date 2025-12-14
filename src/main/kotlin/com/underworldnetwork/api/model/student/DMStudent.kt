package com.underworldnetwork.api.model.student

import com.underworldnetwork.api.model.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table

@Entity
@Table(name = "student")
class DMStudent : BaseEntity() {
    @Column(name = "name")
    var name: String? = null
    @Column(name = "sid", nullable = false)
    var sid: String = ""
    @Column(name = "phone")
    var phone: String? = null
}
