package com.attendanceio.api.external.langfuse

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory

/** A prompt message as sent to the model, for the trace. */
data class AgentTraceMessage(val role: String, val content: String)

/** One tool invocation, for the trace. */
data class AgentToolCallTrace(
    val name: String,
    val arguments: Map<String, Any?>,
    val startedAt: Instant,
    val durationMs: Long,
    val resultPreview: String?,
    val error: String?
)

/** Everything [LangfuseClient.recordAgentTurn] needs. Free of Spring AI types on purpose. */
data class AgentTurnTrace(
    val turnId: String,
    val conversationId: String,
    val userEmail: String,
    val provider: String?,
    val model: String?,
    val stream: Boolean,
    val historyMessages: Int,
    val inputMessages: List<AgentTraceMessage>,
    val userMessage: String,
    val answer: String,
    val toolCalls: List<AgentToolCallTrace>,
    val startedAt: Instant,
    val endedAt: Instant,
    val latencyMs: Long,
    val firstTokenMs: Long?,
    val inputTokens: Int?,
    val outputTokens: Int?,
    val error: String?
)

/**
 * Best-effort sender of assistant traces to Langfuse's public ingestion API
 * (`POST {host}/api/public/ingestion`, Basic Auth: public key as username, secret key as password).
 *
 * Fire-and-forget on a single background thread: a slow or unreachable Langfuse never adds latency
 * to an answer, and every failure is caught and logged.
 *
 * One chat turn becomes: a `trace` (id = turn id, sessionId = conversation id, userId = email) so
 * Langfuse groups a whole thread; a `generation` carrying the full model input (system prompt,
 * replayed history, the question), the answer, model, timing and token usage; and one `span` per
 * tool call with arguments, a truncated result and real start/end times.
 */
@Component
class LangfuseClient(
    private val properties: LangfuseProperties,
    private val objectMapper: ObjectMapper
) {
    private val log = LoggerFactory.getLogger(LangfuseClient::class.java)

    private val httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()

    private val executor = Executors.newSingleThreadExecutor(
        ThreadFactory { r -> Thread(r, "langfuse-sender").apply { isDaemon = true } }
    )

    private val basicAuth: String by lazy {
        Base64.getEncoder().encodeToString("${properties.publicKey}:${properties.secretKey}".toByteArray())
    }

    init {
        log.info("Langfuse configured={} environment='{}'", properties.isConfigured(), properties.environment)
    }

    fun recordAgentTurn(turn: AgentTurnTrace) {
        if (!properties.isConfigured()) return
        executor.execute {
            try {
                ingest(buildEvents(turn))
            } catch (e: Exception) {
                log.warn("Langfuse agent trace send failed: {}", e.message)
            }
        }
    }

    private fun buildEvents(turn: AgentTurnTrace): List<Map<String, Any?>> {
        val now = Instant.now().toString()
        // Null (not blank) lets Langfuse fall back to its own "default" rather than rejecting "".
        val environment = properties.environment.trim().ifBlank { null }
        val metadata = mapOf(
            "provider" to turn.provider,
            "stream" to turn.stream,
            "historyMessages" to turn.historyMessages,
            "latencyMs" to turn.latencyMs,
            "firstTokenMs" to turn.firstTokenMs,
            "toolCallCount" to turn.toolCalls.size,
            "toolNames" to turn.toolCalls.map { it.name }
        )

        val traceEvent = mapOf(
            "id" to UUID.randomUUID().toString(),
            "timestamp" to now,
            "type" to "trace-create",
            "body" to mapOf(
                "id" to turn.turnId,
                "name" to "attendance-assistant",
                "userId" to turn.userEmail,
                "sessionId" to turn.conversationId,
                "environment" to environment,
                "input" to turn.userMessage,
                "output" to turn.answer,
                "metadata" to metadata,
                "tags" to listOf("agent")
            )
        )

        val generationEvent = mapOf(
            "id" to UUID.randomUUID().toString(),
            "timestamp" to now,
            "type" to "generation-create",
            "body" to mapOf(
                "id" to UUID.randomUUID().toString(),
                "traceId" to turn.turnId,
                "name" to "chat",
                "environment" to environment,
                "startTime" to turn.startedAt.toString(),
                "endTime" to turn.endedAt.toString(),
                "model" to turn.model,
                // Plain {role, content: string} entries — Langfuse's chat renderer needs exactly this shape.
                "input" to turn.inputMessages.map { mapOf("role" to it.role, "content" to it.content) },
                "output" to turn.answer,
                "metadata" to metadata,
                "usage" to if (turn.inputTokens != null || turn.outputTokens != null) {
                    mapOf("input" to turn.inputTokens, "output" to turn.outputTokens, "unit" to "TOKENS")
                } else {
                    null
                },
                "level" to if (turn.error == null) "DEFAULT" else "ERROR",
                "statusMessage" to turn.error
            )
        )

        val spanEvents = turn.toolCalls.map { call ->
            mapOf(
                "id" to UUID.randomUUID().toString(),
                "timestamp" to now,
                "type" to "span-create",
                "body" to mapOf(
                    "id" to UUID.randomUUID().toString(),
                    "traceId" to turn.turnId,
                    "name" to "tool:${call.name}",
                    "environment" to environment,
                    "startTime" to call.startedAt.toString(),
                    "endTime" to call.startedAt.plusMillis(call.durationMs).toString(),
                    "input" to call.arguments,
                    "output" to call.resultPreview,
                    "metadata" to mapOf("durationMs" to call.durationMs),
                    "level" to if (call.error == null) "DEFAULT" else "ERROR",
                    "statusMessage" to call.error
                )
            )
        }
        return listOf(traceEvent, generationEvent) + spanEvents
    }

    private fun ingest(batch: List<Map<String, Any?>>) {
        val body = objectMapper.writeValueAsBytes(mapOf("batch" to batch))
        val request = HttpRequest.newBuilder()
            .uri(URI.create("${properties.host.trimEnd('/')}/api/public/ingestion"))
            .timeout(Duration.ofSeconds(15))
            .header("Authorization", "Basic $basicAuth")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofByteArray(body))
            .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) {
            log.warn("Langfuse ingestion returned HTTP {}: {}", response.statusCode(), response.body().take(500))
        }
    }
}
