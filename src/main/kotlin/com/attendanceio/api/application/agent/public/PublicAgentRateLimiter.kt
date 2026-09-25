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
    /** Questions someone gets before the page asks them to sign in. */
    @Value("\${app.agent.public.daily-limit-anonymous:3}") private val anonymousLimit: Int,
    /** Questions a signed-in Google account gets per day. */
    @Value("\${app.agent.public.daily-limit-signed-in:10}") private val signedInLimit: Int,
    @Value("\${app.agent.public.daily-limit-total:400}") private val total: Int,
    /**
     * Questions one network gets per day, whatever it signs in as. Without this the signed-in
     * allowance is per Google account, and Google accounts are free — so a second one buys a second
     * allowance and the cap means nothing to anyone willing to spend a minute on it.
     *
     * Set above the per-account allowance on purpose: a hostel or a campus can put many genuine
     * people behind one address, and refusing the second of them would be a worse failure than
     * letting someone farm a few accounts. 0 removes the ceiling.
     */
    @Value("\${app.agent.public.daily-limit-per-address:20}") private val addressLimit: Int
) {
    private val logger = LoggerFactory.getLogger(PublicAgentRateLimiter::class.java)

    private val visitors = ConcurrentHashMap<String, AtomicInteger>()
    private val overall = AtomicInteger(0)

    @Volatile
    private var day: LocalDate = LocalDate.now()

    /** Counts one answer against [visitor], or refuses with the message the page shows verbatim. */
    fun check(visitor: PublicAgentIdentity.Visitor) {
        rollOver()
        if (overall.get() >= total) {
            logger.info("public=LIMIT_TOTAL used={} limit={}", overall.get(), total)
            throw AgentDailyLimitExceededException(
                limit = total,
                used = overall.get().toLong(),
                message = "The demo has answered its limit of questions for today. It resets tomorrow — " +
                    "or ask Param for a walkthrough of the real app."
            )
        }
        // Counted against the network as well as the account, unless they are the same string —
        // an anonymous visitor is already counted by address and must not be charged twice.
        if (addressLimit > 0 && visitor.addressKey.isNotBlank() && visitor.addressKey != visitor.key) {
            val fromAddress = visitors.computeIfAbsent(visitor.addressKey) { AtomicInteger(0) }.incrementAndGet()
            if (fromAddress > addressLimit) {
                logger.info("public=LIMIT_ADDRESS key={} used={} limit={}", visitor.addressKey, fromAddress, addressLimit)
                throw AgentDailyLimitExceededException(
                    limit = addressLimit,
                    used = fromAddress.toLong(),
                    message = "This network has used the demo's questions for today. They reset at midnight."
                )
            }
        }

        val limit = limitFor(visitor)
        val used = visitors.computeIfAbsent(visitor.key) { AtomicInteger(0) }.incrementAndGet()
        if (used > limit) {
            logger.info("public=LIMIT_VISITOR key={} tier={} used={} limit={}", visitor.key, visitor.tier, used, limit)
            throw AgentDailyLimitExceededException(
                limit = limit,
                used = used.toLong(),
                message = if (visitor.signedIn) {
                    "That is $limit questions today — the cap keeps this free to run. It resets tomorrow."
                } else {
                    // The page turns this into the sign-in prompt, so it has to say why.
                    "Sign in with Google to keep asking — it keeps one person from using up the day's questions."
                }
            )
        }
        overall.incrementAndGet()
    }

    fun limitFor(visitor: PublicAgentIdentity.Visitor): Int =
        if (visitor.signedIn) signedInLimit else anonymousLimit

    /**
     * Questions [visitor] has left today, without counting this look as one of them. Whichever of
     * the two ceilings is closer is the one that decides, so the page never promises questions the
     * next request would refuse.
     */
    fun remaining(visitor: PublicAgentIdentity.Visitor): Int {
        rollOver()
        if (overall.get() >= total) return 0
        val used = visitors[visitor.key]?.get() ?: 0
        val ownLeft = (limitFor(visitor) - used).coerceAtLeast(0)
        if (addressLimit <= 0 || visitor.addressKey.isBlank() || visitor.addressKey == visitor.key) return ownLeft
        val fromAddress = visitors[visitor.addressKey]?.get() ?: 0
        return minOf(ownLeft, (addressLimit - fromAddress).coerceAtLeast(0))
    }

    fun snapshot(): Snapshot = Snapshot(overall.get(), total, anonymousLimit, signedInLimit, visitors.size)

    data class Snapshot(
        val askedToday: Int,
        val dailyLimit: Int,
        val anonymousLimit: Int,
        val signedInLimit: Int,
        val visitorsToday: Int
    )

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
