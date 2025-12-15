package com.attendanceio.api

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class AttendanceIOBackendApplication

fun main(args: Array<String>) {
	runApplication<AttendanceIOBackendApplication>(*args)
}
