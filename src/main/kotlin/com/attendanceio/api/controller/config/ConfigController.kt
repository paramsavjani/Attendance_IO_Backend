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
    @Value("\${app.classes.start-date:}") private val startDateString: String,
    @Value("\${app.classes.end-date:}") private val endDateString: String
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
    
    @GetMapping("/classes-end-date")
    fun getClassesEndDate(): ResponseEntity<Map<String, String>> {
        val endDate = try {
            if (endDateString.isBlank()) {
                null
            } else {
                LocalDate.parse(endDateString, DateTimeFormatter.ISO_LOCAL_DATE)
            }
        } catch (e: DateTimeParseException) {
            null
        }
        
        return if (endDate != null) {
            ResponseEntity.ok(mapOf("endDate" to endDate.toString()))
        } else {
            ResponseEntity.status(404).body(mapOf("error" to "End date not configured"))
        }
    }
}

