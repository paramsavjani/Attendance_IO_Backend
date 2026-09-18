package com.attendanceio.api.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Knobs for the chat agent (`app.agent.*`). Provider and model are Spring AI's own properties
 * (`spring.ai.model.chat`, `spring.ai.google.genai.*`, `spring.ai.anthropic.*`), not ours.
 */
@ConfigurationProperties(prefix = "app.agent")
data class AgentProperties(
    /** Messages (user + assistant) replayed to the model per turn, taken from the end of the thread. */
    val historyWindow: Int = 30,
    /** Rows a list tool hands to the model; the true total is reported separately. */
    val maxToolRows: Int = 20,
    /**
     * Optional file the system prompt is read from, re-read whenever its mtime changes, so
     * wording can be tuned on a running instance. Blank falls back to the bundled prompt.
     */
    val systemPromptPath: String = "",
    /** Guard against a runaway model call; the SSE stream ends with an error event past this. */
    val requestTimeoutSeconds: Long = 90,
    /** How long an idle thread stays in memory before it expires. Memory is short-term by design. */
    val memoryTtlHours: Long = 24,
    /** Messages kept per thread; older ones are trimmed on every append. */
    val memoryMaxMessages: Int = 40,
    /** Upper bound on threads held in memory; the least recently used are dropped past this. */
    val memoryMaxThreads: Int = 5000
)
