package com.attendanceio.api.service

import com.attendanceio.api.model.timetable.DMStudentTimetable
import com.attendanceio.api.repository.attendance.AttendanceRepositoryAppAction
import org.springframework.stereotype.Component
import java.time.LocalDate

/**
 * Checks whether an attendance record already exists for a given timetable entry.
 * Handles time-slot-based, custom-time-based, and backward-compatible general records.
 */
@Component
class AttendanceExistenceChecker(
    private val attendanceRepositoryAppAction: AttendanceRepositoryAppAction
) {
    fun hasAttendance(
        studentId: Long,
        subjectId: Long,
        date: LocalDate,
        timetableEntry: DMStudentTimetable
    ): Boolean {
        val bySlot = timetableEntry.slot != null && timetableEntry.slot!!.id != null
        val byCustom = timetableEntry.customStartTime != null && timetableEntry.customEndTime != null

        if (bySlot) {
            val slotId = timetableEntry.slot!!.id!!
            if (attendanceRepositoryAppAction.findByStudentIdAndSubjectIdAndLectureDateAndTimeSlotId(
                    studentId, subjectId, date, slotId
                ) != null
            ) return true
        }

        if (byCustom) {
            if (attendanceRepositoryAppAction.findByStudentIdAndSubjectIdAndLectureDateAndCustomStartTimeAndCustomEndTime(
                    studentId, subjectId, date,
                    timetableEntry.customStartTime!!,
                    timetableEntry.customEndTime!!
                ) != null
            ) return true
        }

        // Fallback: general record with no time info (backward compatibility)
        val general = attendanceRepositoryAppAction.findByStudentIdAndSubjectIdAndLectureDate(studentId, subjectId, date)
        return general?.let {
            it.timeSlot == null && it.customStartTime == null && it.customEndTime == null
        } ?: false
    }
}
