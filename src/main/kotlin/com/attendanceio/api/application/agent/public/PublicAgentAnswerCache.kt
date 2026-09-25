package com.attendanceio.api.application.agent.`public`

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.util.Collections
import java.util.Locale

/**
 * Finished answers for the public demo, keyed by the question itself.
 *
 * The page suggests eight questions before anyone types, so those eight are asked far more than
 * anything else — and published institute information (placements, programmes, faculty, clubs) is
 * the same for every visitor and the same all day. Answering them from memory removes the whole
 * turn rather than shaving tokens off it: no prefix, no tool loop, no output tokens, and the visitor
 * gets the answer immediately.
 *
 * Only the *opening* question of a thread is eligible, in both directions. A follow-up's answer
 * depends on what came before it, so it is neither served nor stored — the key is the question
 * alone, and two visitors who typed the same follow-up were not asking the same thing.
 *
 * Kept in memory on purpose, like [PublicAgentConversationMemory]: an anonymous visitor should not
 * leave a row behind, and losing the cache on a restart costs one real call per question. Nothing
 * here is required for answering — a miss is simply the behaviour that existed before it.
 */
@Component
class PublicAgentAnswerCache(
    @Value("\${app.agent.public.answer-cache.enabled:true}") private val enabled: Boolean,
    @Value("\${app.agent.public.answer-cache.ttl-hours:24}") private val ttlHours: Long,
    @Value("\${app.agent.public.answer-cache.max-entries:500}") private val maxEntries: Int
) {
    private val logger = LoggerFactory.getLogger(PublicAgentAnswerCache::class.java)

    private data class Entry(val answer: String, val storedAt: Instant)

    /** Normalised question -> the answer given for it. Access-ordered, so eviction drops the coldest. */
    private val entries = Collections.synchronizedMap(object : LinkedHashMap<String, Entry>(64, 0.75f, true) {})

    private val ttl: Duration get() = Duration.ofHours(ttlHours)

    /**
     * The answer already given for [question], or null to answer it for real. [firstTurn] is false
     * for a follow-up, which is never served from here.
     */
    fun lookup(question: String, firstTurn: Boolean): String? {
        if (!enabled || !firstTurn) return null
        val key = normalise(question) ?: return null
        val entry = synchronized(entries) {
            sweep()
            entries[key]
        } ?: return null
        logger.info("public=ANSWER_CACHE_HIT ageMin={} question={}", Duration.between(entry.storedAt, Instant.now()).toMinutes(), preview(key))
        return entry.answer
    }

    /**
     * Remembers [answer] as the reply to [question]. Called only for a turn that finished cleanly:
     * a failed tool leaves a caveat in the answer that should not outlive the failure.
     */
    fun store(question: String, answer: String, firstTurn: Boolean) {
        if (!enabled || !firstTurn) return
        if (answer.isBlank() || answer.length > MAX_ANSWER_CHARS) return
        val key = normalise(question) ?: return
        synchronized(entries) {
            sweep()
            entries[key] = Entry(answer, Instant.now())
            while (entries.size > maxEntries) {
                val coldest = entries.keys.firstOrNull() ?: break
                entries.remove(coldest)
            }
        }
        logger.info("public=ANSWER_CACHE_STORED entries={} question={}", entries.size, preview(key))
    }

    /**
     * The question reduced to what makes two askings the same one: case, surrounding punctuation and
     * the amount of whitespace are not part of it. Interior wording is left alone — "clubs at DAU"
     * and "clubs at DAU?" are one question, "which clubs" and "which faculty" are not.
     *
     * Null for anything not worth a slot: too short to be a real question (and so likely "hi", where
     * serving yesterday's reply would be odd), or long enough that nobody will type it twice.
     */
    private fun normalise(question: String): String? {
        val collapsed = question.trim().lowercase(Locale.ROOT).replace(WHITESPACE, " ")
        val stripped = collapsed.trim('"', '\'', '“', '”', '‘', '’', '?', '!', '.', ' ')
        return stripped.takeIf { it.length in MIN_QUESTION_CHARS..MAX_QUESTION_CHARS }
    }

    private fun sweep() {
        val now = Instant.now()
        entries.entries.removeIf { Duration.between(it.value.storedAt, now) > ttl }
    }

    private fun preview(key: String): String = key.take(60)

    private companion object {
        val WHITESPACE = Regex("\\s+")

        /** Below this a "question" is a greeting or a stray word; above it, a one-off nobody repeats. */
        const val MIN_QUESTION_CHARS = 8
        const val MAX_QUESTION_CHARS = 200

        /** Bounds what one entry can hold; a longer answer is served fresh rather than stored. */
        const val MAX_ANSWER_CHARS = 16_000
    }
}
