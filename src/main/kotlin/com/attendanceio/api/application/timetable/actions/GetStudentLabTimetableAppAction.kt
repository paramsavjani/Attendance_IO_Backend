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
        // Get current active semester
        val activeSemesters = semesterRepositoryAppAction.findByIsActive(true)
        if (activeSemesters.isEmpty()) {
            return TimetableResponse(emptyList())
        }
        val currentSemester = activeSemesters.first()
        val currentSemesterId = currentSemester.id ?: return TimetableResponse(emptyList())
        
        // Get lab timetable entries for student and current semester
        val timetableEntries = studentLabTimetableRepositoryAppAction.findByStudentIdAndSemesterId(
            studentId, 
            currentSemesterId
        )
        
        return labTimetableAdapter.toResponse(timetableEntries)
    }
}
