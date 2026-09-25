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
    private fun limiter(anonymous: Int = 3, signedIn: Int = 15, total: Int = 400, perAddress: Int = 0) =
        PublicAgentRateLimiter(anonymous, signedIn, total, perAddress)

    private fun anonymous(key: String = "ip:aaa") =
        PublicAgentIdentity.Visitor(key, key, PublicAgentIdentity.Tier.ANONYMOUS)

    private fun member(key: String = "g:bbb", address: String = "") =
        PublicAgentIdentity.Visitor(key, address, PublicAgentIdentity.Tier.SIGNED_IN, name = "A Visitor")

    @Test
    fun `a second Google account from the same network does not buy a second allowance`() {
        val limiter = limiter(signedIn = 5, perAddress = 8)
        // One person, two accounts, one address.
        repeat(5) { limiter.check(member("g:first", address = "ip:shared")) }
        repeat(3) { limiter.check(member("g:second", address = "ip:shared")) }

        val refused = runCatching { limiter.check(member("g:third", address = "ip:shared")) }.exceptionOrNull()
        assertTrue(refused is AgentDailyLimitExceededException, "the network's own ceiling should stop a fresh account")
        assertTrue(
            refused.message.orEmpty().contains("network", ignoreCase = true),
            "the refusal should say it is the network, not the account, got: ${refused.message}"
        )
    }

    @Test
    fun `another network is unaffected by one that has run out`() {
        val limiter = limiter(signedIn = 5, perAddress = 5)
        repeat(5) { limiter.check(member("g:first", address = "ip:one")) }

        limiter.check(member("g:second", address = "ip:two"))
        assertEquals(4, limiter.remaining(member("g:second", address = "ip:two")))
    }

    @Test
    fun `an anonymous visitor is counted once, not twice, for being their own address`() {
        val limiter = limiter(anonymous = 3, perAddress = 20)
        val visitor = anonymous("ip:solo")
        repeat(3) { limiter.check(visitor) }

        // Three questions asked; had they been double-counted the address would show six.
        assertEquals(0, limiter.remaining(visitor))
        assertTrue(runCatching { limiter.check(visitor) }.exceptionOrNull() is AgentDailyLimitExceededException)
    }

    @Test
    fun `remaining reports whichever ceiling is closer`() {
        val limiter = limiter(signedIn = 10, perAddress = 4)
        repeat(3) { limiter.check(member("g:first", address = "ip:shared")) }

        // Seven left on the account, one left on the network: the network decides.
        assertEquals(1, limiter.remaining(member("g:second", address = "ip:shared")))
    }

    @Test
    fun `a zero per-address limit leaves the account allowance alone`() {
        val limiter = limiter(signedIn = 2, perAddress = 0)
        limiter.check(member("g:first", address = "ip:shared"))
        limiter.check(member("g:second", address = "ip:shared"))
        limiter.check(member("g:third", address = "ip:shared"))

        assertEquals(2, limiter.remaining(member("g:fourth", address = "ip:shared")))
    }

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
