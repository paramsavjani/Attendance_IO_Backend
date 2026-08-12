package com.attendanceio.api.controller.timetable

import com.attendanceio.api.application.timetable.actions.GetLectureTimeSlotsAppAction
import com.attendanceio.api.model.timetable.TimeSlotResponse
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/time-slots")
class TimeSlotController(
    private val getLectureTimeSlotsAppAction: GetLectureTimeSlotsAppAction
) {
    @GetMapping
    fun getLectureTimeSlots(): ResponseEntity<List<TimeSlotResponse>> {
        return ResponseEntity.ok(getLectureTimeSlotsAppAction.execute())
    }
}
