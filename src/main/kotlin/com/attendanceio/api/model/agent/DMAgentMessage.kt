package com.attendanceio.api.model.agent

import com.attendanceio.api.model.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

/**
 * One message of a thread. Assistant rows also record what produced them (turn id, tools,
 * latency) — that is what makes a user's "this answer was wrong" traceable later.
 */
@Entity
@Table(
    name = "agent_message",
    indexes = [Index(name = "idx_agent_message_conversation", columnList = "conversation_id, id")]
)
class DMAgentMessage : BaseEntity() {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false)
    var conversation: DMAgentConversation? = null

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    var role: AgentMessageRole = AgentMessageRole.USER

    @Column(name = "content", nullable = false, columnDefinition = "text")
    var content: String = ""

    @Column(name = "turn_id", length = 36)
    var turnId: String? = null

    /** Comma-separated tool names, in call order. */
    @Column(name = "tool_names", length = 500)
    var toolNames: String? = null

    @Column(name = "latency_ms")
    var latencyMs: Long? = null
}
