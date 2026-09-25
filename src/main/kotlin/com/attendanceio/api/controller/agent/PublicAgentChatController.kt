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
            // True only for an institute account with a student row: the page says so, and offers the
            // questions that are only worth asking when the assistant can see your own attendance.
            "fullAccess" to visitor.isStudent,
            "name" to visitor.name,
            "remaining" to rateLimiter.remaining(visitor),
            "limit" to rateLimiter.limitFor(visitor),
            "signedInLimit" to limits.signedInLimit,
            "askedToday" to limits.askedToday,
            "dailyLimit" to limits.dailyLimit,
            // Public by nature: the browser has to send it to Google anyway.
            "googleClientId" to googleVerifier.clientId().takeIf { it.isNotBlank() },
            "suggestions" to if (visitor.isStudent) STUDENT_SUGGESTIONS else SUGGESTIONS
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
        /**
         * Written for whoever opens the link — someone deciding where to apply, a parent, a recruiter,
         * an alumnus — rather than for a student who is already here. Spread across the tools the page
         * actually has, so nothing suggested can disappoint.
         *
         * The whole list is sent; the page shows a few of them and rotates through the rest on each
         * visit, so a second look never opens on the same four questions. That is why there are far
         * more here than fit on the screen, and why none of them is a near-copy of another.
         *
         * Two rules when adding one. It must be answerable by a tool in
         * [com.attendanceio.api.application.agent.public.PublicAgentToolPolicy.ALLOWED], or the page
         * would suggest a question the demo then refuses. And its answer must not be relative to
         * today — no "upcoming", no "next week" — because a suggested question is the one most likely
         * to be served from the day-long answer cache, where "tomorrow" would go stale overnight.
         */
        val SUGGESTIONS = listOf(
            // Placements: the first thing almost everyone opening the link wants.
            "What were DAU's placement figures last year?",
            "Which companies recruit from DAU?",
            "What was the highest package of the last placement season?",
            // Alumni: where the degree actually leads.
            "Where do DAU graduates work?",
            "Which cities do DAU alumni work in?",
            // Academics: what is taught, and at what level.
            "Which programmes does DAU offer?",
            "What does the B.Tech programme cover?",
            "Does DAU offer postgraduate and PhD programmes?",
            // Faculty: who teaches and what they research.
            "Who are the faculty working on AI and machine learning?",
            // Student life: the part a prospectus never conveys.
            "What clubs and student bodies are there?",
            // Campus and cost.
            "What scholarships does DAU offer?",
            "What facilities does the campus have?"
        )

        /**
         * Shown instead when the visitor is a student of the institute, because the questions worth
         * suggesting to them are the ones nobody else can ask. Their answers are never cached — a
         * student's caller is not public, and only public turns are eligible — so these are free to be
         * about the person asking.
         */
        val STUDENT_SUGGESTIONS = listOf(
            "How many classes can I still miss?",
            "What is my attendance this semester?",
            "Which subject am I weakest in?",
            "What does my timetable look like this week?",
            "Am I above the attendance criteria in everything?",
            "How does my attendance compare with my batch?",
            "Which lectures did I miss last week?",
            "What were DAU's placement figures last year?",
            "Where do DAU graduates work?",
            "Who are the faculty working on AI and machine learning?",
            "What clubs and student bodies are there?",
            "What scholarships does DAU offer?"
        )
    }
}
