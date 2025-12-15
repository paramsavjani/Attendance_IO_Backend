package com.attendanceio.api.model.subject

import com.attendanceio.api.model.BaseEntity
import com.attendanceio.api.model.semester.DMSemester
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

    @Column(name = "lecture_place", nullable = true, length = 50)
    var lecturePlace: String? = null

    @Column(name = "color", nullable = false, length = 7)
    var color: String = "#3B82F6"

    @ManyToOne
    @JoinColumn(name = "semester_id", nullable = false)
    var semester: DMSemester? = null
}

