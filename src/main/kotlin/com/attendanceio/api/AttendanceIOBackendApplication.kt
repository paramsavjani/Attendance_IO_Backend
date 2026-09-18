package com.attendanceio.api

import com.attendanceio.api.config.AgentProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling
import java.util.TimeZone
import jakarta.annotation.PostConstruct

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(AgentProperties::class)
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
