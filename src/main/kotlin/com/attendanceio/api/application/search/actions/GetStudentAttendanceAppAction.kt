package com.attendanceio.api.application.search.actions

import com.attendanceio.api.model.attendance.AttendanceStatus
import com.attendanceio.api.repository.attendance.AttendanceRepositoryAppAction
import com.attendanceio.api.repository.attendance.InstituteAttendanceRepositoryAppAction
import com.attendanceio.api.repository.student.StudentRepositoryAppAction
import org.springframework.stereotype.Component
import java.time.LocalDate

@Component
class GetStudentAttendanceAppAction(
    private val studentRepositoryAppAction: StudentRepositoryAppAction,
    private val attendanceRepositoryAppAction: AttendanceRepositoryAppAction,
    private val instituteAttendanceRepositoryAppAction: InstituteAttendanceRepositoryAppAction
) {
    fun execute(studentId: Long): Map<String, Any> {
        val student = studentRepositoryAppAction.findById(studentId)
            ?: throw IllegalArgumentException("Student not found")

        return getAllAttendance(studentId, student)
    }

    private fun getAllAttendance(
        studentId: Long,
        student: com.attendanceio.api.model.student.DMStudent
    ): Map<String, Any> {
        // Step 1: Get all institute attendance records to find all enrolled subjects
        val allInstituteAttendance = instituteAttendanceRepositoryAppAction.findByStudentId(studentId)
        
        // Step 2: Get all attendance records for the student
        val allAttendance = attendanceRepositoryAppAction.findByStudentId(studentId)
        
        // Step 3: Find all unique subject IDs the student is enrolled in
        val enrolledSubjectIds = mutableSetOf<Long>()
        allInstituteAttendance.forEach { 
            it.subject?.id?.let { id -> enrolledSubjectIds.add(id) }
        }
        allAttendance.forEach { 
            it.subject?.id?.let { id -> enrolledSubjectIds.add(id) }
        }
        
        // Step 4: For each subject, find latest institute attendance and calculate totals
        val subjectAttendanceMap = mutableMapOf<Long, SubjectAttendanceData>()
        
        enrolledSubjectIds.forEach { subjectId ->
            // Find latest institute attendance for this subject
            val instituteAttendanceForSubject = allInstituteAttendance
                .filter { it.subject?.id == subjectId }
                .maxByOrNull { it.cutoffDate ?: LocalDate.MIN }
            
            val cutoffDate = instituteAttendanceForSubject?.cutoffDate
            
            // Get attendance records after cutoff date (or all if no cutoff)
            val attendanceAfterCutoff = if (cutoffDate != null) {
                allAttendance.filter { 
                    it.subject?.id == subjectId && 
                    it.lectureDate != null && 
                    it.lectureDate!!.isAfter(cutoffDate)
                }
            } else {
                allAttendance.filter { it.subject?.id == subjectId }
            }
            
            // Calculate totals - start with institute attendance baseline
            val presentFromInstitute = instituteAttendanceForSubject?.presentClasses ?: 0
            val absentFromInstitute = if (instituteAttendanceForSubject != null) {
                instituteAttendanceForSubject.totalClasses - instituteAttendanceForSubject.presentClasses
            } else {
                0
            }
            val totalFromInstitute = instituteAttendanceForSubject?.totalClasses ?: 0
            
            var present = presentFromInstitute
            var absent = absentFromInstitute
            var leave = 0
            var total = totalFromInstitute
            
            // Add attendance records after cutoff
            attendanceAfterCutoff.forEach { attendance ->
                when (attendance.status) {
                    AttendanceStatus.PRESENT -> present++
                    AttendanceStatus.ABSENT -> absent++
                    AttendanceStatus.LEAVE -> leave++
                }
                total++
            }
            
            // Get subject and semester info
            val subject = allInstituteAttendance.firstOrNull { it.subject?.id == subjectId }?.subject
                ?: allAttendance.firstOrNull { it.subject?.id == subjectId }?.subject
                ?: return@forEach
            
            val semester = subject.semester ?: return@forEach
            
            subjectAttendanceMap[subjectId] = SubjectAttendanceData(
                subjectId = subjectId,
                subjectCode = subject.code,
                subjectName = subject.name,
                semesterId = semester.id ?: return@forEach,
                semesterYear = semester.year,
                semesterType = semester.type.name,
                present = present,
                absent = absent,
                leave = leave,
                total = total
            )
        }
        
        // Step 5: Group by semester
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
        
        // Step 6: Convert to response format
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
