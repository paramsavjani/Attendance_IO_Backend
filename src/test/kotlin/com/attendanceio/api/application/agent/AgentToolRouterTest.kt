package com.attendanceio.api.application.agent

import com.attendanceio.api.application.agent.AgentToolRouter.Group
import com.attendanceio.api.model.agent.AgentMessageRole
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AgentToolRouterTest {
    private val router = AgentToolRouter(mock(), mock(), mock(), mock(), mock(), mock(), mock(), mock())

    private fun groups(msg: String, vararg earlier: String) =
        router.select(msg, earlier.map { StoredAgentMessage(AgentMessageRole.USER, it, Instant.now()) }).groups

    @Test
    fun `routes by topic and keeps the thread's groups on follow-ups`() {
        assertEquals(setOf(Group.ATTENDANCE), groups("Can I skip the next two CT303 classes and stay above 75%?"))
        assertEquals(setOf(Group.ATTENDANCE), groups("compare me with 202301045"))
        assertTrue(Group.ALUMNI in groups("Alumni at Google I can reach on LinkedIn"))
        assertTrue(Group.COLLEGE in groups("who is the convener of the cultural committee"))
        assertTrue(Group.CAMPUS in groups("women's hostel warden phone number"))
        assertTrue(Group.CAMPUS in groups("laundry timings"))
        assertTrue(Group.COLLEGE in groups("when do end sem exams start"))
        assertTrue(Group.CAMPUS in groups("subjects in sem 3 of ICT"))
        assertTrue(Group.COLLEGE in groups("average package for UG in 2024-25") && Group.ALUMNI in groups("average package for UG in 2024-25"))
        assertTrue(Group.ATTENDANCE in groups("meri attendance kitni hai"))
        // follow-up inherits the club group from the thread even though "phone number" alone is CAMPUS
        val follow = groups("phone number?", "members of the cultural committee")
        assertTrue(Group.COLLEGE in follow && Group.CAMPUS in follow)
    }

    @Test
    fun `unknown phrasing falls back to every group`() {
        val sel = router.select("hello", emptyList())
        assertTrue(sel.fallback)
        assertEquals(Group.entries.toSet(), sel.groups)
        assertEquals(8, sel.toolObjects.size)
    }
}
