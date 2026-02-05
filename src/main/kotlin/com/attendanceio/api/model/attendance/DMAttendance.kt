package com.attendanceio.api.model.attendance

import com.attendanceio.api.model.BaseEntity
import com.attendanceio.api.model.student.DMStudent
import com.attendanceio.api.model.subject.DMSubject
import com.attendanceio.api.model.timetable.DMTimeSlot
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Enumerated
import jakarta.persistence.EnumType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.LocalDate
import java.time.LocalTime

@Entity
@Table(
    name = "attendance",
    uniqueConstraints = [
        // Unique constraint that includes time information
        // Allows multiple records for same student/subject/date when time differs
        // NULL values are treated as distinct, allowing backward compatibility
        // Note: For extra classes (is_extra_class=true), multiple records are allowed
        // by having all time fields as NULL, which PostgreSQL treats as distinct
        UniqueConstraint(
            name = "attendance_unique_time_specific",
            columnNames = ["student_id", "subject_id", "lecture_date", "time_slot_id", "custom_start_time", "custom_end_time"]
        )
    ]
)
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

    // Time slot information (for distinguishing multiple lectures of same subject on same day)
    // If null, attendance applies to all lectures of that subject on that day (backward compatibility)
    @ManyToOne
    @JoinColumn(name = "time_slot_id", nullable = true)
    var timeSlot: DMTimeSlot? = null

    // Custom time fields (for custom time slots)
    @Column(name = "custom_start_time", nullable = true)
    var customStartTime: LocalTime? = null

    @Column(name = "custom_end_time", nullable = true)
    var customEndTime: LocalTime? = null

    // Flag to identify extra classes added by user (classes not in timetable)
    @Column(name = "is_extra_class", nullable = false)
    var isExtraClass: Boolean = false

    /**
     * When true, this row is excluded from admin and app analytics (e.g. bulk-inserted holiday
     * cancellations). Null/false = included in analytics. User-facing attendance views still show it.
     */
    @Column(name = "exclude_from_analytics", nullable = true)
    var excludeFromAnalytics: Boolean? = null
}
