package com.attendanceio.api.model.attendance

import com.attendanceio.api.model.BaseEntity
import com.attendanceio.api.model.student.DMStudent
import com.attendanceio.api.model.subject.DMSubject
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.LocalDate

@Entity
@Table(name = "institute_attendance")
class DMInstituteAttendance : BaseEntity() {
    @ManyToOne
    @JoinColumn(name = "student_id", nullable = false)
    var student: DMStudent? = null

    @ManyToOne
    @JoinColumn(name = "subject_id", nullable = false)
    var subject: DMSubject? = null

    @Column(name = "cutoff_date", nullable = false)
    var cutoffDate: LocalDate? = null

    @Column(name = "total_classes", nullable = false)
    var totalClasses: Int = 0

    @Column(name = "present_classes", nullable = false)
    var presentClasses: Int = 0

    @Column(name = "absent_classes", nullable = false)
    var absentClasses: Int = 0

}

