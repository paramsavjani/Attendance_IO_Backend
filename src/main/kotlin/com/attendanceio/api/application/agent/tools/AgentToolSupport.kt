package com.attendanceio.api.application.agent.tools

import com.attendanceio.api.application.agent.AgentCaller
import com.attendanceio.api.application.agent.AgentToolCallRecorder
import org.springframework.ai.chat.model.ToolContext
import org.springframework.stereotype.Component

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
class AgentToolSupport {
    fun caller(toolContext: ToolContext): AgentCaller =
        toolContext.context[AgentToolCallRecorder.CALLER_KEY] as? AgentCaller
            ?: throw IllegalStateException("Tool called without a caller in context")

    /** The caller's student id, or a clear error the model can relay ("sign in with an institute account"). */
    fun myStudentId(toolContext: ToolContext): Long =
        caller(toolContext).studentId
            ?: throw IllegalStateException("This account is not linked to a student record, so personal attendance is not available.")

    fun <T> recorded(toolContext: ToolContext, toolName: String, arguments: Map<String, Any?>, block: () -> T): T {
        val recorder = toolContext.context[AgentToolCallRecorder.CONTEXT_KEY] as? AgentToolCallRecorder
        return recorder?.record(toolName, arguments, block) ?: block()
    }
}
