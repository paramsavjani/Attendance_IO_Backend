package com.attendanceio.api.application.agent.actions

import com.attendanceio.api.application.agent.AgentCaller
import com.attendanceio.api.application.agent.AgentConversationMemory
import com.attendanceio.api.application.agent.AgentSystemPromptLoader
import com.attendanceio.api.application.agent.AgentToolCallRecorder
import com.attendanceio.api.application.agent.RecordedToolCall
import com.attendanceio.api.application.agent.StoredAgentMessage
import com.attendanceio.api.application.agent.tools.AnalyticsAgentTools
import com.attendanceio.api.application.agent.tools.CatalogAgentTools
import com.attendanceio.api.application.agent.tools.MyAttendanceAgentTools
import com.attendanceio.api.application.agent.tools.StudentAgentTools
import com.attendanceio.api.config.AgentProperties
import com.attendanceio.api.external.langfuse.AgentToolCallTrace
import com.attendanceio.api.external.langfuse.AgentTraceMessage
import com.attendanceio.api.external.langfuse.AgentTurnTrace
import com.attendanceio.api.external.langfuse.LangfuseClient
import com.attendanceio.api.model.agent.AgentChatRequest
import com.attendanceio.api.model.agent.AgentChatResponse
import com.attendanceio.api.model.agent.AgentMessageRole
import com.attendanceio.api.model.agent.AgentStreamEvent
import com.attendanceio.api.model.agent.AgentTokenUsage
import com.attendanceio.api.model.agent.AgentToolCallResponse
import org.slf4j.LoggerFactory
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.messages.Message
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * One chat turn: replay the thread's recent history, let the model call tools until it has an
 * answer, remember the exchange, trace it. The tool loop itself is Spring AI's; this class only
 * decides what goes into the prompt and what comes out of it. Only successful turns are stored;
 * every turn — successful or not — is sent to Langfuse.
 *
 * The caller's identity is put into the system prompt every turn (name, roll number, today's
 * date) so the model never has to ask who "I" am, and is also passed to the tools through the
 * ToolContext so "my …" tools read the signed-in student and nothing the model could invent.
 *
 * The chat model is optional at wiring time: with `spring.ai.model.chat=none` no provider bean
 * exists, the app still starts, and the endpoints fail with a clear message instead.
 */
@Component
class ChatWithAgentAppAction(
    private val chatClientBuilderProvider: ObjectProvider<ChatClient.Builder>,
    private val chatModelProvider: ObjectProvider<ChatModel>,
    @Value("\${spring.ai.model.chat:}") private val provider: String,
    private val properties: AgentProperties,
    private val systemPromptLoader: AgentSystemPromptLoader,
    private val memory: AgentConversationMemory,
    private val langfuseClient: LangfuseClient,
    private val myAttendanceTools: MyAttendanceAgentTools,
    private val studentTools: StudentAgentTools,
    private val catalogTools: CatalogAgentTools,
    private val analyticsTools: AnalyticsAgentTools
) {
    private val logger = LoggerFactory.getLogger(ChatWithAgentAppAction::class.java)

    private val chatClient: ChatClient by lazy {
        val builder = chatClientBuilderProvider.ifAvailable
            ?: throw IllegalStateException("The assistant is not configured: set AGENT_CHAT_PROVIDER and the matching API key.")
        builder.build()
    }

    /** Non-streaming turn: the whole answer in one response. */
    fun chat(caller: AgentCaller, request: AgentChatRequest): AgentChatResponse {
        val turn = Turn(caller, request, stream = false)
        val response = try {
            turn.prompt().call().chatResponse()
        } catch (e: Exception) {
            turn.finish(answer = "", usage = null, firstTokenMs = null, error = e.message ?: e.javaClass.simpleName)
            throw e
        }
        val answer = response?.results?.firstOrNull()?.output?.text.orEmpty()
        val outcome = turn.finish(answer = answer, usage = response?.let(::usageOf), firstTokenMs = null, error = null)
        return AgentChatResponse(
            conversationId = turn.conversationId,
            turnId = turn.id,
            answer = answer,
            toolCalls = outcome.toolCalls,
            latencyMs = outcome.latencyMs,
            usage = outcome.usage
        )
    }

    /** Streaming turn: META first, TOKEN chunks as the model produces them, then DONE (or ERROR). */
    fun stream(caller: AgentCaller, request: AgentChatRequest): Flux<AgentStreamEvent> {
        val turn = Turn(caller, request, stream = true)
        val answer = StringBuilder()
        var usage: AgentTokenUsage? = null
        var firstTokenMs: Long? = null

        val tokens: Flux<AgentStreamEvent> = turn.prompt()
            .stream()
            .chatResponse()
            .timeout(Duration.ofSeconds(properties.requestTimeoutSeconds))
            .doOnNext { response -> usageOf(response)?.let { usage = it } }
            .map { response -> response.results.firstOrNull()?.output?.text.orEmpty() }
            .filter { text -> text.isNotEmpty() }
            .doOnNext { text ->
                if (firstTokenMs == null) firstTokenMs = turn.elapsedMs()
                synchronized(answer) { answer.append(text) }
            }
            .map { text -> AgentStreamEvent.token(turn.conversationId, turn.id, text) }

        val done: Flux<AgentStreamEvent> = Flux.defer {
            val outcome = turn.finish(synchronized(answer) { answer.toString() }, usage, firstTokenMs, error = null)
            Flux.just(AgentStreamEvent.done(turn.conversationId, turn.id, outcome.toolCalls, outcome.latencyMs, firstTokenMs, outcome.usage))
        }

        return Flux.concat(Flux.just(AgentStreamEvent.meta(turn.conversationId, turn.id)), tokens, done)
            .doOnCancel {
                // Browser navigated away or hit Stop: the turn is not remembered, but it is logged.
                turn.finish(synchronized(answer) { answer.toString() }, usage, firstTokenMs, error = "cancelled by client")
            }
            .onErrorResume { e ->
                val reason = e.message ?: e.javaClass.simpleName
                logger.error("agent=TURN_FAILED turnId={} email={} reason={}", turn.id, caller.email, reason, e)
                turn.finish(synchronized(answer) { answer.toString() }, usage, firstTokenMs, error = reason)
                Flux.just(AgentStreamEvent.error(turn.conversationId, turn.id, "The assistant could not complete this request: $reason"))
            }
    }

    /** Usage is only present on the chunks/responses that carry it; zeros mean "not reported". */
    private fun usageOf(response: ChatResponse): AgentTokenUsage? {
        val usage = response.metadata.usage
        val input = usage.promptTokens ?: 0
        val output = usage.completionTokens ?: 0
        return if (input == 0 && output == 0) null else AgentTokenUsage(input, output)
    }

    private fun modelName(): String? = runCatching { chatModelProvider.ifAvailable?.defaultOptions?.model }.getOrNull()

    private data class Outcome(val toolCalls: List<AgentToolCallResponse>, val latencyMs: Long, val usage: AgentTokenUsage?)

    private inner class Turn(val caller: AgentCaller, request: AgentChatRequest, val stream: Boolean) {
        val id: String = UUID.randomUUID().toString()
        val message: String = request.message.trim()
        val startedAt: Instant = Instant.now()
        val recorder = AgentToolCallRecorder()
        val systemPrompt: String = systemPromptLoader.load() + "\n\n" + callerContext()
        private val finished = AtomicBoolean(false)
        private var outcome: Outcome? = null

        val conversationId: String = request.conversationId ?: UUID.randomUUID().toString()

        /** Empty for a new thread; otherwise the last `history-window` messages the server remembers. */
        val history: List<StoredAgentMessage> =
            request.conversationId?.let { memory.load(caller.email, it, properties.historyWindow) } ?: emptyList()

        init {
            logger.info(
                "agent=TURN_START turnId={} conversationId={} email={} studentId={} historyMessages={} provider={} model={} stream={}",
                id, conversationId, caller.email, caller.studentId, history.size, provider, modelName(), stream
            )
        }

        fun elapsedMs(): Long = Instant.now().toEpochMilli() - startedAt.toEpochMilli()

        /** Who is asking and what day it is — the two things every "my …" and "this week" question depends on. */
        private fun callerContext(): String = buildString {
            appendLine("## Current user and date")
            appendLine("- Today: ${LocalDate.now()} (${LocalDate.now().dayOfWeek})")
            when {
                caller.studentId == null ->
                    appendLine("- Signed in as ${caller.email}, which is not linked to a student record. Personal attendance tools will not work; other students' data can still be searched.")
                caller.isDemo ->
                    appendLine("- Demo account viewing sample data of student ${caller.name ?: ""} (roll ${caller.rollNumber ?: ""}, studentId ${caller.studentId}).")
                else ->
                    appendLine("- Student: ${caller.name ?: caller.email} (roll ${caller.rollNumber ?: "?"}, studentId ${caller.studentId}). \"I/me/my\" refers to this student.")
            }
        }

        fun prompt(): ChatClient.ChatClientRequestSpec = chatClient
            .prompt()
            .system(systemPrompt)
            .messages(history.map(::toModelMessage))
            .user(message)
            .tools(myAttendanceTools, studentTools, catalogTools, analyticsTools)
            .toolContext(
                mapOf(
                    AgentToolCallRecorder.CALLER_KEY to caller,
                    AgentToolCallRecorder.CONTEXT_KEY to recorder
                )
            )

        /** Remembers the exchange (success only) and logs it. Runs once; later calls return the first outcome. */
        fun finish(answer: String, usage: AgentTokenUsage?, firstTokenMs: Long?, error: String?): Outcome {
            if (!finished.compareAndSet(false, true)) return outcome ?: Outcome(emptyList(), elapsedMs(), usage)
            val endedAt = Instant.now()
            val latencyMs = endedAt.toEpochMilli() - startedAt.toEpochMilli()
            val calls = recorder.snapshot()

            if (error == null) {
                memory.append(
                    caller, conversationId,
                    listOf(
                        StoredAgentMessage(AgentMessageRole.USER, message, startedAt),
                        StoredAgentMessage(AgentMessageRole.ASSISTANT, answer, endedAt, turnId = id, toolNames = calls.map { it.name }, latencyMs = latencyMs)
                    )
                )
            }
            langfuseClient.recordAgentTurn(
                AgentTurnTrace(
                    turnId = id,
                    conversationId = conversationId,
                    userEmail = caller.email,
                    provider = provider.ifBlank { null },
                    model = modelName(),
                    stream = stream,
                    historyMessages = history.size,
                    inputMessages = listOf(AgentTraceMessage("system", systemPrompt)) +
                        history.map { AgentTraceMessage(it.role.name.lowercase(), it.content) } +
                        AgentTraceMessage("user", message),
                    userMessage = message,
                    answer = answer,
                    toolCalls = calls.map { AgentToolCallTrace(it.name, it.arguments, it.startedAt, it.durationMs, it.resultPreview, it.error) },
                    startedAt = startedAt,
                    endedAt = endedAt,
                    latencyMs = latencyMs,
                    firstTokenMs = firstTokenMs,
                    inputTokens = usage?.inputTokens,
                    outputTokens = usage?.outputTokens,
                    error = error
                )
            )
            logger.info(
                "agent=TURN_END turnId={} conversationId={} email={} latencyMs={} firstTokenMs={} tokens={}/{} toolCalls={} error={}",
                id, conversationId, caller.email, latencyMs, firstTokenMs, usage?.inputTokens, usage?.outputTokens,
                calls.map { "${it.name}(${it.durationMs}ms)" }, error
            )
            return Outcome(calls.map(::toResponse), latencyMs, usage).also { outcome = it }
        }
    }

    private fun toModelMessage(message: StoredAgentMessage): Message = when (message.role) {
        AgentMessageRole.USER -> UserMessage(message.content)
        AgentMessageRole.ASSISTANT -> AssistantMessage(message.content)
    }

    private fun toResponse(call: RecordedToolCall): AgentToolCallResponse =
        AgentToolCallResponse(call.name, call.arguments, call.durationMs, call.error)
}
