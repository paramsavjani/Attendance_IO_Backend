package com.attendanceio.api.application.agent.`public`

import org.slf4j.LoggerFactory
import org.springframework.ai.tool.ToolCallback
import org.springframework.stereotype.Component
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.node.ArrayNode
import tools.jackson.databind.node.ObjectNode

/**
 * What an anonymous visitor on the public demo is allowed to see.
 *
 * The demo runs on the same tools and the same data as the student app, so the boundary is drawn
 * here and nowhere else. Two independent rules, both enforced in code rather than asked for in the
 * system prompt — a prompt is a request, and this is the one thing that must not depend on the
 * model behaving:
 *
 *  1. [ALLOWED] names every tool a visitor may call. It holds only institute-wide information that
 *     is already published — clubs and committees, faculty, curriculum and programmes, the academic
 *     calendar and holidays, campus events and services, placement figures, the subject catalogue
 *     and lecture timetable, and the alumni directory. Nothing that reads a student's attendance is
 *     in it, so no wording a visitor invents can reach one: a tool outside this list is neither
 *     offered to the model nor resolvable if it asks for it anyway.
 *
 *  2. [REDACTED_KEYS] are stripped from every result before the model sees them. Several allowed
 *     tools carry personal phone numbers alongside public information — club members, faculty, staff
 *     contacts, committee members — and those belong to real people who published them for students,
 *     not for the internet. Names, roles, roll numbers and official club or faculty email addresses
 *     stay: those are on the institute's own pages.
 */
@Component
class PublicAgentToolPolicy(
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(PublicAgentToolPolicy::class.java)

    /** The allowed tools, wrapped so their results are redacted. Everything else is dropped. */
    fun publicCallbacks(all: List<ToolCallback>): List<ToolCallback> =
        all.filter { it.toolDefinition.name() in ALLOWED }.map { Redacting(it) }

    fun allows(toolName: String): Boolean = toolName in ALLOWED

    /**
     * [json] with every denied key removed, at any depth. Fails closed: a result that cannot be
     * parsed is withheld rather than passed through, because the only reason to pass it would be to
     * hope it contained nothing personal.
     */
    fun redact(json: String): String {
        if (json.isBlank()) return json
        return try {
            val root = objectMapper.readTree(json)
            scrub(root)
            objectMapper.writeValueAsString(root)
        } catch (e: Exception) {
            logger.warn("public=REDACTION_FAILED withholding result reason={}", e.message)
            WITHHELD
        }
    }

    private fun scrub(node: JsonNode) {
        when (node) {
            is ObjectNode -> {
                node.propertyNames().toList().forEach { name ->
                    if (name.lowercase() in REDACTED_KEYS) node.remove(name) else scrub(node.get(name))
                }
            }
            is ArrayNode -> node.forEach { scrub(it) }
            else -> Unit
        }
    }

    /** An allowed tool whose result is filtered on the way back to the model. */
    private inner class Redacting(private val delegate: ToolCallback) : ToolCallback {
        override fun getToolDefinition() = delegate.toolDefinition

        override fun call(toolInput: String): String = redact(delegate.call(toolInput))

        override fun call(toolInput: String, toolContext: org.springframework.ai.chat.model.ToolContext?): String =
            redact(delegate.call(toolInput, toolContext))
    }

    companion object {
        /** Returned instead of a result that could not be filtered. */
        const val WITHHELD = """{"error":"This information is not available on the public demo."}"""

        /** Keys dropped from every public result, compared in lower case. */
        val REDACTED_KEYS = setOf(
            "phone", "phones", "phonenumber", "mobile", "mobilenumber", "contactnumber",
            "contactnumbers", "whatsapp", "telephone",
            // Where a scraped record came from. The singular `source` is left alone on purpose:
            // placement figures name the document they came from, which is worth showing.
            "sources"
        )

        /**
         * Tools an anonymous visitor may call. Grouped as they read on the demo, and deliberately
         * written out one by one: a new tool is private until someone adds it here on purpose.
         */
        val ALLOWED = setOf(
            // Clubs, committees, faculty, events, placements — the institute's public face.
            "find_clubs", "get_club", "find_club_member", "get_campus_events",
            "find_faculty", "get_faculty", "get_institute_calendar",
            "get_placement_stats", "list_placement_recruiters",
            // Campus information published for prospective and current students.
            "get_holidays", "find_staff_contacts", "list_programmes", "get_curriculum",
            "find_institute_committees", "find_scholarships", "get_placement_events",
            "find_campus_services",
            // Course catalogue and lecture timetable: what is taught, when and where.
            "list_semesters", "list_subjects", "get_subject_schedule", "get_academic_calendar",
            // Alumni directory: where graduates work, capped and search-only like in the app.
            "search_alumni", "list_alumni_companies"
        )
    }
}
