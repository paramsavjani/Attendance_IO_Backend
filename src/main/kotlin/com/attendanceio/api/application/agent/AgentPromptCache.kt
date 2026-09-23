package com.attendanceio.api.application.agent

import tools.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.ai.tool.ToolCallback
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Keeps the fixed half of every model call — the system prompt and the tool declarations — parked
 * in Gemini's context cache instead of re-uploading it on every request.
 *
 * Roughly 90% of what this assistant sends is identical on every turn and every round of the tool
 * loop: the same instructions, the same tool schemas, for every student. Gemini bills a cached
 * prefix at a tenth of the normal input price, so the same traffic costs a fraction once the
 * prefix lives server-side. What goes in is only what is the same for everyone — never a student's
 * name, question, history or tool results; those travel with the request as usual.
 *
 * A cache is keyed by a hash of exactly what it contains, so editing the prompt or a tool
 * description simply creates a new one and the old expires by itself. Everything here is
 * best-effort: any failure returns null and the caller falls back to sending the prefix inline,
 * because a cache is a cost optimisation and never a requirement for answering.
 *
 * Uses Gemini's REST API directly rather than Spring AI's `GoogleGenAiCachedContentService`, whose
 * `CachedContentRequest` cannot carry tool declarations (model, contents and systemInstruction
 * only) — and the tools are the larger half of what we are trying to cache.
 */
@Component
class AgentPromptCache(
    @Value("\${app.agent.cache.enabled:true}") private val enabled: Boolean,
    @Value("\${app.agent.cache.ttl-minutes:15}") private val ttlMinutes: Long,
    @Value("\${app.agent.cache.min-tokens:1024}") private val minTokens: Int,
    @Value("\${spring.ai.google.genai.api-key:}") private val apiKey: String,
    @Value("\${spring.ai.google.genai.chat.options.model:}") private val model: String,
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(AgentPromptCache::class.java)

    private val httpClient: HttpClient by lazy {
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
    }

    /** key -> the cache Gemini is holding for it. */
    private val entries = ConcurrentHashMap<String, Entry>()

    /** One in-flight creation per key; a second turn with the same prefix waits rather than creating a twin. */
    private val locks = ConcurrentHashMap<String, Any>()

    data class Entry(val name: String, val tokens: Int, val expiresAt: Instant)

    fun isEnabled(): Boolean = enabled && apiKey.isNotBlank() && model.isNotBlank()

    /**
     * The cache holding [systemPrompt] plus [callbacks]' declarations, creating it on first use and
     * re-creating it once expired. Null means "not cached" — send the prefix inline as before.
     */
    fun nameFor(systemPrompt: String, callbacks: List<ToolCallback>): String? {
        if (!isEnabled() || callbacks.isEmpty()) return null
        val declarations = declarations(callbacks)
        val key = key(systemPrompt, declarations)

        live(key)?.let { return it }

        return synchronized(locks.computeIfAbsent(key) { Any() }) {
            // Another thread may have created it while this one waited on the lock.
            live(key) ?: build(key, systemPrompt, declarations, callbacks.size)
        }
    }

    private fun live(key: String): String? =
        entries[key]?.takeIf { Instant.now().isBefore(it.expiresAt.minusSeconds(RENEW_BEFORE_SECONDS)) }?.name

    private fun build(key: String, systemPrompt: String, declarations: List<Map<String, Any?>>, tools: Int): String? {
        val created = runCatching { create(systemPrompt, declarations) }
            .onFailure { logger.warn("agent=CACHE_CREATE_FAILED reason={}", it.message) }
            .getOrNull()
            ?: return null
        if (created.tokens < minTokens) {
            // Too small to be worth the storage it would be billed for; drop it and inline instead.
            logger.info("agent=CACHE_TOO_SMALL tokens={} min={}", created.tokens, minTokens)
            runCatching { delete(created.name) }
            return null
        }
        entries[key] = created
        logger.info(
            "agent=CACHE_CREATED name={} tokens={} tools={} ttlMin={}",
            created.name, created.tokens, tools, ttlMinutes
        )
        return created.name
    }

    /** Gemini's functionDeclarations: the same name/description/schema Spring AI would have sent inline. */
    private fun declarations(callbacks: List<ToolCallback>): List<Map<String, Any?>> =
        callbacks
            .sortedBy { it.toolDefinition.name() }
            .map { callback ->
                val schema = objectMapper.readValue(callback.toolDefinition.inputSchema(), MutableMap::class.java)
                // Gemini rejects JSON-Schema bookkeeping it does not implement.
                schema.remove("\$schema")
                schema.remove("additionalProperties")
                mapOf(
                    "name" to callback.toolDefinition.name(),
                    "description" to callback.toolDefinition.description(),
                    "parameters" to schema
                )
            }

    private fun key(systemPrompt: String, declarations: List<Map<String, Any?>>): String {
        val material = listOf(model, systemPrompt, objectMapper.writeValueAsString(declarations)).joinToString("\n--\n")
        return MessageDigest.getInstance("SHA-256").digest(material.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    private fun create(systemPrompt: String, declarations: List<Map<String, Any?>>): Entry {
        val body = objectMapper.writeValueAsBytes(
            mapOf(
                "model" to "models/$model",
                "displayName" to "attendance-io-agent",
                "systemInstruction" to mapOf("parts" to listOf(mapOf("text" to systemPrompt))),
                "tools" to listOf(mapOf("functionDeclarations" to declarations)),
                "ttl" to "${ttlMinutes * 60}s"
            )
        )
        val request = HttpRequest.newBuilder()
            .uri(URI.create("$BASE_URL/cachedContents?key=${URLEncoder.encode(apiKey, Charsets.UTF_8)}"))
            .timeout(Duration.ofSeconds(20))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofByteArray(body))
            .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        check(response.statusCode() in 200..299) { "HTTP ${response.statusCode()}: ${response.body().take(300)}" }
        val json = objectMapper.readTree(response.body())
        val name = json.path("name").asText("")
        check(name.isNotBlank()) { "cache response carried no name" }
        return Entry(
            name = name,
            tokens = json.path("usageMetadata").path("totalTokenCount").asInt(0),
            // Trust our own TTL rather than the returned expireTime: believing a dead cache is alive
            // costs a failed turn, while re-creating one early costs almost nothing.
            expiresAt = Instant.now().plusSeconds(ttlMinutes * 60)
        )
    }

    private fun delete(name: String) {
        val request = HttpRequest.newBuilder()
            .uri(URI.create("$BASE_URL/$name?key=${URLEncoder.encode(apiKey, Charsets.UTF_8)}"))
            .timeout(Duration.ofSeconds(10))
            .DELETE()
            .build()
        httpClient.send(request, HttpResponse.BodyHandlers.discarding())
    }

    /** Called when a turn fails against a cache, so the next turn builds a fresh one instead. */
    fun forget(name: String) {
        if (entries.entries.removeIf { it.value.name == name }) {
            logger.info("agent=CACHE_DROPPED name={}", name)
        }
    }

    private companion object {
        const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta"

        /** Re-create this long before the TTL runs out, so a turn never picks a cache that expires mid-flight. */
        const val RENEW_BEFORE_SECONDS = 120L
    }
}
