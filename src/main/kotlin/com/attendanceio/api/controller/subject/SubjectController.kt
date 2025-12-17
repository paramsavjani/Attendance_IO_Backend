package com.attendanceio.api.controller.subject

import com.attendanceio.api.application.subject.actions.GetSubjectSchedulesAppAction
import com.attendanceio.api.application.subject.actions.GetSubjectsForCurrentSemesterAppAction
import com.attendanceio.api.model.schedule.SubjectScheduleResponse
import com.attendanceio.api.model.subject.SubjectResponse
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/subjects")
class SubjectController(
    private val getSubjectsForCurrentSemesterAppAction: GetSubjectsForCurrentSemesterAppAction,
    private val getSubjectSchedulesAppAction: GetSubjectSchedulesAppAction
) {
    @GetMapping("/current")
    fun getSubjectsForCurrentSemester(): ResponseEntity<List<SubjectResponse>> {
        val subjects = getSubjectsForCurrentSemesterAppAction.execute()
        return ResponseEntity.ok(subjects)
    }
    
    @GetMapping("/schedules")
    fun getSubjectSchedules(
        @RequestParam("subjectIds") subjectIds: List<Long>
    ): ResponseEntity<List<SubjectScheduleResponse>> {
        val schedules = getSubjectSchedulesAppAction.execute(subjectIds)
        return ResponseEntity.ok(schedules)
    }
}

