package com.attendanceio.api.model.agent

import com.attendanceio.api.model.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table

/**
 * One chat thread of the assistant. Keyed by the signed-in account's email (not a student row)
 * so demo accounts and any future non-student logins get history too. The public id is a UUID
 * the client holds; the numeric id is internal.
 */
@Entity
@Table(
    name = "agent_conversation",
    indexes = [
        Index(name = "idx_agent_conversation_email_updated", columnList = "user_email, updated_at"),
        Index(name = "idx_agent_conversation_student", columnList = "student_id"),
        Index(name = "idx_agent_conversation_public_id", columnList = "conversation_id", unique = true)
    ]
)
class DMAgentConversation : BaseEntity() {
    @Column(name = "conversation_id", nullable = false, length = 36, unique = true)
    var conversationId: String = ""

    @Column(name = "user_email", nullable = false, length = 255)
    var userEmail: String = ""

    /**
     * The student this thread belongs to (joins `student.id`). Null only for a signed-in account
     * with no student row; demo logins carry the demo student's id, as everywhere else in the app.
     */
    @Column(name = "student_id")
    var studentId: Long? = null

    /** Denormalised roll number so analysis queries need no join. */
    @Column(name = "roll_number", length = 20)
    var rollNumber: String? = null

    /** First user message, trimmed, so a history list can label the thread. */
    @Column(name = "title", length = 200)
    var title: String? = null

    @Column(name = "message_count", nullable = false)
    var messageCount: Int = 0
}
