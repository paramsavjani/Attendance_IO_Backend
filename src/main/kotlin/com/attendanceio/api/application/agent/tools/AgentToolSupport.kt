package com.attendanceio.api.application.agent.tools

import com.attendanceio.api.application.agent.AgentCaller
import com.attendanceio.api.application.agent.AgentToolCallRecorder
import com.attendanceio.api.model.agent.AgentPersonCard
import com.attendanceio.api.repository.student.StudentRepositoryAppAction
import org.springframework.ai.chat.model.ToolContext
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper

/**
 * The one thing every tool does before its own work: identify the caller and record the call.
 * Kept in a helper so each `@Tool` method stays a one-liner over the AppAction it wraps.
 *
 * There is no per-tool permission check here on purpose: every search/attendance endpoint in
 * this app is readable by any signed-in student already (see SecurityConfig), so the tools
 * expose nothing the UI does not. The only identity-sensitive tools are the "my …" ones, which
 * take the caller's own student id from the context and nowhere else.
 */
@Component
class AgentToolSupport(
    private val objectMapper: ObjectMapper,
    private val students: StudentRepositoryAppAction
) {
    fun caller(toolContext: ToolContext): AgentCaller =
        toolContext.context[AgentToolCallRecorder.CALLER_KEY] as? AgentCaller
            ?: throw IllegalStateException("Tool called without a caller in context")

    /** The caller's student id, or a clear error the model can relay ("sign in with an institute account"). */
    fun myStudentId(toolContext: ToolContext): Long =
        caller(toolContext).studentId
            ?: throw IllegalStateException("This account is not linked to a student record, so personal attendance is not available.")

    /**
     * The database id for whatever the model passed as a student. Models often hand over the
     * 9-digit roll number ("202501142") where the id from search_students belongs, and a tool that
     * then quietly found nobody — or the wrong person — got the same call resent until the loop
     * limit. Both forms work now, and an unknown one is an error the model can act on.
     */
    fun studentId(raw: Long): Long {
        if (raw >= ROLL_NUMBER_FLOOR) {
            return students.findBySid(raw.toString())?.id
                ?: throw IllegalArgumentException("No student with roll number $raw. Check the number with search_students.")
        }
        students.findById(raw)
            ?: throw IllegalArgumentException("No student with id $raw. Pass the id field from search_students, or the 9-digit roll number.")
        return raw
    }

    /** Shows [studentId]'s photo card beside this turn's answer; does nothing for the caller themself. */
    fun mention(toolContext: ToolContext, studentId: Long) {
        val recorder = toolContext.context[AgentToolCallRecorder.CONTEXT_KEY] as? AgentToolCallRecorder ?: return
        if (studentId == callerStudentId(toolContext)) return
        val student = students.findById(studentId) ?: return
        recorder.mention(AgentPersonCard(studentId.toString(), student.name ?: "", student.sid, student.pictureUrl))
    }

    /** Same, for search hits that already carry everything the card needs. */
    fun mention(toolContext: ToolContext, person: AgentPersonCard) {
        val recorder = toolContext.context[AgentToolCallRecorder.CONTEXT_KEY] as? AgentToolCallRecorder ?: return
        if (person.id == callerStudentId(toolContext)?.toString()) return
        recorder.mention(person)
    }

    private fun callerStudentId(toolContext: ToolContext): Long? =
        (toolContext.context[AgentToolCallRecorder.CALLER_KEY] as? AgentCaller)?.studentId

    /** [studentId] for an optional parameter, falling back to the caller. */
    fun studentIdOrMe(raw: Long?, toolContext: ToolContext): Long = raw?.let(::studentId) ?: myStudentId(toolContext)

    /**
     * Runs one tool and records it. Read-only transactional on purpose: the assistant executes tools
     * on a reactive thread while the answer streams, where there is no open-session-in-view session,
     * so anything the tool reads lazily would otherwise fail with "Could not initialize proxy" —
     * which is exactly how get_subject_schedule once cost a student their answer twice in a row.
     * Every tool here only reads, so the transaction costs nothing but the session it exists for
     * (Hibernate takes a connection only once a query actually runs).
     */
    @Transactional(readOnly = true)
    fun <T> recorded(toolContext: ToolContext, toolName: String, arguments: Map<String, Any?>, block: () -> T): T {
        val recorder = toolContext.context[AgentToolCallRecorder.CONTEXT_KEY] as? AgentToolCallRecorder
        // Not `recorder?.record(...) ?: block()`: a tool whose answer is null would then run twice.
        return if (recorder == null) block() else recorder.record(toolName, arguments, { result -> result?.let(objectMapper::writeValueAsString) }, block)
    }

    companion object {
        /** Roll numbers are nine digits (202501142); database ids are far smaller. */
        const val ROLL_NUMBER_FLOOR = 100_000_000L
    }
}
