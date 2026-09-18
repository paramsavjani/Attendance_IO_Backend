package com.attendanceio.api.application.agent

import java.time.Instant
import java.util.Collections

/** One tool invocation as observed by the recorder — enough to show the user what the model did. */
data class RecordedToolCall(
    val name: String,
    val arguments: Map<String, Any?>,
    val startedAt: Instant,
    val durationMs: Long,
    val error: String?
)

/**
 * Per-request log of the tools the model invoked. Travels to the tools through Spring AI's
 * ToolContext so nothing here depends on thread-locals — tool execution may happen on a different
 * thread from the HTTP request when the answer is streamed.
 */
class AgentToolCallRecorder {
    private val calls = Collections.synchronizedList(mutableListOf<RecordedToolCall>())

    /** Runs [block] and records name, arguments, timing and — if it threw — the error. */
    fun <T> record(name: String, arguments: Map<String, Any?>, block: () -> T): T {
        val startedAt = Instant.now()
        return try {
            block().also { calls.add(RecordedToolCall(name, arguments, startedAt, elapsed(startedAt), null)) }
        } catch (e: Exception) {
            calls.add(RecordedToolCall(name, arguments, startedAt, elapsed(startedAt), e.message ?: e.javaClass.simpleName))
            throw e
        }
    }

    fun snapshot(): List<RecordedToolCall> = synchronized(calls) { calls.toList() }

    private fun elapsed(since: Instant): Long = Instant.now().toEpochMilli() - since.toEpochMilli()

    companion object {
        const val CONTEXT_KEY = "toolCallRecorder"
        const val CALLER_KEY = "caller"
    }
}
