package com.attendanceio.api.application.agent

import java.time.Instant
import java.util.Collections

/** One tool invocation as observed by the recorder — enough to replay it in a trace. */
data class RecordedToolCall(
    val name: String,
    val arguments: Map<String, Any?>,
    val startedAt: Instant,
    val durationMs: Long,
    /** JSON of the result, truncated — for the trace, not for the model. */
    val resultPreview: String?,
    val error: String?
)

/**
 * Per-request log of the tools the model invoked. Travels to the tools through Spring AI's
 * ToolContext so nothing here depends on thread-locals — tool execution may happen on a different
 * thread from the HTTP request when the answer is streamed.
 */
class AgentToolCallRecorder(
    private val previewLimit: Int = DEFAULT_PREVIEW_LIMIT
) {
    private val calls = Collections.synchronizedList(mutableListOf<RecordedToolCall>())

    /** Runs [block] and records name, arguments, timing, a preview of the result and — if it threw — the error. */
    fun <T> record(name: String, arguments: Map<String, Any?>, preview: (T) -> String?, block: () -> T): T {
        val startedAt = Instant.now()
        return try {
            block().also { result ->
                val previewText = runCatching { preview(result) }.getOrNull()?.take(previewLimit)
                calls.add(RecordedToolCall(name, arguments, startedAt, elapsed(startedAt), previewText, null))
            }
        } catch (e: Exception) {
            calls.add(RecordedToolCall(name, arguments, startedAt, elapsed(startedAt), null, e.message ?: e.javaClass.simpleName))
            throw e
        }
    }

    fun snapshot(): List<RecordedToolCall> = synchronized(calls) { calls.toList() }

    private fun elapsed(since: Instant): Long = Instant.now().toEpochMilli() - since.toEpochMilli()

    companion object {
        const val CONTEXT_KEY = "toolCallRecorder"
        const val CALLER_KEY = "caller"
        const val DEFAULT_PREVIEW_LIMIT = 4_000
    }
}
