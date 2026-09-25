package com.attendanceio.api.application.agent

import com.attendanceio.api.application.agent.actions.EnforceAgentDailyLimitAppAction
import com.attendanceio.api.config.AgentProperties
import com.attendanceio.api.repository.agent.AgentConversationRepository
import com.attendanceio.api.repository.agent.AgentConversationRepositoryAppAction
import com.attendanceio.api.repository.agent.AgentMessageRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.TemporalAdjusters
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EnforceAgentDailyLimitAppActionTest {
    private val caller = AgentCaller(email = "s@daiict.ac.in", studentId = 1, name = "S", rollNumber = "202301001", isDemo = false)

    /** Returns a fixed count and remembers every window it was asked about. */
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

    // Each test disables the window it is not about. On a Monday the two windows begin at the same
    // instant, so a test that left both on would pass or fail depending on the day it ran.
    private fun dailyOnly(limit: Int) = AgentProperties(dailyMessageLimit = limit, weeklyMessageLimit = 0)
    private fun weeklyOnly(limit: Int) = AgentProperties(dailyMessageLimit = 0, weeklyMessageLimit = limit)

    @Test
    fun `under the daily limit passes, at it is refused with a friendly message`() {
        EnforceAgentDailyLimitAppAction(FakeRepo(9), dailyOnly(10)).execute(caller)
        val e = assertThrows<AgentDailyLimitExceededException> {
            EnforceAgentDailyLimitAppAction(FakeRepo(10), dailyOnly(10)).execute(caller)
        }
        assertEquals(10, e.limit)
        assertTrue(e.message!!.contains("10 messages") && e.message!!.contains("midnight"), e.message)
    }

    @Test
    fun `the weekly limit refuses with its own message and its own reset`() {
        EnforceAgentDailyLimitAppAction(FakeRepo(29), weeklyOnly(30)).execute(caller)
        val e = assertThrows<AgentDailyLimitExceededException> {
            EnforceAgentDailyLimitAppAction(FakeRepo(30), weeklyOnly(30)).execute(caller)
        }
        assertEquals(30, e.limit)
        assertTrue(e.message!!.contains("30 messages"), e.message)
        assertTrue(e.message!!.contains("week"), "it should say the week ran out, not the day: ${e.message}")
        assertTrue(e.message!!.contains("Sunday"), "it should name when it resets: ${e.message}")
    }

    @Test
    fun `the weekly window starts on Monday at midnight IST`() {
        val repo = FakeRepo(0)
        EnforceAgentDailyLimitAppAction(repo, weeklyOnly(30)).execute(caller)

        val monday = LocalDate.now(EnforceAgentDailyLimitAppAction.ZONE)
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        assertEquals(caller.email, repo.askedEmail)
        assertEquals(monday.atStartOfDay(), repo.askedSince)
        assertEquals(DayOfWeek.MONDAY, repo.askedSince!!.dayOfWeek)
    }

    @Test
    fun `the daily limit is reported first when both are exhausted`() {
        // 30 messages already sent, both caps at their limit: the day is the nearer one to explain.
        val e = assertThrows<AgentDailyLimitExceededException> {
            EnforceAgentDailyLimitAppAction(FakeRepo(30), AgentProperties(dailyMessageLimit = 10, weeklyMessageLimit = 30))
                .execute(caller)
        }
        assertEquals(10, e.limit)
    }

    @Test
    fun `a week's worth of quiet days still runs out`() {
        // Under the daily cap but over the weekly one: five a day for six days is 30.
        val e = assertThrows<AgentDailyLimitExceededException> {
            EnforceAgentDailyLimitAppAction(CountingRepo(today = 5, week = 30), AgentProperties(dailyMessageLimit = 10, weeklyMessageLimit = 30))
                .execute(caller)
        }
        assertEquals(30, e.limit)
        assertTrue(e.message!!.contains("week"), e.message)
    }

    /** Answers each window separately, so a day under its cap can still exhaust the week. */
    private class CountingRepo(private val today: Long, private val week: Long) : AgentConversationRepositoryAppAction(
        Mockito.mock(AgentConversationRepository::class.java),
        Mockito.mock(AgentMessageRepository::class.java)
    ) {
        override fun countUserMessagesSince(email: String, since: LocalDateTime): Long =
            if (since == LocalDate.now(EnforceAgentDailyLimitAppAction.ZONE).atStartOfDay()) today else week
    }

    @Test
    fun `exempt student ids are never limited`() {
        val repo = FakeRepo(1_000)
        EnforceAgentDailyLimitAppAction(repo, AgentProperties(dailyLimitExemptStudentIds = setOf(1L))).execute(caller)
        assertEquals(null, repo.askedEmail, "no query for an exempt student")
        assertThrows<AgentDailyLimitExceededException> {
            EnforceAgentDailyLimitAppAction(FakeRepo(1_000), AgentProperties(dailyLimitExemptStudentIds = setOf(2L))).execute(caller)
        }
    }

    @Test
    fun `zero disables each window independently`() {
        val neither = FakeRepo(1_000)
        EnforceAgentDailyLimitAppAction(neither, AgentProperties(dailyMessageLimit = 0, weeklyMessageLimit = 0)).execute(caller)
        assertEquals(null, neither.askedEmail, "no query when both are disabled")

        val repo = FakeRepo(0)
        EnforceAgentDailyLimitAppAction(repo, dailyOnly(10)).execute(caller)
        assertEquals(LocalDate.now(EnforceAgentDailyLimitAppAction.ZONE).atStartOfDay(), repo.askedSince)
    }

    @Test
    fun `the shipped defaults are ten a day and thirty a week`() {
        val defaults = AgentProperties()
        assertEquals(10, defaults.dailyMessageLimit)
        assertEquals(30, defaults.weeklyMessageLimit)
    }
}
