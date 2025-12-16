package com.attendanceio.api.application.student.adapters

import com.attendanceio.api.model.student.DMStudentSubject
import com.attendanceio.api.model.student.EnrolledSubjectResponse
import org.springframework.stereotype.Component

@Component
class EnrolledSubjectAdapter {
    fun toResponse(studentSubject: DMStudentSubject): EnrolledSubjectResponse {
        val subject = studentSubject.subject ?: throw IllegalStateException("Subject is null")
        return EnrolledSubjectResponse(
            subjectId = subject.id?.toString() ?: "",
            subjectCode = subject.code,
            subjectName = subject.name,
            lecturePlace = subject.lecturePlace,
            color = subject.color,
            minimumCriteria = studentSubject.minimumCriteria
        )
    }
    
    fun toResponseList(studentSubjects: List<DMStudentSubject>): List<EnrolledSubjectResponse> {
        return studentSubjects.map { toResponse(it) }
    }
}

