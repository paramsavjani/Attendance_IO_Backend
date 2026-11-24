package com.attendanceio.api.application.agent

import org.slf4j.LoggerFactory
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.prompt.ChatOptions
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.ai.google.genai.GoogleGenAiChatOptions
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Keeps the assistant answering when one Gemini model is overloaded. A model call that fails with a
 * throttling/overload error, or produces nothing within [firstChunk], is re-run on the next model in
 * [fallbacks] before anything reached the caller. A model that failed is tried last for [cooldown], so
 * the turns after it don't each wait for the same failure.
 *
 * It wraps single model calls, below the tool loop, so falling over never runs a tool twice. A call that
 * points at a context cache stays on its own model (the cache holds the prompt and tools for that model
 * only); the turn retries it without the cache.
 */
class FailoverChatModel(
    private val delegate: ChatModel,
    private val fallbacks: List<String>,
    private val firstChunk: Duration,
    private val cooldown: Duration,
    private val isRetryable: (Throwable) -> Boolean
) : ChatModel {
    private val logger = LoggerFactory.getLogger(FailoverChatModel::class.java)
    private val failedUntil = ConcurrentHashMap<String, Instant>()

    /** A model that sent nothing in time. Worded so it counts as the provider being overloaded. */
    class ModelUnresponsiveException(model: String, waited: Duration) :
        RuntimeException("503 UNAVAILABLE: $model sent nothing in ${waited.toSeconds()}s")

    override fun getOptions(): ChatOptions = delegate.options

    /** True while the configured model is being avoided. */
    fun primaryIsFailing(): Boolean = delegate.options.model?.let(::cooling) ?: false

    override fun call(prompt: Prompt): ChatResponse {
        val models = candidates(prompt)
        for ((i, model) in models.withIndex()) {
            try {
                return delegate.call(withModel(prompt, model))
            } catch (e: Exception) {
                if (!isRetryable(e)) throw e
                markFailed(model, e)
                if (i == models.lastIndex) throw e
            }
        }
        error("no model to call")
    }

    override fun stream(prompt: Prompt): Flux<ChatResponse> = Flux.defer { attempt(prompt, candidates(prompt), 0) }

    private fun attempt(prompt: Prompt, models: List<String>, i: Int): Flux<ChatResponse> {
        val model = models[i]
        val last = i == models.lastIndex
        // The last model gets longer: there is nothing left to switch to.
        val wait = if (last) firstChunk.multipliedBy(2) else firstChunk
        val started = AtomicBoolean(false)
        return Flux.defer { delegate.stream(withModel(prompt, model)) }
            // The client blocks while connecting; on its own thread the timeout can still fire.
            .subscribeOn(Schedulers.boundedElastic())
            .timeout(Mono.delay(wait))
            .onErrorMap(TimeoutException::class.java) { ModelUnresponsiveException(model, wait) }
            .doOnNext { started.set(true) }
            .onErrorResume { e ->
                if (started.get() || !isRetryable(e)) return@onErrorResume Flux.error(e)
                markFailed(model, e)
                if (last) Flux.error(e) else attempt(prompt, models, i + 1)
            }
    }

    /** The prompt's model then the fallbacks, with any that failed lately moved to the end. */
    internal fun candidates(prompt: Prompt): List<String> {
        val options = prompt.options ?: delegate.options
        val primary = options.model ?: delegate.options.model ?: return fallbacks
        if ((options as? GoogleGenAiChatOptions)?.cachedContentName != null) return listOf(primary)
        val (failing, healthy) = (listOf(primary) + fallbacks).distinct().partition(::cooling)
        return healthy + failing
    }

    private fun cooling(model: String): Boolean = failedUntil[model]?.isAfter(Instant.now()) ?: false

    private fun markFailed(model: String, e: Throwable) {
        failedUntil[model] = Instant.now().plus(cooldown)
        logger.warn("agent=MODEL_FAILOVER model={} avoidedForSec={} reason={}", model, cooldown.toSeconds(), (e.cause ?: e).message?.take(160))
    }

    private fun withModel(prompt: Prompt, model: String): Prompt {
        val options = prompt.options ?: delegate.options
        if (options.model == model) return prompt
        return Prompt(prompt.instructions, options.mutate().model(model).build())
    }
}
