package com.attendanceio.api.application.student.actions

import com.attendanceio.api.application.timetable.actions.SyncTimetableWithSubjectsAppAction
import com.attendanceio.api.model.student.DMStudent
import com.attendanceio.api.model.student.DMStudentSubject
import com.attendanceio.api.model.student.SaveEnrolledSubjectsRequest
import com.attendanceio.api.model.timetable.SubjectEnrollmentSyncResult
import com.attendanceio.api.repository.semester.SemesterRepositoryAppAction
import com.attendanceio.api.repository.student.StudentSubjectRepositoryAppAction
import com.attendanceio.api.repository.subject.SubjectRepositoryAppAction
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class SaveEnrolledSubjectsAppAction(
    private val subjectRepositoryAppAction: SubjectRepositoryAppAction,
    private val studentSubjectRepositoryAppAction: StudentSubjectRepositoryAppAction,
    private val semesterRepositoryAppAction: SemesterRepositoryAppAction,
    private val syncTimetableWithSubjectsAppAction: SyncTimetableWithSubjectsAppAction
) {
    private val MAX_SUBJECTS = 7
    
    @Transactional
    fun execute(student: DMStudent, request: SaveEnrolledSubjectsRequest): SaveEnrolledSubjectsResult {
        // Validate max subjects constraint
        if (request.subjectIds.size > MAX_SUBJECTS) {
            throw IllegalArgumentException("Maximum $MAX_SUBJECTS subjects allowed. You selected ${request.subjectIds.size} subjects.")
        }
        
        val studentId = student.id ?: throw IllegalArgumentException("Student ID is null")
        
        // Convert subject IDs to Long and validate format
        val newSubjectIds = request.subjectIds.mapNotNull { subjectIdStr ->
            subjectIdStr.toLongOrNull() ?: throw IllegalArgumentException("Invalid subject ID: $subjectIdStr")
        }.toSet()
        
        // Get current active semester
        val activeSemesters = semesterRepositoryAppAction.findByIsActive(true)
        if (activeSemesters.isEmpty()) {
            throw IllegalArgumentException("No active semester found")
        }
        val currentSemester = activeSemesters.first()
        val currentSemesterId = currentSemester.id ?: throw IllegalArgumentException("Current semester ID is null")
        
        // Step 1: Get previously enrolled subjects for this semester
        val previousEnrollments = studentSubjectRepositoryAppAction.findByStudentId(studentId)
            .filter { it.subject?.semester?.id == currentSemesterId }
        val previousSubjectIds = previousEnrollments.mapNotNull { it.subject?.id }.toSet()
        
        // Step 2: Fetch all subjects (both new and existing) for validation and details
        val allSubjectIds = (previousSubjectIds + newSubjectIds).toList()
        val allSubjects = if (allSubjectIds.isNotEmpty()) {
            subjectRepositoryAppAction.findAllById(allSubjectIds).associateBy { it.id!! }
        } else {
            emptyMap()
        }
        
        // Validate all new subjects exist
        val missingSubjectIds = newSubjectIds.filter { it !in allSubjects.keys }
        if (missingSubjectIds.isNotEmpty()) {
            throw IllegalArgumentException("Subject(s) not found: ${missingSubjectIds.joinToString(", ")}")
        }
        
        // Step 3: Sync timetable with subject changes (handles conflicts)
        val syncResult = syncTimetableWithSubjectsAppAction.execute(
            student = student,
            semester = currentSemester,
            previousSubjectIds = previousSubjectIds,
            newSubjectIds = newSubjectIds,
            allSubjects = allSubjects
        )
        
        // Step 4: Update subject enrollments
        // Delete old enrollments for current semester
        studentSubjectRepositoryAppAction.deleteAllByStudentIdAndSemesterId(studentId, currentSemesterId)
        
        // Create new enrollments with default minimum criteria of 70
        val newEnrollments = newSubjectIds.mapNotNull { subjectId ->
            val subject = allSubjects[subjectId] ?: return@mapNotNull null
            DMStudentSubject().apply {
                this.student = student
                this.subject = subject
                this.minimumCriteria = 70
            }
        }
        
        // Save all new enrollments
        if (newEnrollments.isNotEmpty()) {
            studentSubjectRepositoryAppAction.saveAll(newEnrollments)
        }
        
        return SaveEnrolledSubjectsResult(
            subjectIds = request.subjectIds,
            syncResult = syncResult
        )
    }
}

/**
 * Result of saving enrolled subjects, including timetable sync details
 */
data class SaveEnrolledSubjectsResult(
    val subjectIds: List<String>,
    val syncResult: SubjectEnrollmentSyncResult
)

