package com.attendanceio.api.application.agent

import com.attendanceio.api.config.AgentProperties
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * In-process [AgentConversationMemory]. This deployment has no Redis, and chat history is
 * short-lived by design, so a bounded map is enough: each thread is trimmed to
 * `app.agent.memory-max-messages`, expires `memory-ttl-hours` after its last message, and the
 * least recently used threads are dropped once `memory-max-threads` is exceeded. A restart
 * forgets everything, which is acceptable for a "continue the conversation" convenience — the
 * client just starts a new thread. Swap in a Redis/JPA implementation behind the interface if
 * that ever stops being acceptable.
 *
 * Key is `{email}:{conversationId}`: the email makes ownership structural — a caller can only
 * ever address threads under their own email.
 */
@Component
class InMemoryAgentConversationMemory(
    private val properties: AgentProperties
) : AgentConversationMemory {

    private class Thread(val messages: ArrayDeque<StoredAgentMessage> = ArrayDeque(), @Volatile var touchedAt: Instant = Instant.now())

    private val threads = ConcurrentHashMap<String, Thread>()

    override fun load(userEmail: String, conversationId: String, limit: Int): List<StoredAgentMessage> {
        if (limit <= 0) return emptyList()
        val all = loadAll(userEmail, conversationId)
        return all.takeLast(limit)
    }

    override fun loadAll(userEmail: String, conversationId: String): List<StoredAgentMessage> {
        val thread = threads[key(userEmail, conversationId)] ?: return emptyList()
        if (isExpired(thread)) {
            threads.remove(key(userEmail, conversationId))
            return emptyList()
        }
        return synchronized(thread) { thread.messages.toList() }
    }

    override fun append(userEmail: String, conversationId: String, messages: List<StoredAgentMessage>) {
        if (messages.isEmpty()) return
        val thread = threads.computeIfAbsent(key(userEmail, conversationId)) { Thread() }
        synchronized(thread) {
            if (isExpired(thread)) thread.messages.clear()
            thread.messages.addAll(messages)
            while (thread.messages.size > properties.memoryMaxMessages) thread.messages.removeFirst()
            thread.touchedAt = Instant.now()
        }
        evictIfNeeded()
    }

    private fun isExpired(thread: Thread): Boolean =
        Duration.between(thread.touchedAt, Instant.now()).toHours() >= properties.memoryTtlHours

    /** Drop expired threads first, then the least recently touched, until under the cap. */
    private fun evictIfNeeded() {
        if (threads.size <= properties.memoryMaxThreads) return
        threads.entries.removeIf { isExpired(it.value) }
        if (threads.size <= properties.memoryMaxThreads) return
        threads.entries
            .sortedBy { it.value.touchedAt }
            .take(threads.size - properties.memoryMaxThreads)
            .forEach { threads.remove(it.key) }
    }

    private fun key(userEmail: String, conversationId: String) = "${userEmail.trim().lowercase()}:$conversationId"
}
