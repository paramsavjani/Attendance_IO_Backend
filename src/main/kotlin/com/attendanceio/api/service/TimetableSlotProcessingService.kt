package com.attendanceio.api.service

import com.attendanceio.api.model.semester.DMSemester
import com.attendanceio.api.model.subject.DMSubject
import com.attendanceio.api.model.timetable.DMTimeSlot
import com.attendanceio.api.model.timetable.DMWeekDay
import com.attendanceio.api.model.timetable.TimetableSlotRequest
import com.attendanceio.api.repository.semester.SemesterRepositoryAppAction
import com.attendanceio.api.repository.subject.SubjectRepositoryAppAction
import com.attendanceio.api.repository.timetable.TimeSlotRepository
import com.attendanceio.api.repository.timetable.WeekDayRepository
import org.springframework.stereotype.Service
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Service
class TimetableSlotProcessingService(
    private val semesterRepositoryAppAction: SemesterRepositoryAppAction,
    private val subjectRepositoryAppAction: SubjectRepositoryAppAction,
    private val weekDayRepository: WeekDayRepository,
    private val timeSlotRepository: TimeSlotRepository
) {
    data class ResolvedSlot(
        val semester: DMSemester,
        val weekDay: DMWeekDay,
        val subject: DMSubject,
        val customStartTime: LocalTime?,
        val customEndTime: LocalTime?,
        val timeSlot: DMTimeSlot?,
        val location: String? = null
    )

    fun getActiveSemester(): DMSemester {
        return semesterRepositoryAppAction.findByIsActive(true).firstOrNull()
            ?: throw IllegalArgumentException("No active semester found")
    }

    fun resolveSlots(slots: List<TimetableSlotRequest>): List<ResolvedSlot> {
        val semester = getActiveSemester()
        val weekDays = weekDayRepository.findAll().associateBy { it.id }
        val timeSlots = timeSlotRepository.findAll().associateBy { it.id }
        val formatter = DateTimeFormatter.ofPattern("HH:mm")

        return slots
            .filter { it.subjectId != null }
            .mapNotNull { slot ->
                val dayId = (slot.day + 1).toShort()
                val weekDay = weekDays[dayId]
                    ?: throw IllegalArgumentException("Week day not found for day: ${slot.day}")

                val subjectId = slot.subjectId?.toLongOrNull() ?: return@mapNotNull null
                val subject = subjectRepositoryAppAction.findById(subjectId)
                    ?: throw IllegalArgumentException("Subject not found: ${slot.subjectId}")

                val hasCustomTimes = slot.startTime != null && slot.endTime != null
                val hasStandardSlot = slot.timeSlot != null

                if (!hasCustomTimes && !hasStandardSlot)
                    throw IllegalArgumentException("Either timeSlot or startTime/endTime must be provided")
                if (hasCustomTimes && hasStandardSlot)
                    throw IllegalArgumentException("Cannot provide both timeSlot and custom times")

                val location = slot.location?.uppercase()?.trim()?.takeIf { it.isNotBlank() }

                if (hasCustomTimes) {
                    val startTime = try { LocalTime.parse(slot.startTime, formatter) }
                    catch (e: Exception) { throw IllegalArgumentException("Invalid time format. Use HH:mm format (e.g., 14:30)") }
                    val endTime = try { LocalTime.parse(slot.endTime, formatter) }
                    catch (e: Exception) { throw IllegalArgumentException("Invalid time format. Use HH:mm format (e.g., 14:30)") }
                    ResolvedSlot(semester, weekDay, subject, startTime, endTime, null, location)
                } else {
                    val slotId = (slot.timeSlot!! + 1).toShort()
                    val timeSlot = timeSlots[slotId]
                        ?: throw IllegalArgumentException("Time slot not found for timeSlot: ${slot.timeSlot}")
                    ResolvedSlot(semester, weekDay, subject, null, null, timeSlot, location)
                }
            }
    }
}
