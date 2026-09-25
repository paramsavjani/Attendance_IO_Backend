package com.attendanceio.api.application.agent

import com.attendanceio.api.application.agent.`public`.PublicAgentGoogleVerifier
import com.attendanceio.api.application.agent.`public`.PublicAgentIdentity
import com.attendanceio.api.application.agent.`public`.PublicAgentRateLimiter
import com.attendanceio.api.application.agent.actions.ChatWithAgentAppAction
import com.attendanceio.api.controller.agent.PublicAgentChatController
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.mock.web.MockHttpServletRequest
import tools.jackson.databind.ObjectMapper
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * What `/info` tells the page before anyone types. The page decides what to say about access from
 * this one response, so the two shapes of it are pinned: a student is told they have their own data,
 * and everybody else is not told something that is not true of them.
 */
class PublicAgentInfoTest {
    // Stubbed on the request instance rather than a matcher: Mockito's any() hands Kotlin a null,
    // which a non-null parameter type rejects before the stub is ever recorded.
    private val request = MockHttpServletRequest()

    private fun controller(visitor: PublicAgentIdentity.Visitor): PublicAgentChatController {
        val identities = Mockito.mock(PublicAgentIdentity::class.java)
        Mockito.`when`(identities.resolve(request)).thenReturn(visitor)
        val limiter = PublicAgentRateLimiter(3, 10, 400, 20)
        val verifier = Mockito.mock(PublicAgentGoogleVerifier::class.java)
        Mockito.`when`(verifier.clientId()).thenReturn("client-id.apps.googleusercontent.com")
        return PublicAgentChatController(
            ObjectMapper(),
            Mockito.mock(ChatWithAgentAppAction::class.java),
            limiter,
            identities,
            verifier
        )
    }

    private fun visitor(tier: PublicAgentIdentity.Tier) = PublicAgentIdentity.Visitor(
        key = "g:abc",
        addressKey = "ip:abc",
        tier = tier,
        name = "A Person",
        student = if (tier == PublicAgentIdentity.Tier.STUDENT) {
            PublicAgentIdentity.Student("student@daiict.ac.in", 790L, "A Student", "202301001")
        } else {
            null
        }
    )

    @Suppress("UNCHECKED_CAST")
    private fun suggestionsOf(info: Map<String, Any?>) = info["suggestions"] as List<String>

    @Test
    fun `a student is told they have their own data, and offered questions about it`() {
        val info = controller(visitor(PublicAgentIdentity.Tier.STUDENT)).info(request)

        assertEquals(true, info["fullAccess"])
        val suggestions = suggestionsOf(info)
        assertTrue(
            suggestions.any { it.contains("my attendance", ignoreCase = true) },
            "a student should be offered their own attendance, got: $suggestions"
        )
        assertEquals(12, suggestions.size)
    }

    @Test
    fun `a personal account is offered the public questions and told nothing more`() {
        val info = controller(visitor(PublicAgentIdentity.Tier.SIGNED_IN)).info(request)

        assertEquals(false, info["fullAccess"])
        assertEquals(true, info["signedIn"])
        val suggestions = suggestionsOf(info)
        assertFalse(
            suggestions.any { it.contains(" my ", ignoreCase = true) },
            "nothing personal should be suggested to someone who cannot ask it, got: $suggestions"
        )
        assertEquals(12, suggestions.size)
    }

    @Test
    fun `an anonymous visitor is neither signed in nor privileged`() {
        val info = controller(visitor(PublicAgentIdentity.Tier.ANONYMOUS)).info(request)

        assertEquals(false, info["fullAccess"])
        assertEquals(false, info["signedIn"])
        assertEquals(3, info["limit"])
    }
}
