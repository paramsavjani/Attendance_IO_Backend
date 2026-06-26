package com.attendanceio.api.application.timetable.actions

import com.attendanceio.api.application.timetable.adapters.TutorialTimetableAdapter
import com.attendanceio.api.model.timetable.TimetableResponse
import com.attendanceio.api.repository.semester.SemesterRepositoryAppAction
import com.attendanceio.api.repository.timetable.StudentTutorialTimetableRepositoryAppAction
import org.springframework.stereotype.Component

@Component
class GetStudentTutorialTimetableAppAction(
    private val studentTutorialTimetableRepositoryAppAction: StudentTutorialTimetableRepositoryAppAction,
    private val semesterRepositoryAppAction: SemesterRepositoryAppAction,
    private val tutorialTimetableAdapter: TutorialTimetableAdapter
) {
    fun execute(studentId: Long): TimetableResponse {
        val activeSemesters = semesterRepositoryAppAction.findByIsActive(true)
        if (activeSemesters.isEmpty()) return TimetableResponse(emptyList())
        val currentSemesterId = activeSemesters.first().id ?: return TimetableResponse(emptyList())
        val timetableEntries = studentTutorialTimetableRepositoryAppAction.findByStudentIdAndSemesterId(studentId, currentSemesterId)
        return tutorialTimetableAdapter.toResponse(timetableEntries)
    }
}
