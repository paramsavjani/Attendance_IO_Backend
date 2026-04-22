package com.attendanceio.api.model.search

import com.attendanceio.api.model.BaseEntity
import com.attendanceio.api.model.student.DMStudent
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

@Entity
@Table(name = "search_history")
class DMSearchHistory : BaseEntity() {

    /** The student who performed the search */
    @ManyToOne
    @JoinColumn(name = "student_id", nullable = false)
    var student: DMStudent? = null

    /** The student profile that was viewed */
    @ManyToOne
    @JoinColumn(name = "viewed_student_id", nullable = false)
    var viewedStudent: DMStudent? = null

    @Column(name = "is_deleted", nullable = false)
    var isDeleted: Boolean = false
}
