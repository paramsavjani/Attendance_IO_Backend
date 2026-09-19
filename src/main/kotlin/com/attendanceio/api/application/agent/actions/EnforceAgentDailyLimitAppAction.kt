package com.attendanceio.api.application.agent.actions

import com.attendanceio.api.application.agent.AgentCaller
import com.attendanceio.api.application.agent.AgentDailyLimitExceededException
import com.attendanceio.api.config.AgentProperties
import com.attendanceio.api.repository.agent.AgentConversationRepositoryAppAction
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.ZoneId

/**
 * Per-user daily quota, counted from the messages already stored for this email (every
 * successful turn stores one USER row) — so no extra table or cache, and a restart cannot
 * reset anyone's count. The day rolls over at midnight IST, which is also the JVM zone the
 * timestamps are written in.
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
        val limit = properties.dailyMessageLimit
        if (limit <= 0) return
        if (caller.studentId != null && caller.studentId in properties.dailyLimitExemptStudentIds) return
        val since = LocalDate.now(ZONE).atStartOfDay()
        val used = conversations.countUserMessagesSince(caller.email, since)
        if (used >= limit) throw AgentDailyLimitExceededException(limit, used)
    }
}
