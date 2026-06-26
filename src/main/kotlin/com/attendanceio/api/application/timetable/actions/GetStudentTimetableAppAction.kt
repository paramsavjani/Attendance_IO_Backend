package com.attendanceio.api.application.timetable.actions

import com.attendanceio.api.application.timetable.adapters.TimetableAdapter
import com.attendanceio.api.model.timetable.TimetableResponse
import com.attendanceio.api.repository.semester.SemesterRepositoryAppAction
import com.attendanceio.api.repository.timetable.StudentTimetableRepositoryAppAction
import org.springframework.stereotype.Component

@Component
class GetStudentTimetableAppAction(
    private val semesterRepositoryAppAction: SemesterRepositoryAppAction,
    private val studentTimetableRepositoryAppAction: StudentTimetableRepositoryAppAction,
    private val timetableAdapter: TimetableAdapter
) {
    fun execute(studentId: Long): TimetableResponse {
        val activeSemesters = semesterRepositoryAppAction.findByIsActive(true)
        if (activeSemesters.isEmpty()) return TimetableResponse(emptyList())
        val currentSemesterId = activeSemesters.first().id ?: return TimetableResponse(emptyList())
        val timetableEntries = studentTimetableRepositoryAppAction.findByStudentIdAndSemesterId(studentId, currentSemesterId)
        return timetableAdapter.toResponse(timetableEntries)
    }
}

