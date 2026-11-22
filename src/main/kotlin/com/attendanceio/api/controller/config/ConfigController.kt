package com.attendanceio.api.controller.config

import com.attendanceio.api.repository.student.StudentRepositoryAppAction
import com.attendanceio.api.service.ClassCalculationService
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/config")
class ConfigController(
    private val classCalculationService: ClassCalculationService,
    private val studentRepositoryAppAction: StudentRepositoryAppAction
) {
    @GetMapping("/classes-start-date")
    fun getClassesStartDate(): ResponseEntity<Map<String, String>> {
        val startDate = classCalculationService.getConfiguredStartDate()
        return if (startDate != null) ResponseEntity.ok(mapOf("startDate" to startDate.toString()))
        else ResponseEntity.status(404).body(mapOf("error" to "Start date not configured"))
    }

    /** The caller's last day of classes: their batch's own date (1st years end later), else the general one. */
    @GetMapping("/classes-end-date")
    fun getClassesEndDate(@AuthenticationPrincipal oauth2User: OAuth2User?): ResponseEntity<Map<String, String>> {
        val sid = oauth2User?.getAttribute<String>("email")?.let { studentRepositoryAppAction.findByEmail(it)?.sid }
        val endDate = classCalculationService.endDateFor(sid)
        return if (endDate != null) ResponseEntity.ok(mapOf("endDate" to endDate.toString()))
        else ResponseEntity.status(404).body(mapOf("error" to "End date not configured"))
    }
}
