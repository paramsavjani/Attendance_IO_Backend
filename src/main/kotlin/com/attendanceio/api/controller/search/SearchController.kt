package com.attendanceio.api.controller.search

import com.attendanceio.api.application.search.actions.GetStudentAttendanceAppAction
import com.attendanceio.api.application.search.actions.SearchStudentsAppAction
import com.attendanceio.api.model.search.StudentAttendanceResponse
import com.attendanceio.api.model.search.StudentSearchResponse
import com.attendanceio.api.application.placement.PlacementAccess
import com.attendanceio.api.util.DemoUserUtil
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.core.user.OAuth2User
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
    fun searchStudents(
        @RequestParam query: String,
        @RequestParam(required = false, defaultValue = "0") offset: Int,
        @AuthenticationPrincipal user: OAuth2User?
    ): ResponseEntity<List<StudentSearchResponse>> {
        // Placement results name students, so they go only to signed-in DAU students (see PlacementAccess)
        val withPlacements = user != null &&
            PlacementAccess.allowed(user.getAttribute<String>("email"), DemoUserUtil.isDemoUser(user))
        val results = searchStudentsAppAction.execute(query, offset, withPlacements)
        return ResponseEntity.ok(results)
    }

    @GetMapping("/student/{studentId}/attendance")
    fun getStudentAttendance(
        @PathVariable studentId: Long,
        @RequestParam(required = false) source: String?
    ): ResponseEntity<StudentAttendanceResponse> {
        return try {
            val result = getStudentAttendanceAppAction.execute(studentId, source)
            ResponseEntity.ok(result)
        } catch (e: IllegalArgumentException) {
            ResponseEntity.status(404).build()
        } catch (e: Exception) {
            ResponseEntity.status(500).build()
        }
    }
}
