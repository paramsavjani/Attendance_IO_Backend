package com.attendanceio.api.model.schedule

import com.attendanceio.api.model.subject.DMSubject
import com.attendanceio.api.model.timetable.DMTimeSlot
import com.attendanceio.api.model.timetable.DMWeekDay
import jakarta.persistence.*

/**
 * Represents the default institute-defined weekly schedule for each subject.
 * This is used to auto-populate a student's timetable when they enroll in subjects.
 */
@Entity
@Table(
    name = "subject_schedule",
    uniqueConstraints = [
        UniqueConstraint(
            columnNames = ["subject_id", "day_id", "slot_id"]
        )
    ]
)
class DMSubjectSchedule {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    var id: Long? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subject_id", nullable = false)
    var subject: DMSubject? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "day_id", nullable = false)
    var day: DMWeekDay? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "slot_id", nullable = false)
    var slot: DMTimeSlot? = null
}

