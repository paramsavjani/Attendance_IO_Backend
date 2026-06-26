package com.attendanceio.api.application.timetable.actions

import com.attendanceio.api.application.timetable.adapters.LabTimetableAdapter
import com.attendanceio.api.model.timetable.TimetableResponse
import com.attendanceio.api.repository.semester.SemesterRepositoryAppAction
import com.attendanceio.api.repository.timetable.StudentLabTimetableRepositoryAppAction
import org.springframework.stereotype.Component

@Component
class GetStudentLabTimetableAppAction(
    private val studentLabTimetableRepositoryAppAction: StudentLabTimetableRepositoryAppAction,
    private val semesterRepositoryAppAction: SemesterRepositoryAppAction,
    private val labTimetableAdapter: LabTimetableAdapter
) {
    fun execute(studentId: Long): TimetableResponse {
        val activeSemesters = semesterRepositoryAppAction.findByIsActive(true)
        if (activeSemesters.isEmpty()) return TimetableResponse(emptyList())
        val currentSemesterId = activeSemesters.first().id ?: return TimetableResponse(emptyList())
        val timetableEntries = studentLabTimetableRepositoryAppAction.findByStudentIdAndSemesterId(studentId, currentSemesterId)
        return labTimetableAdapter.toResponse(timetableEntries)
    }
}
