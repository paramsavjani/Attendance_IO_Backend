package com.attendanceio.api.application.agent.actions

import com.attendanceio.api.application.agent.AgentCaller
import com.attendanceio.api.application.agent.AgentConversationMemory
import com.attendanceio.api.application.agent.AgentModelBackoff
import com.attendanceio.api.application.agent.AgentPromptCache
import com.attendanceio.api.application.agent.AgentBusyException
import com.attendanceio.api.application.agent.AgentSystemPromptLoader
import com.attendanceio.api.application.agent.AgentToolCallRecorder
import com.attendanceio.api.application.agent.AgentToolRouter
import com.attendanceio.api.application.agent.RecordedToolCall
import com.attendanceio.api.application.agent.StoredAgentMessage
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
import org.springframework.ai.chat.client.DefaultChatClientBuilder
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.messages.Message
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.google.genai.GoogleGenAiChatOptions
import org.springframework.ai.model.tool.ToolCallingChatOptions
import org.springframework.ai.model.tool.ToolCallingManager
import org.springframework.ai.tool.ToolCallback
import org.springframework.ai.tool.method.MethodToolCallbackProvider
import org.springframework.ai.tool.resolution.StaticToolCallbackResolver
import io.micrometer.observation.ObservationRegistry
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.core.publisher.Sinks
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
    private val backoff: AgentModelBackoff,
    private val toolRouter: AgentToolRouter,
    private val promptCache: AgentPromptCache
) {
    private val logger = LoggerFactory.getLogger(ChatWithAgentAppAction::class.java)

    /**
     * Every tool callback of every group, by name. When a turn runs off a context cache the tool
     * declarations are inside the cache and are not sent with the request, so the options carry no
     * callbacks — this resolver is how the executed call is still matched back to its Kotlin method.
     */
    private val allToolCallbacks: List<ToolCallback> by lazy {
        MethodToolCallbackProvider.builder().toolObjects(*toolRouter.allToolObjects().toTypedArray()).build().toolCallbacks.toList()
    }

    /** Context caching is a Gemini feature; every other provider keeps Spring AI's stock wiring. */
    private val cachingSupported: Boolean get() = provider == GOOGLE_PROVIDER && promptCache.isEnabled()

    private val chatClient: ChatClient by lazy {
        val model = chatModelProvider.ifAvailable
        if (model == null || !cachingSupported) {
            val builder = chatClientBuilderProvider.ifAvailable
                ?: throw IllegalStateException("The assistant is not configured: set AGENT_CHAT_PROVIDER and the matching API key.")
            return@lazy builder.build()
        }
        // Same client as the auto-configured one, except its tool loop can resolve a callback the
        // request never declared. Built here rather than customised through a bean so an instance
        // without caching keeps Spring AI's stock wiring untouched.
        val manager = ToolCallingManager.builder()
            .toolCallbackResolver(StaticToolCallbackResolver(allToolCallbacks))
            // Off by default, and the whole point here: a cached turn declares no tools in the
            // request (they live in the cache), so the only way back from a tool name to its
            // Kotlin method is this resolver.
            .resolutionFallbackEnabled(true)
            .build()
        DefaultChatClientBuilder(
            model,
            ObservationRegistry.NOOP,
            null,
            null,
            ToolCallingAdvisor.builder().toolCallingManager(manager)
        ).build()
    }

    /** Non-streaming turn: the whole answer in one response. */
    fun chat(caller: AgentCaller, request: AgentChatRequest): AgentChatResponse {
        val turn = Turn(caller, request, stream = false)
        val response = try {
            try {
                backoff.call(turn.id) { turn.prompt().call().chatResponse() }
            } catch (e: Exception) {
                if (!turn.retryInline(e)) throw e
                backoff.call(turn.id) { turn.prompt().call().chatResponse() }
            }
        } catch (e: Exception) {
            turn.finish(answer = "", usage = null, firstTokenMs = null, error = e.message ?: e.javaClass.simpleName)
            if (backoff.isRetryable(e)) throw AgentBusyException(AgentModelBackoff.BUSY_MESSAGE, e)
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

        // A throttled call is retried with backoff as long as no token has reached the client yet.
        val model: Flux<AgentStreamEvent> = backoff.retrying(turn.id, nothingSentYet = { firstTokenMs == null }) { turn.prompt().stream().chatResponse() }
            .timeout(Duration.ofSeconds(properties.requestTimeoutSeconds))
            // A dead context cache fails before the first token, so the turn can be re-run inline
            // without the client ever seeing it.
            .onErrorResume { e ->
                if (firstTokenMs == null && e is Exception && turn.retryInline(e)) {
                    turn.prompt().stream().chatResponse().timeout(Duration.ofSeconds(properties.requestTimeoutSeconds))
                } else {
                    Flux.error(e)
                }
            }
            .doOnNext { response -> usageOf(response)?.let { usage = it } }
            .map { response -> response.results.firstOrNull()?.output?.text.orEmpty() }
            .filter { text -> text.isNotEmpty() }
            .doOnNext { text ->
                if (firstTokenMs == null) firstTokenMs = turn.elapsedMs()
                synchronized(answer) { answer.append(text) }
            }
            .map { text -> AgentStreamEvent.token(turn.conversationId, turn.id, text) }
            .doFinally { turn.statusSink.tryEmitComplete() }

        // Tool-start notices are produced on Spring AI's threads while the model call is in flight;
        // merging them here lets the UI say "searching students…" during a multi-second tool loop.
        val tokens: Flux<AgentStreamEvent> = Flux.merge(turn.statusSink.asFlux(), model)

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
                Flux.just(AgentStreamEvent.error(turn.conversationId, turn.id, backoff.describe(e)))
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

    private companion object {
        const val GOOGLE_PROVIDER = "google-genai"

        /** What Gemini says when the cache named in a request is gone or unusable. */
        val CACHE_FAILURE = Regex("(?i)cachedcontent|cached_content|CachedContent not found")
    }

    private data class Outcome(val toolCalls: List<AgentToolCallResponse>, val latencyMs: Long, val usage: AgentTokenUsage?)

    private inner class Turn(val caller: AgentCaller, request: AgentChatRequest, val stream: Boolean) {
        val id: String = UUID.randomUUID().toString()
        val conversationId: String = request.conversationId ?: UUID.randomUUID().toString()
        val message: String = request.message.trim()
        val startedAt: Instant = Instant.now()
        val statusSink: Sinks.Many<AgentStreamEvent> = Sinks.many().unicast().onBackpressureBuffer()
        val recorder = AgentToolCallRecorder(onStart = { name -> statusSink.tryEmitNext(AgentStreamEvent.status(conversationId, id, name)) })
        /**
         * The shared half of the call — identical for every student, which is what makes it
         * cacheable. The caller and the date travel with the question instead (see [userMessage]).
         */
        val systemPrompt: String = systemPromptLoader.load()

        /** The question, prefixed with who is asking and what day it is. */
        val userMessage: String = callerContext() + "\n" + message

        /**
         * Gemini's cache holding [systemPrompt] and this turn's tool declarations, when one could be
         * created. Null falls back to sending both inline, exactly as before.
         */
        val cacheName: String? by lazy { if (cachingSupported) promptCache.nameFor(systemPrompt, toolCallbacks) else null }

        /** Set when a turn fails against its cache, so the retry sends the prefix inline instead. */
        @Volatile
        var skipCache: Boolean = false

        /**
         * True when [e] looks like the cache being gone (expired, deleted, or rejected). The cache is
         * forgotten so the next turn builds a new one, and this turn is told to retry inline.
         */
        fun retryInline(e: Exception): Boolean {
            val cache = cacheName ?: return false
            if (skipCache) return false
            val reason = generateSequence<Throwable>(e) { it.cause }.mapNotNull { it.message }.joinToString(" ")
            if (!CACHE_FAILURE.containsMatchIn(reason)) return false
            logger.warn("agent=CACHE_FAILED turnId={} name={} retryingInline reason={}", id, cache, reason.take(200))
            promptCache.forget(cache)
            skipCache = true
            return true
        }

        /** The callbacks for the groups this turn was routed to. */
        val toolCallbacks: List<ToolCallback> by lazy {
            MethodToolCallbackProvider.builder().toolObjects(*tools.toolObjects.toTypedArray()).build().toolCallbacks.toList()
        }
        /** Tool groups offered to the model for this thread; logged so a wrong routing is visible in the logs. */
        val tools: AgentToolRouter.Selection by lazy {
            toolRouter.select(message, history).also {
                logger.info("agent=TOOLS turnId={} groups={} fallback={}", id, it.groups, it.fallback)
            }
        }
        private val finished = AtomicBoolean(false)
        private var outcome: Outcome? = null

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

        private val toolContext: Map<String, Any> = mapOf(
            AgentToolCallRecorder.CALLER_KEY to caller,
            AgentToolCallRecorder.CONTEXT_KEY to recorder
        )

        fun prompt(): ChatClient.ChatClientRequestSpec {
            val cache = if (skipCache) null else cacheName
            val spec = chatClient.prompt()
                .messages(history.map(::toModelMessage))
                .user(userMessage)
            return if (cache == null) {
                spec.system(systemPrompt)
                    .tools(*tools.toolObjects.toTypedArray())
                    .toolContext(toolContext)
            } else {
                // Gemini refuses a request that carries a cache AND a system instruction or tools:
                // both are already inside the cache, so only the conversation travels. The tools
                // still run — the ChatClient resolves them by name (see [allToolCallbacks]).
                spec.options(cachedOptions(cache))
            }
        }

        /** The model's own defaults, pointed at [cache] and stripped of anything the cache holds. */
        private fun cachedOptions(cache: String): GoogleGenAiChatOptions.Builder {
            val defaults = chatModelProvider.ifAvailable?.defaultOptions as? GoogleGenAiChatOptions
            val builder = (defaults?.mutate() ?: GoogleGenAiChatOptions.builder()) as GoogleGenAiChatOptions.Builder
            builder.cachedContentName(cache)
            builder.useCachedContent(true)
            builder.toolCallbacks(emptyList<ToolCallback>())
            builder.toolContext(toolContext)
            return builder
        }

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
                        AgentTraceMessage("user", userMessage),
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
