package com.attendanceio.api.controller.search

import com.attendanceio.api.application.search.actions.GetStudentAttendanceAppAction
import com.attendanceio.api.application.search.actions.SearchStudentsAppAction
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/search")
class SearchController(
    private val searchStudentsAppAction: SearchStudentsAppAction,
    private val getStudentAttendanceAppAction: GetStudentAttendanceAppAction
) {
    @GetMapping("/students")
    fun searchStudents(@RequestParam query: String): ResponseEntity<List<Map<String, Any>>> {
        val results = searchStudentsAppAction.execute(query)
        return ResponseEntity.ok(results)
    }

    @GetMapping("/student/{studentId}/attendance")
    fun getStudentAttendance(
        @PathVariable studentId: Long
    ): ResponseEntity<Map<String, Any>> {
        return try {
            val result = getStudentAttendanceAppAction.execute(studentId)
            ResponseEntity.ok(result)
        } catch (e: IllegalArgumentException) {
            ResponseEntity.status(404).body(mapOf("error" to (e.message ?: "Resource not found")))
        } catch (e: Exception) {
            ResponseEntity.status(500).body(mapOf("error" to "Internal server error"))
        }
    }
}
