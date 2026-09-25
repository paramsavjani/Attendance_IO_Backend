package com.attendanceio.api.application.agent.actions

import com.attendanceio.api.application.agent.AgentCaller
import com.attendanceio.api.application.agent.AgentDailyLimitExceededException
import com.attendanceio.api.config.AgentProperties
import com.attendanceio.api.repository.agent.AgentConversationRepositoryAppAction
import org.springframework.stereotype.Component
import java.time.DayOfWeek
import java.time.LocalDate
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
    private val properties: AgentProperties
) {
    companion object {
        val ZONE: ZoneId = ZoneId.of("Asia/Kolkata")
    }

    fun execute(caller: AgentCaller) {
        if (caller.studentId != null && caller.studentId in properties.dailyLimitExemptStudentIds) return
        val today = LocalDate.now(ZONE)

        val daily = properties.dailyMessageLimit
        if (daily > 0) {
            val used = conversations.countUserMessagesSince(caller.email, today.atStartOfDay())
            if (used >= daily) throw AgentDailyLimitExceededException(daily, used)
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
}
