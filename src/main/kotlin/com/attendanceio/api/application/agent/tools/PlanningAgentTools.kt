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
                "present/total/% plus each student's average. Pass ids from search_students or 9-digit roll numbers; include " +
                "the caller to compare 'me' with friends. Prefer this over several get_student_attendance calls.",
    )
    fun compareStudents(
        @ToolParam(description = "2–6 students: ids from search_students or roll numbers") studentIds: List<Long>,
        @ToolParam(description = "true (default) = only subjects everyone shares; false = every subject of every student", required = false) sharedOnly: Boolean?,
        toolContext: ToolContext
    ): AgentComparison =
        support.recorded(toolContext, "compare_students", mapOf("studentIds" to studentIds, "sharedOnly" to sharedOnly)) {
            val ids = studentIds.map(support::studentId).distinct()
            require(ids.size >= 2) { "Comparing needs at least two different students; these all point to the same one." }
            // A null used to come back with no reason, and the model resent the identical call 40 times.
            planning.compare(ids, sharedOnly ?: true)
                ?: throw IllegalStateException("No current semester is set up, so there is nothing to compare.")
        }

    @Tool(
        name = "simulate_attendance",
        description =
            "What-if for the CALLER: skip N and/or attend M upcoming classes → new percentage, whether it stays above " +
                "their minimum, and how many more they can bunk. Omit subject to get EVERY current subject in this one call. " +
                "attendAllRemaining=true attends every class still scheduled this semester (counted for you). Use for 'can I " +
                "skip 2 CT303 classes', 'if I attend everything will I reach 75%', 'what will my attendance be at the end'.",
    )
    fun simulateAttendance(
        @ToolParam(description = "Subject code or name; omit for all current subjects", required = false) subject: String?,
        @ToolParam(description = "Upcoming classes to skip (default 0)", required = false) skip: Int?,
        @ToolParam(description = "Upcoming classes to attend (default 0)", required = false) attend: Int?,
        @ToolParam(description = "true = attend every class left this semester; overrides attend", required = false) attendAllRemaining: Boolean?,
        toolContext: ToolContext
    ): AgentListResult<AgentSimulation>? =
        support.recorded(
            toolContext, "simulate_attendance",
            mapOf("subject" to subject, "skip" to skip, "attend" to attend, "attendAllRemaining" to attendAllRemaining)
        ) {
            planning.simulate(
                support.myStudentId(toolContext), subject?.takeIf { it.isNotBlank() },
                (skip ?: 0).coerceIn(0, 200), (attend ?: 0).coerceIn(0, 200), attendAllRemaining == true
            )?.let { AgentListResult(it, it.size) }
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
        description = "Each timetabled lecture of a student with PRESENT / ABSENT / CANCELLED / UNMARKED (UPCOMING for future " +
            "dates), for one date or a whole range up to 31 days in ONE call (date + toDate), optionally one subject. Never " +
            "call it day by day: for 'when did I last miss X' use get_subject_records; for several days pass a range. Use for " +
            "'what did I have yesterday', 'did I attend everything last week', 'what does Rahul have tomorrow'. Omit studentId " +
            "for the caller."
    )
    fun getAttendanceOnDate(
        @ToolParam(description = "Date YYYY-MM-DD, or the start of a range; default today") date: String?,
        @ToolParam(description = "End of the range YYYY-MM-DD (max 31 days after date); omit for a single day", required = false) toDate: String?,
        @ToolParam(description = "Only this subject (code or name); omit for all", required = false) subject: String?,
        @ToolParam(description = "Student id from search_students or roll number; omit for the caller", required = false) studentId: Long?,
        toolContext: ToolContext
    ): AgentDayReport =
        support.recorded(
            toolContext, "get_attendance_on_date",
            mapOf("date" to date, "toDate" to toDate, "subject" to subject, "studentId" to studentId)
        ) {
            planning.dayReport(
                support.studentIdOrMe(studentId, toolContext),
                date?.let(LocalDate::parse) ?: LocalDate.now(),
                toDate?.takeIf { it.isNotBlank() }?.let(LocalDate::parse),
                subject?.takeIf { it.isNotBlank() }
            )
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
        @ToolParam(description = "Student id from search_students or roll number; omit for the caller", required = false) studentId: Long?,
        toolContext: ToolContext
    ): AgentTrend? =
        support.recorded(toolContext, "get_attendance_trend", mapOf("subject" to subject, "weeks" to weeks, "studentId" to studentId)) {
            planning.trend(support.studentIdOrMe(studentId, toolContext), subject, (weeks ?: 6).coerceIn(1, 20))
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
        description = "When and where lectures happen (day, time, room), all in ONE call: one subject (any, enrolled or not), " +
            "every current subject of the CALLER (omit subject and room), or every subject taught in a room (room). Use for " +
            "'when is the CT303 lecture', 'my weekly timetable', 'which classes do I miss if I leave Thursday and Friday', " +
            "'is anything in CEP-207 at 3 pm', 'does DSA clash with CS374'."
    )
    fun getSubjectSchedule(
        @ToolParam(description = "Subject code or name; omit for the caller's subjects or with room", required = false) subject: String?,
        @ToolParam(description = "Room, e.g. 'CEP-207' or 'LT 3': every current subject taught there", required = false) room: String?,
        @ToolParam(description = "Semester id; omit for the current semester (subject only)", required = false) semesterId: Long?,
        toolContext: ToolContext
    ): AgentListResult<AgentSubjectSchedule>? =
        support.recorded(toolContext, "get_subject_schedule", mapOf("subject" to subject, "room" to room, "semesterId" to semesterId)) {
            val named = subject?.takeIf { it.isNotBlank() }
            val place = room?.takeIf { it.isNotBlank() }
            when {
                named != null -> planning.subjectSchedule(named, semesterId)?.let { AgentListResult(listOf(it), 1) }
                place != null -> planning.schedules(null, place).let {
                    AgentListResult(it, it.size, if (it.isEmpty()) "No current subject is taught in a room matching '$place'." else null)
                }
                else -> planning.schedules(support.myStudentId(toolContext), null).let {
                    AgentListResult(it, it.size, if (it.isEmpty()) "The caller has no subjects enrolled this semester." else null)
                }
            }
        }

    @Tool(
        name = "get_academic_calendar",
        description = "Today's date, the current semester, the first and last day of classes, and how many weeks / teaching days " +
            "remain. Use for 'when does the semester end', 'how many weeks are left', 'how many more classes can there be'."
    )
    fun getAcademicCalendar(toolContext: ToolContext): AgentAcademicCalendar =
        support.recorded(toolContext, "get_academic_calendar", emptyMap()) { planning.calendar() }
}
