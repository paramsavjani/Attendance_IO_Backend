package com.attendanceio.api.application.search.adapters

import com.attendanceio.api.model.attendance.AttendanceCalculationResult
import com.attendanceio.api.model.search.SemesterAttendanceResponse
import com.attendanceio.api.model.search.SemesterInfoResponse
import com.attendanceio.api.model.search.StudentAttendanceResponse
import com.attendanceio.api.model.search.SubjectAttendanceResponse
import org.springframework.stereotype.Component

@Component
class StudentAttendanceAdapter {
    fun toResponse(
        studentId: Long,
        studentName: String,
        rollNumber: String,
        attendanceResults: List<AttendanceCalculationResult>
    ): StudentAttendanceResponse {
        // Group by semester
        val semesterMap = mutableMapOf<Long, MutableList<SubjectAttendanceResponse>>()
        
        attendanceResults.forEach { result ->
            val semesterId = result.semesterId
            
            if (!semesterMap.containsKey(semesterId)) {
                semesterMap[semesterId] = mutableListOf()
            }
            
            // Calculate final totals (base + after cutoff)
            val finalPresent = result.basePresent + result.presentAfterCutoff
            val finalAbsent = result.baseAbsent + result.absentAfterCutoff
            val finalLeave = result.leaveAfterCutoff
            val finalTotal = result.baseTotal + result.totalAfterCutoff
            
            semesterMap[semesterId]!!.add(
                SubjectAttendanceResponse(
                    subjectId = result.subjectId.toString(),
                    subjectCode = result.subjectCode,
                    subjectName = result.subjectName,
                    present = finalPresent,
                    absent = finalAbsent,
                    leave = finalLeave,
                    total = finalTotal,
                    color = result.subjectColor
                )
            )
        }
        
        // Convert to response format, sorted by year DESC, type DESC
        val semesters = semesterMap.map { (semesterId, subjects) ->
            val firstResult = attendanceResults.first { it.semesterId == semesterId }
            SemesterAttendanceResponse(
                semester = SemesterInfoResponse(
                    id = semesterId.toString(),
                    year = firstResult.semesterYear,
                    type = firstResult.semesterType
                ),
                subjects = subjects
            )
        }.sortedWith(compareByDescending<SemesterAttendanceResponse> { it.semester.year }
            .thenByDescending { it.semester.type })
        
        return StudentAttendanceResponse(
            studentId = studentId.toString(),
            studentName = studentName,
            rollNumber = rollNumber,
            semesters = semesters
        )
    }
}

