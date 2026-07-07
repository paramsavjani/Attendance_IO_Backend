package com.attendanceio.api.application.student.actions

import com.attendanceio.api.model.student.BaselineAttendanceResponse
import com.attendanceio.api.repository.attendance.InstituteAttendanceRepositoryAppAction
import org.springframework.stereotype.Component
import java.time.LocalDate

@Component
class GetBaselineAttendanceAppAction(
    private val instituteAttendanceRepositoryAppAction: InstituteAttendanceRepositoryAppAction
) {
    fun execute(studentId: Long, subjectId: Long): BaselineAttendanceResponse {
        val records = instituteAttendanceRepositoryAppAction.findByStudentIdAndSubjectId(studentId, subjectId)
        val latest = records.maxByOrNull { it.cutoffDate ?: LocalDate.MIN }
        return BaselineAttendanceResponse(
            subjectId = subjectId.toString(),
            cutoffDate = latest?.cutoffDate?.toString(),
            totalClasses = latest?.totalClasses,
            presentClasses = latest?.presentClasses
        )
    }
}
