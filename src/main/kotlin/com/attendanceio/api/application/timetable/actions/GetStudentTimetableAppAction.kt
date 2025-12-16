package com.attendanceio.api.application.timetable.actions

import com.attendanceio.api.application.timetable.adapters.TimetableAdapter
import com.attendanceio.api.model.timetable.TimetableResponse
import com.attendanceio.api.repository.semester.SemesterRepositoryAppAction
import com.attendanceio.api.repository.student.StudentRepositoryAppAction
import com.attendanceio.api.repository.timetable.StudentTimetableRepositoryAppAction
import org.springframework.stereotype.Component

@Component
class GetStudentTimetableAppAction(
    private val studentRepositoryAppAction: StudentRepositoryAppAction,
    private val semesterRepositoryAppAction: SemesterRepositoryAppAction,
    private val studentTimetableRepositoryAppAction: StudentTimetableRepositoryAppAction,
    private val timetableAdapter: TimetableAdapter
) {
    fun execute(studentId: Long): TimetableResponse {
        // Get current active semester
        val activeSemesters = semesterRepositoryAppAction.findByIsActive(true)
        if (activeSemesters.isEmpty()) {
            return TimetableResponse(emptyList())
        }
        val currentSemester = activeSemesters.first()
        val currentSemesterId = currentSemester.id ?: return TimetableResponse(emptyList())
        
        // Get timetable entries for student and current semester
        val timetableEntries = studentTimetableRepositoryAppAction.findByStudentIdAndSemesterId(
            studentId, 
            currentSemesterId
        )
        
        return timetableAdapter.toResponse(timetableEntries)
    }
}

