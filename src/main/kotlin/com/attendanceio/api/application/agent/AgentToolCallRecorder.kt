package com.attendanceio.api.application.agent

import java.time.Instant
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger

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
    private val previewLimit: Int = DEFAULT_PREVIEW_LIMIT,
    /**
     * Tool calls one turn may make; 0 = unlimited. Every call re-sends the whole conversation to
     * the model, so a model that loops (one date at a time, one student at a time) costs a little
     * more on each pass. Past the budget the tool does not run and the model is told to answer
     * with what it already has.
     */
    private val maxCalls: Int = 0,
    /** Called as each tool starts — lets the stream tell the user what is happening. */
    private val onStart: (String) -> Unit = {}
) {
    private val calls = Collections.synchronizedList(mutableListOf<RecordedToolCall>())
    private val attempts = AtomicInteger()

    /** Runs [block] and records name, arguments, timing, a preview of the result and — if it threw — the error. */
    fun <T> record(name: String, arguments: Map<String, Any?>, preview: (T) -> String?, block: () -> T): T {
        val startedAt = Instant.now()
        if (maxCalls > 0 && attempts.incrementAndGet() > maxCalls) {
            calls.add(RecordedToolCall(name, arguments, startedAt, 0, null, BUDGET_SPENT))
            throw ToolBudgetExhaustedException(maxCalls)
        }
        runCatching { onStart(name) }
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
        const val BUDGET_SPENT = "skipped: tool budget for this question used up"
    }
}

/**
 * Thrown in place of running a tool once a turn has used its budget. Spring AI hands the message
 * back to the model as the tool's result, so it reads as an instruction, not a stack trace.
 */
class ToolBudgetExhaustedException(maxCalls: Int) : RuntimeException(
    "Tool budget used up: this question has already made $maxCalls tool calls, which is the limit. " +
        "Do not call any more tools. Answer now using only the results you already have, and if " +
        "something could not be checked, say so in one short line and suggest a narrower question."
)
