package com.attendanceio.api

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class AttendanceIOBackendApplication

fun main(args: Array<String>) {
	runApplication<AttendanceIOBackendApplication>(*args)
}
