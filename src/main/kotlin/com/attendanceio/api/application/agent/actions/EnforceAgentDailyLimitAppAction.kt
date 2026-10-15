package com.attendanceio.api.application.agent.actions

import com.attendanceio.api.application.agent.AgentCaller
import com.attendanceio.api.application.agent.AgentDailyLimitExceededException
import com.attendanceio.api.application.agent.AgentSubscriptionOffer
import com.attendanceio.api.config.AgentProperties
import com.attendanceio.api.repository.agent.AgentConversationRepositoryAppAction
import com.attendanceio.api.repository.agent.AgentSubscriptionInterestRepositoryAppAction
import org.springframework.stereotype.Component
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * Per-user quotas, daily and weekly, counted from the messages already stored for this email (every
 * successful turn stores one USER row) — so no extra table or cache, and a restart cannot reset
 * anyone's count. Both windows roll over at midnight IST, which is also the JVM zone the timestamps
 * are written in; the week runs Monday to Sunday, so it resets as Sunday night becomes Monday.
 *
 * The daily cap is what someone runs into on a heavy evening. The weekly one is the cap that decides
 * the bill, because seven ordinary days cost more than one heavy one, and it is checked second so
 * that whichever is actually exhausted is the one the person is told about.
 */
@Component
class EnforceAgentDailyLimitAppAction(
    private val conversations: AgentConversationRepositoryAppAction,
    private val interest: AgentSubscriptionInterestRepositoryAppAction,
    private val properties: AgentProperties
) {
    companion object {
        val ZONE: ZoneId = ZoneId.of("Asia/Kolkata")
    }

    /**
     * [message] is the question being sent: over the daily limit, a plain yes/no to today's
     * subscription offer is stored as the answer instead of repeating the offer.
     */
    fun execute(caller: AgentCaller, message: String? = null) {
        if (caller.studentId != null && caller.studentId in properties.dailyLimitExemptStudentIds) return
        val today = LocalDate.now(ZONE)

        val daily = properties.dailyMessageLimit
        if (daily > 0) {
            val used = conversations.countUserMessagesSince(caller.email, today.atStartOfDay())
            if (used >= daily) throw dailyLimitReached(caller, message, daily, used, today)
        }

        val weekly = properties.weeklyMessageLimit
        if (weekly > 0) {
            val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            val used = conversations.countUserMessagesSince(caller.email, monday.atStartOfDay())
            if (used >= weekly) {
                throw AgentDailyLimitExceededException(
                    limit = weekly,
                    used = used,
                    message = "You've used all $weekly messages for this week. They reset at 12 midnight on Sunday night."
                )
            }
        }
    }

    /**
     * The daily limit's message, which doubles as the paid-plan question. A demo login shares one
     * account between many people, so it sees the question but nothing it answers is stored.
     */
    private fun dailyLimitReached(caller: AgentCaller, message: String?, limit: Int, used: Long, today: LocalDate): AgentDailyLimitExceededException {
        val now = LocalDateTime.now(ZONE)
        val answer = message?.let(AgentSubscriptionOffer::parseAnswer)
        val offeredToday = interest.find(caller.email)?.lastOfferedAt?.let { !it.isBefore(today.atStartOfDay()) } == true
        if (answer != null && (offeredToday || caller.isDemo)) {
            if (!caller.isDemo) interest.recordAnswer(caller, answer, AgentSubscriptionOffer.PRICE_RUPEES, now)
            return AgentDailyLimitExceededException(limit, used, AgentSubscriptionOffer.thanks(answer))
        }
        if (!caller.isDemo) interest.recordOffer(caller, AgentSubscriptionOffer.PRICE_RUPEES, now)
        return AgentDailyLimitExceededException(limit, used, AgentSubscriptionOffer.limitMessage(limit), subscriptionOffer = true)
    }
}
