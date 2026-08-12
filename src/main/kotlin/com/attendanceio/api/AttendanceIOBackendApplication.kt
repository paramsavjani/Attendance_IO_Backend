package com.attendanceio.api

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling
import java.util.TimeZone
import jakarta.annotation.PostConstruct

@SpringBootApplication
@EnableScheduling
class AttendanceIOBackendApplication {
	
	@PostConstruct
	fun init() {
		// Set default timezone to IST (Asia/Kolkata) for all scheduled tasks and date/time operations
		TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"))
	}
}

fun main(args: Array<String>) {
	runApplication<AttendanceIOBackendApplication>(*args)
}
