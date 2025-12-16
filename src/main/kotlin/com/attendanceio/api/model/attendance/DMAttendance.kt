package com.attendanceio.api.model.attendance

import com.attendanceio.api.model.BaseEntity
import com.attendanceio.api.model.student.DMStudent
import com.attendanceio.api.model.subject.DMSubject
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Enumerated
import jakarta.persistence.EnumType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
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
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", nullable = false, columnDefinition = "attendance_status_enum")
    var status: AttendanceStatus = AttendanceStatus.PRESENT

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "source_id", nullable = true, columnDefinition = "attendance_source_enum")
    var sourceId: AttendanceSource? = null
}
