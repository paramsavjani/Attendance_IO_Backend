package com.attendanceio.api.application.search.actions

import com.attendanceio.api.repository.attendance.AttendanceRepositoryAppAction
import com.attendanceio.api.repository.student.StudentRepositoryAppAction
import org.springframework.stereotype.Component

@Component
class GetStudentAttendanceAppAction(
    private val studentRepositoryAppAction: StudentRepositoryAppAction,
    private val attendanceRepositoryAppAction: AttendanceRepositoryAppAction
) {
    fun execute(studentId: Long): Map<String, Any> {
        val student = studentRepositoryAppAction.findById(studentId)
            ?: throw IllegalArgumentException("Student not found")

        // Single database query that does all calculations
        val attendanceResults = attendanceRepositoryAppAction.calculateStudentAttendanceBySubject(studentId)
        
        // Group by semester (minimal processing in application layer)
        val semesterMap = mutableMapOf<Long, SemesterData>()
        
        attendanceResults.forEach { result ->
            val semesterId = result.semesterId
            
            if (!semesterMap.containsKey(semesterId)) {
                semesterMap[semesterId] = SemesterData(
                    semesterId = semesterId,
                    year = result.semesterYear,
                    type = result.semesterType,
                    subjects = mutableListOf()
                )
            }
            
            // Calculate final totals (base + after cutoff)
            val finalPresent = result.basePresent + result.presentAfterCutoff
            val finalAbsent = result.baseAbsent + result.absentAfterCutoff
            val finalLeave = result.leaveAfterCutoff
            val finalTotal = result.baseTotal + result.totalAfterCutoff
            
            semesterMap[semesterId]!!.subjects.add(
                mapOf(
                    "subjectId" to result.subjectId.toString(),
                    "subjectCode" to result.subjectCode,
                    "subjectName" to result.subjectName,
                    "present" to finalPresent,
                    "absent" to finalAbsent,
                    "leave" to finalLeave,
                    "total" to finalTotal
                )
            )
        }
        
        // Convert to response format
        val semesters = semesterMap.values.map { semesterData ->
            mapOf(
                "semester" to mapOf(
                    "id" to semesterData.semesterId.toString(),
                    "year" to semesterData.year,
                    "type" to semesterData.type
                ),
                "subjects" to semesterData.subjects
            )
        }
        
        return mapOf(
            "studentId" to studentId.toString(),
            "studentName" to (student.name ?: ""),
            "rollNumber" to student.sid,
            "semesters" to semesters
        )
    }
    
    private data class SemesterData(
        val semesterId: Long,
        val year: Int,
        val type: String,
        val subjects: MutableList<Map<String, Any>>
    )
}
