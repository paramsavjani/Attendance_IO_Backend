package com.attendanceio.api.controller.agent

import com.attendanceio.api.application.agent.AgentCaller
import com.attendanceio.api.application.agent.actions.ChatWithAgentAppAction
import com.attendanceio.api.model.agent.AgentChatRequest
import com.attendanceio.api.model.agent.AgentChatResponse
import com.attendanceio.api.model.agent.AgentStreamEvent
import com.attendanceio.api.repository.student.StudentRepositoryAppAction
import com.attendanceio.api.util.DemoUserUtil
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.http.codec.ServerSentEvent
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import org.springframework.http.HttpStatus
import reactor.core.publisher.Flux

/**
 * The attendance chat assistant. Requires a signed-in user (SecurityConfig: anything not in the
 * permit list is authenticated). The caller is resolved once here from the principal — the same
 * email → student lookup every other controller does — and handed to the agent explicitly.
 *
 * Conversations are stored (agent_conversation / agent_message) for our own analysis but are not
 * exposed back to users: there is deliberately no list/read endpoint. A page load starts a new
 * thread; the conversation id only lives for the duration of that page.
 */
@RestController
@RequestMapping("/api/agent")
class AgentChatController(
    private val chatWithAgentAppAction: ChatWithAgentAppAction,
    private val studentRepositoryAppAction: StudentRepositoryAppAction
) {
    private val logger = LoggerFactory.getLogger(AgentChatController::class.java)

    /**
     * Server-Sent Events: META (conversation id + turn id), TOKEN chunks, then DONE or ERROR.
     * Send the conversation id back on the next turn to continue the thread.
     */
    @PostMapping("/chat/stream", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun chatStream(
        @AuthenticationPrincipal oauth2User: OAuth2User?,
        @Valid @RequestBody request: AgentChatRequest
    ): Flux<ServerSentEvent<AgentStreamEvent>> {
        val caller = resolveCaller(oauth2User)
        logger.info("Agent chat (stream) email={} conversationId={} messageLength={}", caller.email, request.conversationId, request.message.length)
        return chatWithAgentAppAction.stream(caller, request)
            .map { event -> ServerSentEvent.builder(event).event(event.type.name.lowercase()).build() }
    }

    /** Same as the streamed endpoint but returns the whole answer at once. */
    @PostMapping("/chat")
    fun chat(
        @AuthenticationPrincipal oauth2User: OAuth2User?,
        @Valid @RequestBody request: AgentChatRequest
    ): ResponseEntity<AgentChatResponse> {
        val caller = resolveCaller(oauth2User)
        logger.info("Agent chat email={} conversationId={} messageLength={}", caller.email, request.conversationId, request.message.length)
        return ResponseEntity.ok(chatWithAgentAppAction.chat(caller, request))
    }

    private fun resolveCaller(oauth2User: OAuth2User?): AgentCaller {
        if (oauth2User == null) throw ResponseStatusException(HttpStatus.UNAUTHORIZED)
        val email = oauth2User.getAttribute<String>("email")?.takeIf { it.isNotBlank() }
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED)
        val isDemo = DemoUserUtil.isDemoUser(oauth2User)
        // Demo logins see the demo student's data, exactly as the rest of the app does for them.
        val student = if (isDemo) {
            studentRepositoryAppAction.findById(DemoUserUtil.DEMO_STUDENT_ID)
        } else {
            studentRepositoryAppAction.findByEmail(email)
        }
        return AgentCaller(
            email = email,
            studentId = student?.id,
            name = student?.name,
            rollNumber = student?.sid,
            isDemo = isDemo
        )
    }
}
