package com.attendanceio.api.application.timetable.actions

import com.attendanceio.api.model.timetable.TimeSlotResponse
import com.attendanceio.api.repository.timetable.TimeSlotRepository
import org.springframework.stereotype.Component
import java.time.Duration

/**
 * Returns the standard lecture time slots (short, fixed-length 50-minute periods
 * used for regular classes) as opposed to the longer lab/tutorial block slots
 * (60 minutes and up) that share the same time_slots table.
 *
 * The 55-minute cut-off keeps every 50-minute lecture period (morning 08:00-12:50
 * and afternoon 14:00-17:50) while excluding the 60/120/180/240-minute lab and
 * tutorial blocks, including the exactly-60-minute ones (e.g. 14:00-15:00).
 */
@Component
class GetLectureTimeSlotsAppAction(
    private val timeSlotRepository: TimeSlotRepository
) {
    private val maxLectureDuration = Duration.ofMinutes(55)

    fun execute(): List<TimeSlotResponse> {
        return timeSlotRepository.findAll()
            .filter { slot ->
                val start = slot.startTime
                val end = slot.endTime
                start != null && end != null && Duration.between(start, end) <= maxLectureDuration
            }
            .sortedBy { it.startTime }
            .map { slot ->
                TimeSlotResponse(
                    index = (slot.id?.toInt() ?: 0) - 1,
                    startTime = slot.startTime.toString(),
                    endTime = slot.endTime.toString()
                )
            }
    }
}
