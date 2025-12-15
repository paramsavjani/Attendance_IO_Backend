package com.attendanceio.api.application.subject.adapters

import com.attendanceio.api.model.subject.DMSubject
import com.attendanceio.api.model.subject.SubjectResponse
import org.springframework.stereotype.Component

@Component
class SubjectAdapter {
    fun toResponse(subject: DMSubject): SubjectResponse {
        return SubjectResponse(
            id = subject.id?.toString() ?: "",
            code = subject.code,
            name = subject.name,
            lecturePlace = subject.lecturePlace,
            color = subject.color
        )
    }
    
    fun toResponseList(subjects: List<DMSubject>): List<SubjectResponse> {
        return subjects.map { toResponse(it) }
    }
}

