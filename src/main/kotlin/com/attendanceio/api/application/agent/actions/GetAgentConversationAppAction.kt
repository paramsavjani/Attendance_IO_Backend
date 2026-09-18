package com.attendanceio.api.application.agent.actions

import com.attendanceio.api.application.agent.AgentConversationMemory
import com.attendanceio.api.model.agent.AgentChatRequest
import com.attendanceio.api.model.agent.AgentConversationResponse
import com.attendanceio.api.model.agent.AgentStoredMessageResponse
import org.springframework.stereotype.Component

/** The stored thread, so a client can restore a conversation it only holds the id of. */
@Component
class GetAgentConversationAppAction(
    private val memory: AgentConversationMemory
) {
    fun execute(userEmail: String, conversationId: String): AgentConversationResponse {
        require(CONVERSATION_ID.matches(conversationId)) { "conversationId must be a UUID" }
        return AgentConversationResponse(
            conversationId = conversationId,
            messages = memory.loadAll(userEmail, conversationId).map {
                AgentStoredMessageResponse(it.role, it.content, it.at, it.turnId, it.toolNames, it.latencyMs)
            }
        )
    }

    private companion object {
        val CONVERSATION_ID = Regex(AgentChatRequest.CONVERSATION_ID_PATTERN)
    }
}
