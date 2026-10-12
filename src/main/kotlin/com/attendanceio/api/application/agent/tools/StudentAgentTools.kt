package com.attendanceio.api.application.agent.tools

import com.attendanceio.api.application.agent.actions.AgentStudentQueryAppAction
import com.attendanceio.api.config.AgentProperties
import com.attendanceio.api.model.agent.AgentListResult
import com.attendanceio.api.model.agent.AgentSubjectRecords
import com.attendanceio.api.model.agent.AgentStudentAttendance
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
        description = "A student's attendance per semester and subject. Every subject carries TWO figures: " +
            "present/absent/total/percentage is the number that counts today (the institute's published figures up to " +
            "officialAsOf, plus the classes the student marked after that date), and officialPresent/officialTotal/" +
            "officialPercentage is the institute's own figure at that cutoff. Quote the first for 'what is X's attendance'; " +
            "quote both when the difference matters, saying which is which and the as-of date. The current semester is " +
            "flagged isCurrent=true and listed first. Pass official=true only when asked for the institute's figure alone."
    )
    fun getStudentAttendance(
        @ToolParam(description = "Student id from search_students (or the caller's own id)") studentId: Long,
        @ToolParam(description = "true = the institute's published figures only; omit for the combined total that counts today", required = false) official: Boolean?,
        toolContext: ToolContext
    ): AgentStudentAttendance? =
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
