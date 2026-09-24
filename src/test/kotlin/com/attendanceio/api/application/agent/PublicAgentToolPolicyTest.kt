package com.attendanceio.api.application.agent

import com.attendanceio.api.application.agent.`public`.PublicAgentToolPolicy
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.ai.tool.ToolCallback
import org.springframework.ai.tool.definition.ToolDefinition
import tools.jackson.databind.json.JsonMapper
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The public demo answers from the same tools as the student app, so this policy is the whole
 * boundary. It is asserted here rather than trusted to the system prompt: a prompt is a request, and
 * nothing a visitor can type should be able to reach a student's attendance or someone's phone
 * number.
 */
class PublicAgentToolPolicyTest {
    private val policy = PublicAgentToolPolicy(JsonMapper.builder().build())

    private fun callback(name: String, result: String = "{}"): ToolCallback {
        val definition = mock(ToolDefinition::class.java)
        `when`(definition.name()).thenReturn(name)
        val callback = mock(ToolCallback::class.java)
        `when`(callback.toolDefinition).thenReturn(definition)
        `when`(callback.call(org.mockito.ArgumentMatchers.anyString())).thenReturn(result)
        return callback
    }

    @Test
    fun `no tool that reads a student's data is allowed`() {
        val private = listOf(
            "get_my_attendance", "get_my_timetable", "get_lab_tutorial_attendance", "get_unmarked_lectures",
            "get_attendance_on_date", "simulate_attendance", "search_students", "get_student_attendance",
            "compare_students", "get_subject_records", "get_attendance_trend", "get_group_average",
            "get_subject_class_stats", "get_overall_analytics"
        )
        private.forEach { assertFalse(policy.allows(it), "$it must not be reachable from the public demo") }
    }

    @Test
    fun `the published institute tools are allowed`() {
        listOf("find_clubs", "get_faculty", "get_curriculum", "get_placement_stats", "search_alumni",
               "get_subject_schedule", "get_holidays", "find_campus_services")
            .forEach { assertTrue(policy.allows(it), "$it should be part of the demo") }
    }

    @Test
    fun `a tool outside the list is dropped, not just undeclared`() {
        val offered = policy.publicCallbacks(listOf(callback("find_clubs"), callback("get_my_attendance")))
        assertEquals(listOf("find_clubs"), offered.map { it.toolDefinition.name() })
    }

    @Test
    fun `phone numbers are stripped at every depth`() {
        val json = """
            {"name":"Coding Club","email":"coding@dau.ac.in",
             "members":[{"name":"A","rollNumber":"202301001","phone":"+91 90000 00000","email":"a@dau.ac.in"},
                        {"name":"B","phones":["+91 1","+91 2"],"designation":"Convener"}],
             "office":{"contactNumber":"079-000000","room":"FB-1"}}
        """.trimIndent()
        val out = policy.redact(json)
        listOf("90000", "+91 1", "079-000000", "phone", "phones", "contactNumber").forEach {
            assertFalse(out.contains(it, ignoreCase = true), "redacted output still contains $it: $out")
        }
        // What is published stays: names, roles, roll numbers and official emails.
        listOf("Coding Club", "coding@dau.ac.in", "202301001", "Convener", "FB-1").forEach {
            assertTrue(out.contains(it), "redaction dropped something public: $it")
        }
    }

    @Test
    fun `a wrapped tool redacts what it returns`() {
        val wrapped = policy.publicCallbacks(listOf(callback("find_faculty", """[{"name":"X","phone":"+91 5"}]""")))
        val result = wrapped.single().call("{}")
        assertFalse(result.contains("+91 5"))
        assertTrue(result.contains("\"name\":\"X\""))
    }

    @Test
    fun `an unparseable result is withheld rather than passed through`() {
        val wrapped = policy.publicCallbacks(listOf(callback("find_staff_contacts", """phone: +91 98765 43210""")))
        assertEquals(PublicAgentToolPolicy.WITHHELD, wrapped.single().call("{}"))
    }
}
