package com.attendanceio.api.application.agent.actions

import com.attendanceio.api.model.agent.AgentSemesterSummary
import com.attendanceio.api.model.agent.AgentSubjectSummary
import com.attendanceio.api.model.semester.DMSemester
import com.attendanceio.api.model.subject.DMSubject
import com.attendanceio.api.repository.semester.SemesterRepositoryAppAction
import com.attendanceio.api.repository.student.StudentSubjectRepositoryAppAction
import com.attendanceio.api.repository.subject.SubjectRepositoryAppAction
import org.springframework.stereotype.Component

/**
 * Semesters and subjects as the agent sees them, plus the one piece of logic every subject
 * question needs first: turning what a student typed ("DSA", "cs374", "digital comm") into a
 * subject row of the right semester.
 */
@Component
class AgentCatalogAppAction(
    private val semesterRepositoryAppAction: SemesterRepositoryAppAction,
    private val subjectRepositoryAppAction: SubjectRepositoryAppAction,
    private val studentSubjectRepositoryAppAction: StudentSubjectRepositoryAppAction
) {
    fun activeSemester(): DMSemester? = semesterRepositoryAppAction.findByIsActive(true).firstOrNull()

    fun semester(semesterId: Long?): DMSemester? =
        if (semesterId == null) activeSemester() else semesterRepositoryAppAction.findById(semesterId)

    fun listSemesters(): List<AgentSemesterSummary> =
        semesterRepositoryAppAction.findAll()
            .sortedWith(compareBy({ it.year }, { it.type.name }))
            .map { it.toSummary() }

    fun listSubjects(semesterId: Long?, callerStudentId: Long?): List<AgentSubjectSummary> {
        val semester = semester(semesterId) ?: return emptyList()
        val sid = semester.id ?: return emptyList()
        val mine: Set<Long>? = callerStudentId?.let { id ->
            studentSubjectRepositoryAppAction.findByStudentId(id).mapNotNull { it.subject?.id }.toSet()
        }
        return subjectRepositoryAppAction.findBySemesterId(sid)
            .sortedBy { it.code }
            .map { it.toSummary(semester, mine?.contains(it.id)) }
    }

    /**
     * Resolve a subject from free text. Order: exact code in the requested/active semester, exact
     * code in any semester (newest first), then name containment in the requested/active semester,
     * then name containment anywhere. Null when nothing matches — the tool tells the model to ask.
     */
    fun resolveSubject(query: String, semesterId: Long?): DMSubject? {
        val q = query.trim()
        if (q.isBlank()) return null
        val semester = semester(semesterId)
        val semesterSubjects = semester?.id?.let { subjectRepositoryAppAction.findBySemesterId(it) }.orEmpty()

        semesterSubjects.firstOrNull { it.code.equals(q, ignoreCase = true) }?.let { return it }
        subjectRepositoryAppAction.findAllByCodeIgnoreCaseOrderByIdDesc(q).firstOrNull()?.let { return it }

        val needle = q.lowercase()
        semesterSubjects.firstOrNull { it.name.lowercase().contains(needle) }?.let { return it }
        // Codes are often typed without the section suffix ("CT204" for "CT204-A"): prefix match last.
        semesterSubjects.firstOrNull { it.code.lowercase().startsWith(needle) }?.let { return it }
        return semesterRepositoryAppAction.findAll()
            .sortedByDescending { it.id }
            .asSequence()
            .flatMap { sem -> sem.id?.let { subjectRepositoryAppAction.findBySemesterId(it) }.orEmpty().asSequence() }
            .firstOrNull { it.name.lowercase().contains(needle) || it.code.lowercase().startsWith(needle) }
    }

    fun label(semester: DMSemester?): String =
        if (semester == null) "unknown semester" else "${semester.year} ${semester.type.name}${if (semester.isActive) " (current)" else ""}"

    private fun DMSemester.toSummary() = AgentSemesterSummary(
        semesterId = id ?: 0,
        year = year,
        type = type.name,
        label = label(this),
        isActive = isActive
    )

    private fun DMSubject.toSummary(semester: DMSemester, enrolledByMe: Boolean?) = AgentSubjectSummary(
        subjectId = id ?: 0,
        code = code,
        name = name,
        lecturePlace = lecturePlace,
        semesterId = semester.id ?: 0,
        semesterLabel = label(semester),
        enrolledByMe = enrolledByMe
    )
}
