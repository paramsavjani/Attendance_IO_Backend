package com.attendanceio.api.application.search.adapters

import com.attendanceio.api.model.attendance.MarkedAttendanceSummary
import com.attendanceio.api.model.search.StudentSearchResponse
import com.attendanceio.api.model.student.DMStudent
import org.springframework.stereotype.Component

@Component
class StudentSearchAdapter {
    fun toResponse(student: DMStudent, summary: MarkedAttendanceSummary? = null): StudentSearchResponse? {
        val studentId = student.id ?: return null
        val marked = summary?.markedLectures ?: 0
        val present = summary?.presentLectures ?: 0
        return StudentSearchResponse(
            id = studentId.toString(),
            name = student.name ?: "",
            rollNumber = student.sid,
            email = student.email,
            pictureUrl = student.pictureUrl,
            markedLectures = marked,
            presentLectures = present,
            attendancePercentage = if (marked > 0) present * 100.0 / marked else null
        )
    }

    fun toResponseList(
        students: List<DMStudent>,
        summaries: Map<Long, MarkedAttendanceSummary> = emptyMap()
    ): List<StudentSearchResponse> {
        return students.mapNotNull { student -> toResponse(student, student.id?.let { summaries[it] }) }
    }
}
