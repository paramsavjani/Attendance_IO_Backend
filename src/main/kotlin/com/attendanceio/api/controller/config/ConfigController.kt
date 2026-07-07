package com.attendanceio.api.controller.config

import com.attendanceio.api.service.ClassCalculationService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/config")
class ConfigController(
    private val classCalculationService: ClassCalculationService
) {
    @GetMapping("/classes-start-date")
    fun getClassesStartDate(): ResponseEntity<Map<String, String>> {
        val startDate = classCalculationService.getConfiguredStartDate()
        return if (startDate != null) ResponseEntity.ok(mapOf("startDate" to startDate.toString()))
        else ResponseEntity.status(404).body(mapOf("error" to "Start date not configured"))
    }

    @GetMapping("/classes-end-date")
    fun getClassesEndDate(): ResponseEntity<Map<String, String>> {
        val endDate = classCalculationService.getConfiguredEndDate()
        return if (endDate != null) ResponseEntity.ok(mapOf("endDate" to endDate.toString()))
        else ResponseEntity.status(404).body(mapOf("error" to "End date not configured"))
    }
}
