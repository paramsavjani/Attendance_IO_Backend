package com.attendanceio.api.controller.semester

import com.attendanceio.api.repository.semester.SemesterRepositoryAppAction
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/semester")
class SemesterController(
    private val semesterRepositoryAppAction: SemesterRepositoryAppAction
) {
    @GetMapping("/current")
    fun getCurrentSemester(): ResponseEntity<Map<String, Any>> {
        val activeSemesters = semesterRepositoryAppAction.findByIsActive(true)
        
        return if (activeSemesters.isNotEmpty()) {
            val currentSemester = activeSemesters.first()
            ResponseEntity.ok(
                mapOf(
                    "year" to currentSemester.year,
                    "type" to currentSemester.type.name
                )
            )
        } else {
            ResponseEntity.status(404).body(
                mapOf("error" to "No active semester found")
            )
        }
    }
}

