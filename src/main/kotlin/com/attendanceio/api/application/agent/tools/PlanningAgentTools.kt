package com.attendanceio.api.application.agent.tools

import com.attendanceio.api.application.agent.actions.AgentPlanningQueryAppAction
import com.attendanceio.api.model.agent.AgentAcademicCalendar
import com.attendanceio.api.model.agent.AgentComparison
import com.attendanceio.api.model.agent.AgentDayReport
import com.attendanceio.api.model.agent.AgentListResult
import com.attendanceio.api.model.agent.AgentMySubjectStats
import com.attendanceio.api.model.agent.AgentSimulation
import com.attendanceio.api.model.agent.AgentSubjectSchedule
import com.attendanceio.api.model.agent.AgentTrend
import com.attendanceio.api.model.agent.AgentUnmarkedLectures
import org.springframework.ai.chat.model.ToolContext
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam
import org.springframework.stereotype.Component
import java.time.LocalDate

/** Compare, simulate, plan: the questions a student actually thinks about after seeing their numbers. */
@Component
class PlanningAgentTools(
    private val planning: AgentPlanningQueryAppAction,
    private val support: AgentToolSupport
) {
    @Tool(
        name = "compare_students",
        description =
            "Current-semester attendance of 2–6 students side by side over their shared subjects in ONE call: per subject " +
                "present/total/% plus each student's average. Ids from search_students; include the caller to compare 'me' " +
                "with friends. Prefer this over several get_student_attendance calls.",
    )
    fun compareStudents(
        @ToolParam(description = "2–6 student ids") studentIds: List<Long>,
        @ToolParam(description = "true (default) = only subjects everyone shares; false = every subject of every student", required = false) sharedOnly: Boolean?,
        toolContext: ToolContext
    ): AgentComparison? =
        support.recorded(toolContext, "compare_students", mapOf("studentIds" to studentIds, "sharedOnly" to sharedOnly)) {
            planning.compare(studentIds, sharedOnly ?: true)
        }

    @Tool(
        name = "simulate_attendance",
        description =
            "What-if for the CALLER in one subject: skip N and/or attend M upcoming classes → new percentage, whether it " +
                "stays above their minimum, and how many more they can bunk. Use for 'can I skip 2 CT303 classes', 'if I " +
                "attend everything will I reach 75%'.",
    )
    fun simulateAttendance(
        @ToolParam(description = "Subject code or name") subject: String,
        @ToolParam(description = "Upcoming classes to skip (default 0)", required = false) skip: Int?,
        @ToolParam(description = "Upcoming classes to attend (default 0)", required = false) attend: Int?,
        toolContext: ToolContext
    ): AgentSimulation? =
        support.recorded(toolContext, "simulate_attendance", mapOf("subject" to subject, "skip" to skip, "attend" to attend)) {
            planning.simulate(support.myStudentId(toolContext), subject, (skip ?: 0).coerceIn(0, 200), (attend ?: 0).coerceIn(0, 200))
        }

    @Tool(
        name = "get_unmarked_lectures",
        description = "Timetabled lectures of the CALLER in a date range that have no attendance record at all — the classes they " +
            "forgot to mark. Use for 'what did I forget to mark', 'unmarked classes this week', 'did I miss marking anything last week'. " +
            "Defaults to the last 7 days."
    )
    fun getUnmarkedLectures(
        @ToolParam(description = "Start date YYYY-MM-DD (default: 7 days ago)", required = false) from: String?,
        @ToolParam(description = "End date YYYY-MM-DD (default: today)", required = false) to: String?,
        toolContext: ToolContext
    ): AgentUnmarkedLectures =
        support.recorded(toolContext, "get_unmarked_lectures", mapOf("from" to from, "to" to to)) {
            val end = to?.let(LocalDate::parse) ?: LocalDate.now()
            val start = from?.let(LocalDate::parse) ?: end.minusDays(7)
            planning.unmarked(support.myStudentId(toolContext), start, end)
        }

    @Tool(
        name = "get_attendance_on_date",
        description = "Everything on one date for a student: each timetabled lecture with PRESENT / ABSENT / CANCELLED / UNMARKED " +
            "(or UPCOMING for future dates). Use for 'what did I have yesterday', 'did I attend everything on Monday', " +
            "'what does Rahul have tomorrow'. Omit studentId for the caller."
    )
    fun getAttendanceOnDate(
        @ToolParam(description = "Date YYYY-MM-DD; default today") date: String?,
        @ToolParam(description = "Student id from search_students; omit for the caller", required = false) studentId: Long?,
        toolContext: ToolContext
    ): AgentDayReport =
        support.recorded(toolContext, "get_attendance_on_date", mapOf("date" to date, "studentId" to studentId)) {
            planning.dayReport(studentId ?: support.myStudentId(toolContext), date?.let(LocalDate::parse) ?: LocalDate.now())
        }

    @Tool(
        name = "get_attendance_trend",
        description = "Week-by-week present/absent/cancelled counts and percentage for the last N weeks (default 6), for all subjects " +
            "or one subject. Use for 'am I improving', 'how was my attendance in August', 'which week did I miss the most'. " +
            "Omit studentId for the caller."
    )
    fun getAttendanceTrend(
        @ToolParam(description = "Subject code or name; omit for all subjects", required = false) subject: String?,
        @ToolParam(description = "Number of weeks back (1–20, default 6)", required = false) weeks: Int?,
        @ToolParam(description = "Student id from search_students; omit for the caller", required = false) studentId: Long?,
        toolContext: ToolContext
    ): AgentTrend? =
        support.recorded(toolContext, "get_attendance_trend", mapOf("subject" to subject, "weeks" to weeks, "studentId" to studentId)) {
            planning.trend(studentId ?: support.myStudentId(toolContext), subject, (weeks ?: 6).coerceIn(1, 20))
        }

    @Tool(
        name = "get_lab_tutorial_attendance",
        description = "The CALLER's lab and tutorial attendance for the current semester (tracked separately from lectures), " +
            "per subject with percentage, classesNeeded and bunkableClasses. Use when the user asks about labs or tutorials."
    )
    fun getLabTutorialAttendance(toolContext: ToolContext): AgentListResult<AgentMySubjectStats> =
        support.recorded(toolContext, "get_lab_tutorial_attendance", emptyMap()) {
            val items = planning.labTutorial(support.myStudentId(toolContext))
            AgentListResult(items, items.size, if (items.isEmpty()) "No lab or tutorial timetable saved for this semester." else null)
        }

    @Tool(
        name = "get_subject_schedule",
        description = "When and where a subject's lectures happen (day, time, room), for any subject — the caller need not be " +
            "enrolled. Use for 'when is the CT303 lecture', 'which room is Signals in', 'does DSA clash with CS374'."
    )
    fun getSubjectSchedule(
        @ToolParam(description = "Subject code or name") subject: String,
        @ToolParam(description = "Semester id; omit for the current semester", required = false) semesterId: Long?,
        toolContext: ToolContext
    ): AgentSubjectSchedule? =
        support.recorded(toolContext, "get_subject_schedule", mapOf("subject" to subject, "semesterId" to semesterId)) {
            planning.subjectSchedule(subject, semesterId)
        }

    @Tool(
        name = "get_academic_calendar",
        description = "Today's date, the current semester, the first and last day of classes, and how many weeks / teaching days " +
            "remain. Use for 'when does the semester end', 'how many weeks are left', 'how many more classes can there be'."
    )
    fun getAcademicCalendar(toolContext: ToolContext): AgentAcademicCalendar =
        support.recorded(toolContext, "get_academic_calendar", emptyMap()) { planning.calendar() }
}
