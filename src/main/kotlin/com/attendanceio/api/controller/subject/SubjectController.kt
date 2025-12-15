package com.attendanceio.api.controller.subject

import com.attendanceio.api.application.subject.actions.GetSubjectsForCurrentSemesterAppAction
import com.attendanceio.api.model.subject.SubjectResponse
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/subjects")
class SubjectController(
    private val getSubjectsForCurrentSemesterAppAction: GetSubjectsForCurrentSemesterAppAction
) {
    @GetMapping("/current")
    fun getSubjectsForCurrentSemester(): ResponseEntity<List<SubjectResponse>> {
        val subjects = getSubjectsForCurrentSemesterAppAction.execute()
        return ResponseEntity.ok(subjects)
    }
}

