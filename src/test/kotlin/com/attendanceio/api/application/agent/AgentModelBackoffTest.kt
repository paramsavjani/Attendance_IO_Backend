package com.attendanceio.api.application.agent

import com.attendanceio.api.config.AgentProperties
import org.junit.jupiter.api.Test
import reactor.core.publisher.Flux
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AgentModelBackoffTest {
    private val backoff = AgentModelBackoff(AgentProperties(rateLimitRetries = 2, rateLimitBackoffMs = 10))

    @Test
    fun `provider throttling is recognised through the cause chain, other failures are not`() {
        assertTrue(backoff.isRetryable(RuntimeException("429 RESOURCE_EXHAUSTED. You exceeded your current quota")))
        assertTrue(backoff.isRetryable(RuntimeException("402 RESOURCE_EXHAUSTED. Your prepayment credits are depleted")))
        assertTrue(backoff.isRetryable(RuntimeException("503 UNAVAILABLE. This model is currently experiencing high demand")))
        assertTrue(backoff.isRetryable(IllegalStateException("wrapped", RuntimeException("Rate limit exceeded"))))
        assertFalse(backoff.isRetryable(RuntimeException("400 INVALID_ARGUMENT. Request payload too large")))
        assertFalse(backoff.isRetryable(NullPointerException()))
        assertEquals(AgentModelBackoff.BUSY_MESSAGE, backoff.describe(RuntimeException("429 quota")))
        assertTrue(backoff.describe(RuntimeException("boom")).contains("boom"))
    }

    @Test
    fun `blocking call retries throttling then succeeds, and gives up after the limit`() {
        val calls = AtomicInteger()
        val result = backoff.call("t1") { if (calls.incrementAndGet() < 3) throw RuntimeException("429 quota") else "ok" }
        assertEquals("ok", result)
        assertEquals(3, calls.get())

        val failing = AtomicInteger()
        val thrown = runCatching { backoff.call("t2") { failing.incrementAndGet(); throw RuntimeException("429 quota") } }.exceptionOrNull()
        assertTrue(thrown?.message?.contains("429") == true)
        assertEquals(3, failing.get()) // 1 + 2 retries

        val once = AtomicInteger()
        runCatching { backoff.call("t3") { once.incrementAndGet(); throw RuntimeException("400 bad request") } }
        assertEquals(1, once.get())
    }

    @Test
    fun `streamed call retries only while nothing has been sent to the client`() {
        val subscriptions = AtomicInteger()
        val flux = backoff.retrying("t4", nothingSentYet = { true }) {
            Flux.defer { if (subscriptions.incrementAndGet() < 2) Flux.error(RuntimeException("503 UNAVAILABLE")) else Flux.just("a", "b") }
        }
        assertEquals(listOf("a", "b"), flux.collectList().block())
        assertEquals(2, subscriptions.get())

        val sent = AtomicInteger()
        val noRetry = backoff.retrying("t5", nothingSentYet = { false }) { sent.incrementAndGet(); Flux.error<String>(RuntimeException("429 quota")) }
        val err = runCatching { noRetry.collectList().block() }.exceptionOrNull()
        assertTrue(err?.message?.contains("429 quota") == true, err?.toString())
        assertEquals(1, sent.get())
    }
}
