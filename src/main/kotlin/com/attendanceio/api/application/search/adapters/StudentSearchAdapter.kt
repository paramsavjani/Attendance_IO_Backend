package com.attendanceio.api.application.search.adapters

import com.attendanceio.api.model.search.StudentSearchResponse
import com.attendanceio.api.model.student.DMStudent
import org.springframework.stereotype.Component

@Component
class StudentSearchAdapter {
    fun toResponse(student: DMStudent): StudentSearchResponse? {
        val studentId = student.id ?: return null
        return StudentSearchResponse(
            id = studentId.toString(),
            name = student.name ?: "",
            rollNumber = student.sid,
            email = student.email,
            pictureUrl = student.pictureUrl
        )
    }
    
    fun toResponseList(students: List<DMStudent>): List<StudentSearchResponse> {
        return students.mapNotNull { toResponse(it) }
    }
}

