package com.attendanceio.api.application.agent

import com.attendanceio.api.config.AgentProperties
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.util.retry.Retry
import java.time.Duration

/**
 * Retries a model call that failed for a reason that will clear on its own — rate limit (429),
 * exhausted quota (402/429 RESOURCE_EXHAUSTED) or the provider being overloaded (503) — with
 * exponential backoff, and turns the final failure into a message a student can act on instead
 * of a stack-trace fragment. Everything else fails fast as before.
 */
@Component
class AgentModelBackoff(
    private val properties: AgentProperties
) {
    private val logger = LoggerFactory.getLogger(AgentModelBackoff::class.java)

    companion object {
        const val BUSY_MESSAGE = "The assistant is busy right now — too many people are asking at once. Please try again in a minute."
        private val RETRYABLE = Regex("(?i)\\b(429|402|503)\\b|RESOURCE_EXHAUSTED|UNAVAILABLE|rate.?limit|quota|overloaded|high demand|prepayment")
    }

    /** True when the failure is the provider throttling or overloaded rather than a bug in the request. */
    fun isRetryable(e: Throwable): Boolean =
        generateSequence(e) { it.cause?.takeIf { c -> c !== it } }.take(6).any { RETRYABLE.containsMatchIn(it.message ?: "") || RETRYABLE.containsMatchIn(it.javaClass.simpleName) }

    /** The text to show the user for a failure, friendly for throttling and unchanged otherwise. */
    fun describe(e: Throwable): String = if (isRetryable(e)) BUSY_MESSAGE else "The assistant could not complete this request: ${e.message ?: e.javaClass.simpleName}"

    /** Blocking retry loop for the non-streaming call. */
    fun <T> call(turnId: String, block: () -> T): T {
        var attempt = 0
        while (true) {
            try {
                return block()
            } catch (e: Exception) {
                if (!isRetryable(e) || attempt >= properties.rateLimitRetries) throw e
                val wait = properties.rateLimitBackoffMs shl attempt
                attempt++
                logger.warn("agent=MODEL_BACKOFF turnId={} attempt={} waitMs={} reason={}", turnId, attempt, wait, e.message?.take(120))
                Thread.sleep(wait)
            }
        }
    }

    /**
     * Reactor retry for the streamed call. [nothingSentYet] guards against re-running a turn whose
     * tokens have already reached the client, which would duplicate the answer.
     */
    fun <T : Any> retrying(turnId: String, nothingSentYet: () -> Boolean, source: () -> Flux<T>): Flux<T> =
        Flux.defer(source).retryWhen(
            Retry.backoff(properties.rateLimitRetries.toLong(), Duration.ofMillis(properties.rateLimitBackoffMs))
                .maxBackoff(Duration.ofSeconds(20))
                .filter { e -> isRetryable(e) && nothingSentYet() }
                .doBeforeRetry { s -> logger.warn("agent=MODEL_BACKOFF turnId={} attempt={} reason={}", turnId, s.totalRetries() + 1, s.failure().message?.take(120)) }
                .onRetryExhaustedThrow { _, s -> s.failure() }
        )
}
