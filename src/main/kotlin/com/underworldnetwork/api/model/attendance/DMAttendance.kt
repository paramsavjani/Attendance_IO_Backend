package com.underworldnetwork.api.model.attendance

import com.underworldnetwork.api.model.BaseEntity
import com.underworldnetwork.api.model.student.DMStudent
import com.underworldnetwork.api.model.subject.DMSubject
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Enumerated
import jakarta.persistence.EnumType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.LocalDate

@Entity
@Table(name = "attendance")
class DMAttendance : BaseEntity() {
    @ManyToOne
    @JoinColumn(name = "student_id", nullable = false)
    var student: DMStudent? = null

    @ManyToOne
    @JoinColumn(name = "subject_id", nullable = false)
    var subject: DMSubject? = null

    @Column(name = "lecture_date", nullable = false)
    var lectureDate: LocalDate? = null

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    var status: AttendanceStatus = AttendanceStatus.PRESENT

    @Enumerated(EnumType.STRING)
    @Column(name = "source_id", columnDefinition = "TEXT")
    var sourceId: AttendanceSource? = null
}
