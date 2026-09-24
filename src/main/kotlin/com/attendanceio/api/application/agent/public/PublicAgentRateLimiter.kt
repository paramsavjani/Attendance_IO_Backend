package com.attendanceio.api.application.agent.`public`

import com.attendanceio.api.application.agent.AgentDailyLimitExceededException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Keeps the demo from becoming an open tab on someone else's Gemini bill.
 *
 * Every answer costs real money (about ten paise), and a link on LinkedIn can bring a few thousand
 * people or one person with a script. Two ceilings, both per day and both reset at midnight: one per
 * visitor so nobody can sit on it, and one overall so the worst case is a known number rather than a
 * surprise. Counters live in memory — a restart forgives everyone, which is the right way to be
 * wrong for something whose only job is capping a bill.
 */
@Component
class PublicAgentRateLimiter(
    @Value("\${app.agent.public.daily-limit-per-visitor:10}") private val perVisitor: Int,
    @Value("\${app.agent.public.daily-limit-total:400}") private val total: Int
) {
    private val logger = LoggerFactory.getLogger(PublicAgentRateLimiter::class.java)

    private val visitors = ConcurrentHashMap<String, AtomicInteger>()
    private val overall = AtomicInteger(0)

    @Volatile
    private var day: LocalDate = LocalDate.now()

    /** Counts one answer against [fingerprint], or refuses with the message the UI shows verbatim. */
    fun check(fingerprint: String) {
        rollOver()
        if (overall.get() >= total) {
            logger.info("public=LIMIT_TOTAL used={} limit={}", overall.get(), total)
            throw AgentDailyLimitExceededException(
                limit = total,
                used = overall.get().toLong(),
                message = "Attendance IO AI has answered its limit of questions for today. It resets tomorrow."
            )
        }
        val used = visitors.computeIfAbsent(fingerprint) { AtomicInteger(0) }.incrementAndGet()
        if (used > perVisitor) {
            logger.info("public=LIMIT_VISITOR fingerprint={} used={} limit={}", fingerprint, used, perVisitor)
            throw AgentDailyLimitExceededException(
                limit = perVisitor,
                used = used.toLong(),
                message = "That is $perVisitor questions today — the cap keeps this free to run. It resets tomorrow."
            )
        }
        overall.incrementAndGet()
    }

    fun snapshot(): Snapshot = Snapshot(overall.get(), total, perVisitor, visitors.size)

    data class Snapshot(val askedToday: Int, val dailyLimit: Int, val perVisitorLimit: Int, val visitorsToday: Int)

    private fun rollOver() {
        val today = LocalDate.now()
        if (today != day) {
            synchronized(this) {
                if (today != day) {
                    day = today
                    visitors.clear()
                    overall.set(0)
                    logger.info("public=LIMITS_RESET day={}", today)
                }
            }
        }
    }
}
