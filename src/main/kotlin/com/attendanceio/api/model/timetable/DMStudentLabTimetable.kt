package com.attendanceio.api.model.timetable

import com.attendanceio.api.model.BaseEntity
import com.attendanceio.api.model.semester.DMSemester
import com.attendanceio.api.model.student.DMStudent
import com.attendanceio.api.model.subject.DMSubject
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.LocalTime

@Entity
@Table(
    name = "student_lab_timetable"
)
class DMStudentLabTimetable : BaseEntity() {
    @ManyToOne
    @JoinColumn(name = "student_id", nullable = false)
    var student: DMStudent? = null

    @ManyToOne
    @JoinColumn(name = "semester_id", nullable = false)
    var semester: DMSemester? = null

    @ManyToOne
    @JoinColumn(name = "subject_id", nullable = false)
    var subject: DMSubject? = null

    @ManyToOne
    @JoinColumn(name = "day_id", nullable = false)
    var day: DMWeekDay? = null

    // Slot reference (for backward compatibility with fixed time slots)
    // If null, custom times must be provided
    @ManyToOne
    @JoinColumn(name = "slot_id", nullable = true)
    var slot: DMTimeSlot? = null

    // Custom time fields (for flexible scheduling)
    // If provided, slot can be null
    @Column(name = "custom_start_time", nullable = true)
    var customStartTime: LocalTime? = null

    @Column(name = "custom_end_time", nullable = true)
    var customEndTime: LocalTime? = null

    // Location field for lab/tutorial classes
    @Column(name = "location", nullable = true, length = 255)
    var location: String? = null
}
