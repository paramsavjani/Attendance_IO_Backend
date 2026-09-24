package com.attendanceio.api.controller.agent

import com.attendanceio.api.application.agent.AgentBusyException
import com.attendanceio.api.application.agent.AgentDailyLimitExceededException
import com.attendanceio.api.application.agent.actions.ChatWithAgentAppAction
import com.attendanceio.api.application.agent.`public`.PublicAgentGoogleVerifier
import com.attendanceio.api.application.agent.`public`.PublicAgentIdentity
import com.attendanceio.api.application.agent.`public`.PublicAgentRateLimiter
import com.attendanceio.api.model.agent.AgentChatRequest
import com.attendanceio.api.model.agent.AgentChatResponse
import com.attendanceio.api.model.agent.AgentStreamEvent
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.http.codec.ServerSentEvent
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Flux
import tools.jackson.databind.ObjectMapper

/**
 * The same assistant, open to anyone, for the demo at ai.paramsavjani.in.
 *
 * Nobody signs in here, so three things stand between a visitor and the app's real data, all of them
 * in code rather than in the prompt:
 *
 *  - the caller is anonymous — no student row, so every "my …" tool has nothing to read;
 *  - `PublicAgentToolPolicy` decides which tools exist at all for this path and strips phone numbers
 *    from what they return;
 *  - `PublicAgentRateLimiter` caps answers per visitor and per day, because each one costs money.
 *
 * Conversations are kept in memory for an hour and never written to the database: a public link
 * should not be able to grow a table, and a visitor should not leave a row behind.
 */
@RestController
@RequestMapping("/api/public/agent")
class PublicAgentChatController(
    private val objectMapper: ObjectMapper,
    private val chatWithAgentAppAction: ChatWithAgentAppAction,
    private val rateLimiter: PublicAgentRateLimiter,
    private val identities: PublicAgentIdentity,
    private val googleVerifier: PublicAgentGoogleVerifier
) {
    private val logger = LoggerFactory.getLogger(PublicAgentChatController::class.java)

    /** Server-Sent Events, identical in shape to the app's stream: META, TOKEN…, then DONE or ERROR. */
    @PostMapping("/chat/stream", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun chatStream(
        @Valid @RequestBody body: AgentChatRequest,
        request: HttpServletRequest
    ): Flux<ServerSentEvent<AgentStreamEvent>> {
        val visitor = identities.resolve(request)
        rateLimiter.check(visitor)
        val caller = identities.caller(visitor)
        logger.info(
            "public=CHAT visitor={} tier={} conversationId={} messageLength={}",
            visitor.key, visitor.tier, body.conversationId, body.message.length
        )
        return chatWithAgentAppAction.stream(caller, body)
            .map { event -> ServerSentEvent.builder(event).event(event.type.name.lowercase()).build() }
    }

    /** The whole answer at once, for clients that would rather not stream. */
    @PostMapping("/chat")
    fun chat(
        @Valid @RequestBody body: AgentChatRequest,
        request: HttpServletRequest
    ): ResponseEntity<AgentChatResponse> {
        val visitor = identities.resolve(request)
        rateLimiter.check(visitor)
        val caller = identities.caller(visitor)
        logger.info(
            "public=CHAT visitor={} tier={} conversationId={} messageLength={}",
            visitor.key, visitor.tier, body.conversationId, body.message.length
        )
        return ResponseEntity.ok(chatWithAgentAppAction.chat(caller, body))
    }

    /**
     * What the page needs before anyone types: how many questions this visitor has left, whether
     * signing in would give them more, and a few questions worth asking. Called again after each
     * answer, so the page knows when to show the sign-in prompt. Looking does not count as asking.
     */
    @GetMapping("/info")
    fun info(request: HttpServletRequest): Map<String, Any?> {
        val visitor = identities.resolve(request)
        val limits = rateLimiter.snapshot()
        return mapOf(
            "signedIn" to visitor.signedIn,
            "name" to visitor.name,
            "remaining" to rateLimiter.remaining(visitor),
            "limit" to rateLimiter.limitFor(visitor),
            "signedInLimit" to limits.signedInLimit,
            "askedToday" to limits.askedToday,
            "dailyLimit" to limits.dailyLimit,
            // Public by nature: the browser has to send it to Google anyway.
            "googleClientId" to googleVerifier.clientId().takeIf { it.isNotBlank() },
            "suggestions" to SUGGESTIONS
        )
    }

    @ExceptionHandler(AgentBusyException::class)
    fun busy(e: AgentBusyException, response: HttpServletResponse) = write(response, HttpStatus.SERVICE_UNAVAILABLE, e.message)

    @ExceptionHandler(AgentDailyLimitExceededException::class)
    fun dailyLimit(e: AgentDailyLimitExceededException, response: HttpServletResponse) =
        write(response, HttpStatus.TOO_MANY_REQUESTS, e.message)

    private fun write(response: HttpServletResponse, status: HttpStatus, message: String?) {
        response.status = status.value()
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.characterEncoding = "UTF-8"
        response.writer.use { it.write(objectMapper.writeValueAsString(mapOf("message" to (message ?: "")))) }
    }

    private companion object {
        /** Deliberately spread across the tools the demo actually has, so nothing suggested can disappoint. */
        val SUGGESTIONS = listOf(
            "Which clubs can I join at DAU?",
            "Who teaches machine learning here?",
            "Where do DAU graduates work?",
            "What were the placement figures last year?",
            "When is the CT303 lecture and in which room?",
            "What does the first-year B.Tech curriculum cover?",
            "Any campus events coming up?",
            "Which scholarships does the institute offer?"
        )
    }
}
