package com.attendanceio.api.application.agent.tools

import com.attendanceio.api.application.agent.actions.AgentStudentQueryAppAction
import com.attendanceio.api.config.AgentProperties
import com.attendanceio.api.model.agent.AgentListResult
import com.attendanceio.api.model.agent.AgentSubjectRecords
import com.attendanceio.api.model.search.StudentAttendanceResponse
import com.attendanceio.api.model.search.StudentSearchResponse
import org.springframework.ai.chat.model.ToolContext
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam
import org.springframework.stereotype.Component

/** Tools about any student. Thin adapters over the same AppActions the Search page uses. */
@Component
class StudentAgentTools(
    private val studentQuery: AgentStudentQueryAppAction,
    private val support: AgentToolSupport,
    private val properties: AgentProperties
) {
    @Tool(
        name = "search_students",
        description = "Find students by (part of) name or roll number. Returns id, name, rollNumber and their current-semester " +
            "attendance percentage from app data. Call this FIRST whenever the user names another person, then use the " +
            "returned id with get_student_attendance or get_subject_records. If several match, ask the user which one."
    )
    fun searchStudents(
        @ToolParam(description = "Name fragment (e.g. 'Rahul', 'Meet Kantilal') or roll number (e.g. '202301045')") query: String,
        toolContext: ToolContext
    ): AgentListResult<StudentSearchResponse> =
        support.recorded(toolContext, "search_students", mapOf("query" to query)) {
            studentQuery.searchStudents(query, properties.maxToolRows)
        }

    @Tool(
        name = "get_student_attendance",
        description = "A student's attendance for EVERY semester, per subject (present/absent/total). official=false uses what " +
            "the student marked in the app; official=true uses the institute's published figures (exist for past semesters only). " +
            "Use for 'what is X's attendance', 'X's attendance last semester', comparisons between students."
    )
    fun getStudentAttendance(
        @ToolParam(description = "Student id from search_students (or the caller's own id)") studentId: Long,
        @ToolParam(description = "true = institute's official figures; false/omitted = app-marked data", required = false) official: Boolean?,
        toolContext: ToolContext
    ): StudentAttendanceResponse? =
        support.recorded(toolContext, "get_student_attendance", mapOf("studentId" to studentId, "official" to official)) {
            studentQuery.studentAttendance(studentId, official == true)
        }

    @Tool(
        name = "get_subject_records",
        description = "Lecture-by-lecture records of one student in one subject, newest first: date, weekday, PRESENT/ABSENT/CANCELLED, " +
            "time. Also returns lastAttendedOn and lastAbsentOn. Use for 'when did I last attend DSA', 'did I miss CT303 last week', " +
            "'previous attendance in a subject', 'how many classes of X happened in September'. Omit studentId for the caller."
    )
    fun getSubjectRecords(
        @ToolParam(description = "Subject code or name, e.g. 'CT303-A', 'DSA', 'Signals and Systems'") subject: String,
        @ToolParam(description = "Student id from search_students; omit for the calling student", required = false) studentId: Long?,
        @ToolParam(description = "Semester id from list_semesters; omit for the current semester", required = false) semesterId: Long?,
        @ToolParam(description = "How many latest records to return (default 15, max 60)", required = false) limit: Int?,
        toolContext: ToolContext
    ): AgentSubjectRecords? =
        support.recorded(
            toolContext, "get_subject_records",
            mapOf("subject" to subject, "studentId" to studentId, "semesterId" to semesterId, "limit" to limit)
        ) {
            val id = studentId ?: support.myStudentId(toolContext)
            studentQuery.subjectRecords(id, subject, semesterId, (limit ?: 15).coerceIn(1, 60))
        }
}
