package com.attendanceio.api.application.agent.tools

import com.attendanceio.api.application.agent.actions.AgentMyQueryAppAction
import com.attendanceio.api.model.agent.AgentMyAttendance
import com.attendanceio.api.model.agent.AgentTimetable
import org.springframework.ai.chat.model.ToolContext
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam
import org.springframework.stereotype.Component
import java.time.LocalDate

/** Tools about the person asking. The student id always comes from the signed-in caller, never from the model. */
@Component
class MyAttendanceAgentTools(
    private val myQuery: AgentMyQueryAppAction,
    private val support: AgentToolSupport
) {
    @Tool(
        name = "get_my_attendance",
        description = "The calling student's attendance for the CURRENT semester, per subject: present, absent, total, " +
            "percentage, classesNeeded (to reach their minimum criteria) and bunkableClasses (classes they can still skip). " +
            "Also lists the lectures recorded on asOfDate. Use for any 'my attendance / can I bunk / how many classes do I need' question. " +
            "For another student, use get_student_attendance instead."
    )
    fun getMyAttendance(
        @ToolParam(description = "ISO date (YYYY-MM-DD) to compute up to; omit for today", required = false) asOfDate: String?,
        toolContext: ToolContext
    ): AgentMyAttendance =
        support.recorded(toolContext, "get_my_attendance", mapOf("asOfDate" to asOfDate)) {
            myQuery.myAttendance(support.myStudentId(toolContext), asOfDate?.let(LocalDate::parse))
        }

    @Tool(
        name = "get_my_timetable",
        description = "The calling student's weekly lecture timetable for the current semester: day, start/end time, subject, room. " +
            "Use for 'what classes do I have on Tuesday', 'when is my next DSA lecture', 'where is the CT303 class'."
    )
    fun getMyTimetable(toolContext: ToolContext): AgentTimetable =
        support.recorded(toolContext, "get_my_timetable", emptyMap()) {
            myQuery.myTimetable(support.myStudentId(toolContext))
        }
}
