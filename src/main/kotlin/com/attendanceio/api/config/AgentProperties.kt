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
    /** Same, for the public demo's prompt (`agent/public-system-prompt.md` when blank). */
    val publicSystemPromptPath: String = "",
    /** Guard against a runaway model call; the SSE stream ends with an error event past this. */
    val requestTimeoutSeconds: Long = 90,
    /** Messages one user may send per calendar day (IST). 0 disables the limit. */
    val dailyMessageLimit: Int = 20,
    /** Student ids the daily limit does not apply to (the maintainer, testers). */
    val dailyLimitExemptStudentIds: Set<Long> = emptySet(),
    /** Hard cap on alumni rows a single tool call (and so a single answer) can return; the next page is a new question. */
    val alumniRowsPerAnswer: Int = 6,
    /** Retries when the model provider throttles (429/402/503) before giving up; 0 disables. */
    val rateLimitRetries: Int = 3,
    /** First backoff wait; doubles on every retry (2s, 4s, 8s). */
    val rateLimitBackoffMs: Long = 2000
)
