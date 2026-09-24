package com.attendanceio.api.application.agent

import com.attendanceio.api.application.agent.tools.AgentToolSupport
import com.attendanceio.api.model.schedule.DMSubjectSchedule
import com.attendanceio.api.repository.schedule.SubjectScheduleRepository
import com.attendanceio.api.repository.schedule.SubjectScheduleRepositoryAppAction
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The assistant runs its tools on a reactive thread while the answer streams, so the
 * open-session-in-view session that covers a normal request is not there. Anything a tool reads
 * lazily off a detached entity then fails with "Could not initialize proxy [… ] - no session" and
 * the student loses the whole answer — which is what happened twice to one student on 24 Sep 2026
 * when get_subject_schedule read `slot.startTime` off a query that had not fetched the slot.
 *
 * Two guards, because one alone would leave the trap armed for the next tool.
 */
class AgentDetachedEntityTest {
    @Test
    fun `a subject's schedule is read through the query that fetches day and slot`() {
        val repository = mock(SubjectScheduleRepository::class.java)
        val rows = listOf(DMSubjectSchedule())
        `when`(repository.findBySubjectIdIn(listOf(7L))).thenReturn(rows)

        val found = SubjectScheduleRepositoryAppAction(repository).findBySubjectId(7L)

        assertEquals(rows, found)
        // The fetch-joined query, never a derived one that leaves slot and day as dead proxies.
        verify(repository).findBySubjectIdIn(listOf(7L))
    }

    @Test
    fun `every tool call runs inside a read-only transaction, so lazy reads have a session`() {
        val recorded = AgentToolSupport::class.java.methods.single { it.name == "recorded" }
        val transactional = recorded.getAnnotation(Transactional::class.java)

        assertNotNull(transactional, "tools must run in a transaction: on the streaming thread there is no OSIV session")
        assertTrue(transactional.readOnly, "the tools only ever read")
    }
}
