package com.attendanceio.api.application.agent

import com.attendanceio.api.application.agent.actions.EnforceAgentDailyLimitAppAction
import com.attendanceio.api.config.AgentProperties
import com.attendanceio.api.repository.agent.AgentConversationRepository
import com.attendanceio.api.repository.agent.AgentConversationRepositoryAppAction
import com.attendanceio.api.repository.agent.AgentMessageRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EnforceAgentDailyLimitAppActionTest {
    private val caller = AgentCaller(email = "s@daiict.ac.in", studentId = 1, name = "S", rollNumber = "202301001", isDemo = false)

    /** Returns a fixed count and remembers what it was asked. */
    private class FakeRepo(private val used: Long) : AgentConversationRepositoryAppAction(
        Mockito.mock(AgentConversationRepository::class.java),
        Mockito.mock(AgentMessageRepository::class.java)
    ) {
        var askedEmail: String? = null
        var askedSince: LocalDateTime? = null
        override fun countUserMessagesSince(email: String, since: LocalDateTime): Long {
            askedEmail = email; askedSince = since; return used
        }
    }

    @Test
    fun `under the limit passes, at the limit is refused with a friendly message`() {
        EnforceAgentDailyLimitAppAction(FakeRepo(19), AgentProperties(dailyMessageLimit = 20)).execute(caller)
        val e = assertThrows<AgentDailyLimitExceededException> {
            EnforceAgentDailyLimitAppAction(FakeRepo(20), AgentProperties(dailyMessageLimit = 20)).execute(caller)
        }
        assertEquals(20, e.limit)
        assertTrue(e.message!!.contains("20 messages") && e.message!!.contains("midnight"), e.message)
    }

    @Test
    fun `zero disables the limit and the window starts at midnight IST`() {
        val unlimited = FakeRepo(1_000)
        EnforceAgentDailyLimitAppAction(unlimited, AgentProperties(dailyMessageLimit = 0)).execute(caller)
        assertEquals(null, unlimited.askedEmail, "no query when disabled")

        val repo = FakeRepo(0)
        EnforceAgentDailyLimitAppAction(repo, AgentProperties()).execute(caller)
        assertEquals(caller.email, repo.askedEmail)
        assertEquals(LocalDate.now(EnforceAgentDailyLimitAppAction.ZONE).atStartOfDay(), repo.askedSince)
    }
}
