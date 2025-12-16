package com.attendanceio.api.model.timetable

import com.attendanceio.api.model.BaseEntity
import com.attendanceio.api.model.semester.DMSemester
import com.attendanceio.api.model.student.DMStudent
import com.attendanceio.api.model.subject.DMSubject
import jakarta.persistence.Entity
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

@Entity
@Table(
    name = "student_timetable",
    uniqueConstraints = [
        UniqueConstraint(
            columnNames = ["student_id", "semester_id", "day_id", "slot_id"]
        )
    ]
)
class DMStudentTimetable : BaseEntity() {
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

    @ManyToOne
    @JoinColumn(name = "slot_id", nullable = false)
    var slot: DMTimeSlot? = null
}

