package com.attendanceio.api.model.student

import com.attendanceio.api.model.BaseEntity
import com.attendanceio.api.model.subject.DMSubject
import jakarta.persistence.Entity
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

@Entity
@Table(name = "student_subject")
class DMStudentSubject : BaseEntity() {
    @ManyToOne
    @JoinColumn(name = "student_id", nullable = false)
    var student: DMStudent? = null

    @ManyToOne
    @JoinColumn(name = "subject_id", nullable = false)
    var subject: DMSubject? = null
}

