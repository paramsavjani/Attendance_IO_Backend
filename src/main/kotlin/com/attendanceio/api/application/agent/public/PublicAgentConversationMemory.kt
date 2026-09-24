package com.attendanceio.api.application.agent.`public`

import com.attendanceio.api.application.agent.AgentCaller
import com.attendanceio.api.application.agent.AgentConversationMemory
import com.attendanceio.api.application.agent.StoredAgentMessage
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.util.Collections

/**
 * Short-term memory for demo conversations, so a visitor can ask a follow-up ("and who runs it?")
 * without repeating themselves.
 *
 * Deliberately not the database the app uses: an anonymous visitor should not leave a row behind,
 * and a public link should not be able to grow a table. Threads live in memory, expire an hour after
 * their last message, and the oldest are dropped once there are too many. Losing one only costs the
 * visitor the thread they were in.
 */
@Component
class PublicAgentConversationMemory : AgentConversationMemory {
    private data class Thread(val owner: String, val messages: MutableList<StoredAgentMessage>, var touched: Instant)

    private val threads = Collections.synchronizedMap(LinkedHashMap<String, Thread>())

    override fun load(userEmail: String, conversationId: String, limit: Int): List<StoredAgentMessage> =
        synchronized(threads) {
            sweep()
            val thread = threads[conversationId] ?: return emptyList()
            // A conversation id from someone else's browser reads as empty, as in the app.
            if (thread.owner != userEmail) return emptyList()
            thread.messages.takeLast(limit)
        }

    override fun append(owner: AgentCaller, conversationId: String, messages: List<StoredAgentMessage>) {
        synchronized(threads) {
            sweep()
            val thread = threads.getOrPut(conversationId) { Thread(owner.email, mutableListOf(), Instant.now()) }
            if (thread.owner != owner.email) return
            thread.messages.addAll(messages)
            // Only the recent tail is ever replayed; the rest is dead weight.
            while (thread.messages.size > MAX_MESSAGES) thread.messages.removeFirst()
            thread.touched = Instant.now()
            // Re-insert so iteration order is least-recently-used first.
            threads.remove(conversationId)
            threads[conversationId] = thread
        }
    }

    private fun sweep() {
        val now = Instant.now()
        threads.entries.removeIf { Duration.between(it.value.touched, now) > TTL }
        while (threads.size > MAX_THREADS) {
            val oldest = threads.keys.firstOrNull() ?: break
            threads.remove(oldest)
        }
    }

    private companion object {
        val TTL: Duration = Duration.ofHours(1)
        const val MAX_THREADS = 500
        const val MAX_MESSAGES = 20
    }
}
