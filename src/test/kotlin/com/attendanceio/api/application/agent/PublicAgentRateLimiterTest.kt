package com.attendanceio.api.application.agent

import com.attendanceio.api.application.agent.`public`.PublicAgentIdentity
import com.attendanceio.api.application.agent.`public`.PublicAgentRateLimiter
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The two allowances are what stands between a link on LinkedIn and someone else's Gemini bill, so
 * the arithmetic is pinned: a few questions for anyone, more once they have signed in, and a ceiling
 * for the day that nothing gets past.
 */
class PublicAgentRateLimiterTest {
    private fun limiter(anonymous: Int = 3, signedIn: Int = 15, total: Int = 400) =
        PublicAgentRateLimiter(anonymous, signedIn, total)

    private fun anonymous(key: String = "ip:aaa") =
        PublicAgentIdentity.Visitor(key, PublicAgentIdentity.Tier.ANONYMOUS)

    private fun member(key: String = "g:bbb") =
        PublicAgentIdentity.Visitor(key, PublicAgentIdentity.Tier.SIGNED_IN, name = "A Visitor")

    @Test
    fun `a visitor gets their free questions, then is asked to sign in`() {
        val limiter = limiter(anonymous = 3)
        val visitor = anonymous()
        repeat(3) { limiter.check(visitor) }

        val refused = runCatching { limiter.check(visitor) }.exceptionOrNull()
        assertTrue(refused is AgentDailyLimitExceededException)
        assertTrue(
            refused.message.orEmpty().contains("Sign in with Google"),
            "the refusal is what the page turns into its sign-in prompt, got: ${refused.message}"
        )
    }

    @Test
    fun `signing in buys a bigger allowance, counted separately`() {
        val limiter = limiter(anonymous = 3, signedIn = 15)
        val visitor = member()
        repeat(15) { limiter.check(visitor) }
        assertEquals(0, limiter.remaining(visitor))
        assertTrue(runCatching { limiter.check(visitor) }.isFailure)
        // The same person before signing in is a different key, and still has their free questions.
        assertEquals(3, limiter.remaining(anonymous()))
    }

    @Test
    fun `one visitor cannot spend another's allowance`() {
        val limiter = limiter(anonymous = 3)
        repeat(3) { limiter.check(anonymous("ip:first")) }
        assertEquals(0, limiter.remaining(anonymous("ip:first")))
        assertEquals(3, limiter.remaining(anonymous("ip:second")))
    }

    @Test
    fun `the day's ceiling stops everyone, however many accounts they have`() {
        val limiter = limiter(anonymous = 5, total = 6)
        repeat(3) { limiter.check(anonymous("ip:a")) }
        repeat(3) { limiter.check(member("g:b")) }

        val refused = runCatching { limiter.check(member("g:brand-new")) }.exceptionOrNull()
        assertTrue(refused is AgentDailyLimitExceededException, "the daily ceiling is the real cap on the bill")
        assertEquals(0, limiter.remaining(member("g:brand-new")))
    }

    @Test
    fun `asking how many are left does not use one up`() {
        val limiter = limiter(anonymous = 3)
        val visitor = anonymous()
        assertEquals(3, limiter.remaining(visitor))
        assertEquals(3, limiter.remaining(visitor))
        limiter.check(visitor)
        assertEquals(2, limiter.remaining(visitor))
    }
}
