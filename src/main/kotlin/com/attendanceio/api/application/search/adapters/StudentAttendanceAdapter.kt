package com.attendanceio.api.application.search.adapters

import com.attendanceio.api.model.attendance.AttendanceCalculationResult
import com.attendanceio.api.model.attendance.DMInstituteAttendance
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
        studentPictureUrl: String? = null,
        attendanceResults: List<AttendanceCalculationResult>,
        computedTotals: Map<Long, Int> = emptyMap(),
        computedPresents: Map<Long, Int> = emptyMap()
    ): StudentAttendanceResponse {
        val semesterMap = mutableMapOf<Long, MutableList<SubjectAttendanceResponse>>()
        
        attendanceResults.forEach { result ->
            val semesterId = result.semesterId
            
            if (!semesterMap.containsKey(semesterId)) {
                semesterMap[semesterId] = mutableListOf()
            }
            
            val finalPresent = computedPresents[result.subjectId]
                ?: (result.basePresent + result.presentAfterCutoff)
            
            val finalTotal = computedTotals[result.subjectId] 
                ?: (result.baseTotal + result.totalAfterCutoff)
            val finalAbsent = maxOf(0, finalTotal - finalPresent)
            
            semesterMap[semesterId]!!.add(
                SubjectAttendanceResponse(
                    subjectId = result.subjectId.toString(),
                    subjectCode = result.subjectCode,
                    subjectName = result.subjectName,
                    present = finalPresent,
                    absent = finalAbsent,
                    leave = 0,
                    total = finalTotal,
                    color = result.subjectColor
                )
            )
        }
        
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
            studentPictureUrl = studentPictureUrl,
            semesters = semesters
        )
    }

    fun toOfficialResponse(
        studentId: Long,
        studentName: String,
        rollNumber: String,
        studentPictureUrl: String? = null,
        officialRecords: List<DMInstituteAttendance>
    ): StudentAttendanceResponse {
        val semesterMap = mutableMapOf<Long, MutableList<SubjectAttendanceResponse>>()
        val semesterInfoMap = mutableMapOf<Long, Pair<Int, String>>()

        officialRecords.forEach { record ->
            val subject = record.subject ?: return@forEach
            val semester = subject.semester ?: return@forEach
            val semesterId = semester.id ?: return@forEach

            if (!semesterMap.containsKey(semesterId)) {
                semesterMap[semesterId] = mutableListOf()
                semesterInfoMap[semesterId] = Pair(semester.year, semester.type.name)
            }

            val total = record.totalClasses
            val present = record.presentClasses
            val absent = total - present

            semesterMap[semesterId]!!.add(
                SubjectAttendanceResponse(
                    subjectId = subject.id.toString(),
                    subjectCode = subject.code,
                    subjectName = subject.name,
                    present = present,
                    absent = absent,
                    leave = 0,
                    total = total,
                    color = subject.color
                )
            )
        }

        val semesters = semesterMap.map { (semesterId, subjects) ->
            val (year, type) = semesterInfoMap[semesterId]!!
            SemesterAttendanceResponse(
                semester = SemesterInfoResponse(
                    id = semesterId.toString(),
                    year = year,
                    type = type
                ),
                subjects = subjects
            )
        }.sortedWith(compareByDescending<SemesterAttendanceResponse> { it.semester.year }
            .thenByDescending { it.semester.type })

        return StudentAttendanceResponse(
            studentId = studentId.toString(),
            studentName = studentName,
            rollNumber = rollNumber,
            studentPictureUrl = studentPictureUrl,
            semesters = semesters
        )
    }
}

