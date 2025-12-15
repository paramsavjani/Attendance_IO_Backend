package com.attendanceio.api.application.search.actions

import com.attendanceio.api.model.attendance.AttendanceStatus
import com.attendanceio.api.repository.attendance.AttendanceRepositoryAppAction
import com.attendanceio.api.repository.attendance.InstituteAttendanceRepositoryAppAction
import com.attendanceio.api.repository.student.StudentRepositoryAppAction
import com.attendanceio.api.repository.student.StudentSubjectRepositoryAppAction
import org.springframework.stereotype.Component
import java.time.LocalDate

@Component
class GetStudentAttendanceAppAction(
    private val studentRepositoryAppAction: StudentRepositoryAppAction,
    private val attendanceRepositoryAppAction: AttendanceRepositoryAppAction,
    private val instituteAttendanceRepositoryAppAction: InstituteAttendanceRepositoryAppAction,
    private val studentSubjectRepositoryAppAction: StudentSubjectRepositoryAppAction
) {
    fun execute(studentId: Long): Map<String, Any> {
        val student = studentRepositoryAppAction.findById(studentId)
            ?: throw IllegalArgumentException("Student not found")

        return calculateStudentAttendance(studentId, student)
    }

    private fun calculateStudentAttendance(
        studentId: Long,
        student: com.attendanceio.api.model.student.DMStudent
    ): Map<String, Any> {
        // Step 1: Get all enrolled subjects from student_subject table (student-subject mapping)
        val studentSubjects = studentSubjectRepositoryAppAction.findByStudentId(studentId)
        val enrolledSubjectIds = studentSubjects
            .mapNotNull { it.subject?.id }
            .distinct()
        
        // Step 2: Get all institute attendance records for enrolled subjects
        val allInstituteAttendance = instituteAttendanceRepositoryAppAction.findByStudentId(studentId)
        
        // Step 3: For each enrolled subject, calculate attendance
        val subjectAttendanceMap = mutableMapOf<Long, SubjectAttendanceData>()
        
        enrolledSubjectIds.forEach { subjectId ->
            // Step 3a: Find latest institute attendance for this subject (base attendance)
            val instituteAttendanceForSubject = allInstituteAttendance
                .filter { it.subject?.id == subjectId }
                .maxByOrNull { it.cutoffDate ?: LocalDate.MIN }
            
            // Step 3b: Get base attendance from institute attendance (or zero if not available)
            val basePresent = instituteAttendanceForSubject?.presentClasses ?: 0
            val baseAbsent = if (instituteAttendanceForSubject != null) {
                instituteAttendanceForSubject.totalClasses - instituteAttendanceForSubject.presentClasses
            } else {
                0
            }
            val baseTotal = instituteAttendanceForSubject?.totalClasses ?: 0
            
            // Step 3c: Get cutoff date (if institute attendance exists)
            val cutoffDate = instituteAttendanceForSubject?.cutoffDate
            
            // Step 3d: Count attendance records after cutoff date (or all if no cutoff)
            val attendanceAfterCutoff = if (cutoffDate != null) {
                attendanceRepositoryAppAction.findByStudentIdAndSubjectIdAndLectureDateAfter(
                    studentId,
                    subjectId,
                    cutoffDate
                )
            } else {
                attendanceRepositoryAppAction.findByStudentIdAndSubjectId(studentId, subjectId)
            }
            
            // Step 3e: Count attendance statuses after cutoff date
            val presentAfterCutoff = attendanceAfterCutoff.count { it.status == AttendanceStatus.PRESENT }
            val absentAfterCutoff = attendanceAfterCutoff.count { it.status == AttendanceStatus.ABSENT }
            val leaveAfterCutoff = attendanceAfterCutoff.count { it.status == AttendanceStatus.LEAVE }
            val totalAfterCutoff = attendanceAfterCutoff.size
            
            // Step 3f: Calculate final attendance (base + after cutoff)
            val finalPresent = basePresent + presentAfterCutoff
            val finalAbsent = baseAbsent + absentAfterCutoff
            val finalLeave = leaveAfterCutoff
            val finalTotal = baseTotal + totalAfterCutoff
            
            // Step 3g: Get subject and semester info from student_subject mapping
            val studentSubject = studentSubjects.firstOrNull { it.subject?.id == subjectId }
            val subject = studentSubject?.subject ?: return@forEach
            
            val semester = subject.semester ?: return@forEach
            val semesterId = semester.id ?: return@forEach
            
            subjectAttendanceMap[subjectId] = SubjectAttendanceData(
                subjectId = subjectId,
                subjectCode = subject.code,
                subjectName = subject.name,
                semesterId = semesterId,
                semesterYear = semester.year,
                semesterType = semester.type.name,
                present = finalPresent,
                absent = finalAbsent,
                leave = finalLeave,
                total = finalTotal
            )
        }
        
        // Step 4: Group by semester
        val semesterMap = mutableMapOf<Long, SemesterData>()
        
        subjectAttendanceMap.values.forEach { subjectData ->
            val semesterId = subjectData.semesterId
            
            if (!semesterMap.containsKey(semesterId)) {
                semesterMap[semesterId] = SemesterData(
                    semesterId = semesterId,
                    year = subjectData.semesterYear,
                    type = subjectData.semesterType,
                    subjects = mutableListOf()
                )
            }
            
            semesterMap[semesterId]!!.subjects.add(
                mapOf(
                    "subjectId" to subjectData.subjectId.toString(),
                    "subjectCode" to subjectData.subjectCode,
                    "subjectName" to subjectData.subjectName,
                    "present" to subjectData.present,
                    "absent" to subjectData.absent,
                    "leave" to subjectData.leave,
                    "total" to subjectData.total
                )
            )
        }
        
        // Step 5: Convert to response format
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
    
    private data class SubjectAttendanceData(
        val subjectId: Long,
        val subjectCode: String,
        val subjectName: String,
        val semesterId: Long,
        val semesterYear: Int,
        val semesterType: String,
        val present: Int,
        val absent: Int,
        val leave: Int,
        val total: Int
    )
    
    private data class SemesterData(
        val semesterId: Long,
        val year: Int,
        val type: String,
        val subjects: MutableList<Map<String, Any>>
    )
}
