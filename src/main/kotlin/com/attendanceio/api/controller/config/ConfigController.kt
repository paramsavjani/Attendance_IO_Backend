package com.attendanceio.api.controller.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

@RestController
@RequestMapping("/api/config")
class ConfigController(
    @Value("\${app.classes.start-date:}") private val startDateString: String
) {
    @GetMapping("/classes-start-date")
    fun getClassesStartDate(): ResponseEntity<Map<String, String>> {
        val startDate = try {
            if (startDateString.isBlank()) {
                null
            } else {
                LocalDate.parse(startDateString, DateTimeFormatter.ISO_LOCAL_DATE)
            }
        } catch (e: DateTimeParseException) {
            null
        }
        
        return if (startDate != null) {
            ResponseEntity.ok(mapOf("startDate" to startDate.toString()))
        } else {
            ResponseEntity.status(404).body(mapOf("error" to "Start date not configured"))
        }
    }
}

