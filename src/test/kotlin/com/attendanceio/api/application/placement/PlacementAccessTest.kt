package com.attendanceio.api.application.placement

import com.attendanceio.api.application.agent.AgentCaller
import com.attendanceio.api.application.agent.AgentToolCallRecorder
import com.attendanceio.api.application.agent.AgentToolRouter
import com.attendanceio.api.application.agent.`public`.PublicAgentToolPolicy
import com.attendanceio.api.application.agent.tools.AgentToolSupport
import com.attendanceio.api.application.agent.tools.PlacementAgentTools
import com.attendanceio.api.repository.student.StudentRepositoryAppAction
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.ai.chat.model.ToolContext
import org.springframework.ai.tool.method.MethodToolCallbackProvider
import tools.jackson.databind.json.JsonMapper
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Placement data names students and comes from a login-only portal, so only a signed-in DAU student
 * may see it. Each entry point is checked here: the access rule itself, the router, the tools, and
 * the public assistant's allowlist.
 */
class PlacementAccessTest {
    private val student = AgentCaller(email = "202301001@dau.ac.in", studentId = 1, name = "S", rollNumber = "202301001", isDemo = false)
    private val demo = AgentCaller(email = "demo@gmail.com", studentId = 790, name = "Demo", rollNumber = null, isDemo = true)
    private val visitor = AgentCaller(email = "visitor", studentId = null, name = null, rollNumber = null, isDemo = false, isPublic = true)
    private val outsider = AgentCaller(email = "someone@gmail.com", studentId = null, name = null, rollNumber = null, isDemo = false)

    private val placementTools = listOf("search_placement_companies", "get_placement_company", "find_placed_students", "get_interview_experiences", "get_placement_season_summary")

    @Test
    fun `only a signed-in dau student is allowed`() {
        assertTrue(PlacementAccess.allowed(student))
        assertTrue(PlacementAccess.allowed("202301001@DAU.AC.IN"))
        assertFalse(PlacementAccess.allowed(demo))
        assertFalse(PlacementAccess.allowed(visitor))
        assertFalse(PlacementAccess.allowed(outsider))
        assertFalse(PlacementAccess.allowed("x@dau.ac.in.evil.com"))
        assertFalse(PlacementAccess.allowed("x@daiict.ac.in"))
        assertFalse(PlacementAccess.allowed(null))
        assertFalse(PlacementAccess.allowed("202301001@dau.ac.in", isDemo = true))
        assertFalse(PlacementAccess.allowed("202301001@dau.ac.in", isPublic = true))
    }

    @Test
    fun `the router never offers placement tools to someone who may not see them`() {
        val router = AgentToolRouter(mock(), mock(), mock(), mock(), mock(), mock(), mock(), mock(), mock())
        for (q in listOf("who got placed at Tekion with 20 LPA package", "hello")) {
            assertFalse(AgentToolRouter.Group.PLACEMENTS in router.select(q, emptyList(), placements = false).groups, q)
        }
        assertTrue(AgentToolRouter.Group.PLACEMENTS in router.select("who got placed at Tekion", emptyList(), placements = true).groups)
    }

    @Test
    fun `every placement tool refuses a non-student caller`() {
        val tools = PlacementAgentTools(
            PlacementQueryAppAction(mock(com.attendanceio.api.repository.college.PlacementCompanyRepository::class.java), mock(com.attendanceio.api.repository.student.StudentRepositoryAppAction::class.java), com.attendanceio.api.application.placement.PlacementExperienceQueryAppAction(mock(com.attendanceio.api.repository.college.PlacementExperienceRepository::class.java))),
            AgentToolSupport(JsonMapper.builder().build(), mock(StudentRepositoryAppAction::class.java)),
            mock(StudentRepositoryAppAction::class.java),
            com.attendanceio.api.application.placement.PlacementExperienceQueryAppAction(mock(com.attendanceio.api.repository.college.PlacementExperienceRepository::class.java))
        )
        val callbacks = MethodToolCallbackProvider.builder().toolObjects(tools).build().toolCallbacks
        assertEquals(placementTools.sorted(), callbacks.map { it.toolDefinition.name() }.sorted())
        for (caller in listOf(demo, visitor, outsider)) {
            for (cb in callbacks) {
                val ctx = ToolContext(mapOf(AgentToolCallRecorder.CALLER_KEY to caller, AgentToolCallRecorder.CONTEXT_KEY to AgentToolCallRecorder()))
                val result = runCatching { cb.call("""{"company":"tekion"}""", ctx) }
                val text = result.getOrNull() ?: result.exceptionOrNull()?.let { generateSequence(it) { e -> e.cause }.joinToString(" ") { e -> e.message ?: "" } } ?: ""
                assertTrue(PlacementAccess.DENIED in text, "${cb.toolDefinition.name()} for ${caller.email}: $text")
            }
        }
    }

    @Test
    fun `the public assistant can never call a placement tool`() {
        val policy = PublicAgentToolPolicy(JsonMapper.builder().build())
        placementTools.forEach { assertFalse(policy.allows(it), it) }
    }
}
