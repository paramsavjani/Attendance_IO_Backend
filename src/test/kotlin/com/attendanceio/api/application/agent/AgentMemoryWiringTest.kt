package com.attendanceio.api.application.agent

import com.attendanceio.api.application.agent.`public`.PublicAgentConversationMemory
import org.junit.jupiter.api.Test
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Component
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * There are two conversation memories — the database one for students, an in-memory one for the
 * public demo — and something has to say which is meant when a bean of the interface type is asked
 * for. Nothing did once, and the whole app refused to start: "required a single bean, but 2 were
 * found". That failure only shows up when the context loads, so the rule is pinned here instead.
 */
class AgentMemoryWiringTest {
    @Test
    fun `the database memory is the primary one`() {
        assertNotNull(
            JpaAgentConversationMemory::class.java.getAnnotation(Primary::class.java),
            "JpaAgentConversationMemory must be @Primary, or injecting AgentConversationMemory is ambiguous"
        )
        assertNull(
            PublicAgentConversationMemory::class.java.getAnnotation(Primary::class.java),
            "only one implementation may be primary"
        )
    }

    @Test
    fun `both memories are still beans, and both implement the interface`() {
        listOf(JpaAgentConversationMemory::class.java, PublicAgentConversationMemory::class.java).forEach {
            assertNotNull(it.getAnnotation(Component::class.java), "${it.simpleName} should be a @Component")
            assertEquals(true, AgentConversationMemory::class.java.isAssignableFrom(it))
        }
    }
}
