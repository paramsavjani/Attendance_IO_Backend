package com.attendanceio.api.application.agent.tools

import com.attendanceio.api.application.placement.PlacementAccess
import com.attendanceio.api.application.placement.PlacementQueryAppAction
import com.attendanceio.api.model.agent.AgentListResult
import com.attendanceio.api.model.agent.AgentPersonCard
import com.attendanceio.api.model.college.DMPlacementCompany
import com.attendanceio.api.model.placement.PlacedStudent
import com.attendanceio.api.model.placement.PlacementSeasonSummary
import com.attendanceio.api.repository.student.StudentRepositoryAppAction
import org.springframework.ai.chat.model.ToolContext
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam
import org.springframework.stereotype.Component

/**
 * The current placement season (2026-27): which companies came, for which roles and programmes,
 * their packages and job descriptions, and who they selected. Read-only; kept out of the public demo
 * because it names students.
 */
@Component
class PlacementAgentTools(
    private val placements: PlacementQueryAppAction,
    private val support: AgentToolSupport,
    private val students: StudentRepositoryAppAction
) {
    companion object {
        /** Long job descriptions are cut so one company never fills the context. */
        private const val DESCRIPTION_CHARS = 1800
        /** Per job description file; the start holds the role, eligibility and compensation. */
        private const val JD_FILE_CHARS = 3500
        private const val LIST_LIMIT = 40
    }

    @Tool(
        name = "search_placement_companies",
        description = "This season's (2026-27) campus placement and internship drives: companies with roles, programmes they were open for, " +
            "highest advertised package (LPA), CPI cut-off and how many students they selected. All filters optional and combinable. " +
            "For one company's full details (CTC breakdown, stipend, eligibility, selected students) use get_placement_company."
    )
    fun searchPlacementCompanies(
        @ToolParam(description = "Company, role or skill words, e.g. 'quant', 'sde', 'ml', 'remote'", required = false) query: String?,
        @ToolParam(description = "Programme it was open for: btech | mnc | mtech | mtech ec | msc it | msc ds | msc aa | mdes", required = false) programme: String?,
        @ToolParam(description = "job | internship | ppo", required = false) offerType: String?,
        @ToolParam(description = "Only companies offering at least this many LPA", required = false) minLpa: Double?,
        @ToolParam(description = "recent (default) | ctc | selected", required = false) sortBy: String?,
        toolContext: ToolContext
    ): AgentListResult<Map<String, Any?>> =
        support.recorded(toolContext, "search_placement_companies", mapOf("query" to query, "programme" to programme, "offerType" to offerType, "minLpa" to minLpa, "sortBy" to sortBy)) {
            requireStudent(toolContext)
            val rows = placements.search(query, programme, offerType, minLpa, sortBy)
            AgentListResult(
                rows.take(LIST_LIMIT).map { c ->
                    mapOf(
                        "company" to c.name, "offerTypes" to c.offerTypes, "roles" to c.roles.take(5), "openFor" to c.programmes,
                        "maxCtcLpa" to c.maxCtcLpa, "minCpi" to c.minCpi, "selected" to c.selectedCount, "ppos" to c.ppoCount.takeIf { it > 0 },
                        "result" to resultStatus(c),
                        "lastActivity" to c.lastActivity?.take(10)
                    )
                },
                rows.size,
                when {
                    rows.isEmpty() -> "No company this season matched. Try fewer filters."
                    rows.size > LIST_LIMIT -> "Showing $LIST_LIMIT of ${rows.size}; narrow the search."
                    else -> "Season 2026-27, from the placement portal and the placement cell's result mails. maxCtcLpa is the advertised package, not an offer letter."
                }
            )
        }

    @Tool(
        name = "get_placement_company",
        description = "One company of the 2026-27 season in full: every drive (date, offer type, location, CPI cut-off, programmes it was open for, bond), " +
            "each role's UG/PG package, stipend and job description, the text of the attached JD files (CTC breakdown, rounds, skills, " +
            "eligibility, bond), and every student it selected or gave a PPO."
    )
    fun getPlacementCompany(
        @ToolParam(description = "Company name or part of it, e.g. 'tekion', 'unify', 'wells fargo'") company: String,
        toolContext: ToolContext
    ): Map<String, Any?> =
        support.recorded(toolContext, "get_placement_company", mapOf("company" to company)) {
            requireStudent(toolContext)
            val c = placements.find(company)
                ?: return@recorded mapOf("error" to "No company named '$company' this season. Use search_placement_companies to see the list.")
            c.toAgentDetail()
        }

    @Tool(
        name = "find_placed_students",
        description = "Who got placed or got a PPO this season (2026-27), with company, role, package and date. query = student name or roll number; " +
            "company narrows to one company. Omit both for everyone."
    )
    fun findPlacedStudents(
        @ToolParam(description = "Student name or roll number", required = false) query: String?,
        @ToolParam(description = "Company name", required = false) company: String?,
        toolContext: ToolContext
    ): AgentListResult<PlacedStudent> =
        support.recorded(toolContext, "find_placed_students", mapOf("query" to query, "company" to company)) {
            requireStudent(toolContext)
            val rows = placements.placedStudents(query, company)
            if (query != null) rows.mapNotNull { it.studentId }.distinct().take(3).forEach { sid -> mentionBySid(toolContext, sid) }
            AgentListResult(rows.take(80), rows.size, when {
                rows.isEmpty() && query != null -> "No selection on record for '$query' this season. Results cover the B.Tech 2023 batch announced so far."
                rows.isEmpty() -> "No selections matched."
                else -> "One row per offer; a student can hold more than one. PPO packages are as reported by students."
            })
        }

    @Tool(
        name = "get_placement_season_summary",
        description = "2026-27 placement season so far: average (overall, PPO-only, campus-only), median and highest package, offers split into " +
            "PPOs and campus selections, students selected, companies, every company's PPOs and campus selections with package, offer types, and the average package of the companies that came each month. " +
            "Use for any placement average / highest / 'how is placement going' question — never attendance analytics."
    )
    fun getPlacementSeasonSummary(toolContext: ToolContext): PlacementSeasonSummary =
        support.recorded(toolContext, "get_placement_season_summary", emptyMap()) {
            requireStudent(toolContext)
            placements.summary()
        }

    /** Every tool checks the caller itself, so a tool reached by any route still refuses non-students. */
    private fun requireStudent(toolContext: ToolContext) {
        if (!PlacementAccess.allowed(support.caller(toolContext))) throw IllegalStateException(PlacementAccess.DENIED)
    }

    private fun mentionBySid(toolContext: ToolContext, sid: String) {
        val student = students.findBySid(sid) ?: return
        support.mention(toolContext, AgentPersonCard(student.id.toString(), student.name ?: "", student.sid, student.pictureUrl))
    }

    /** "N selected", "declared: no B.Tech student selected (...)" or "not declared yet" — 0 is a real result only when declared. */
    private fun resultStatus(c: DMPlacementCompany): String = when {
        c.selectedCount > 0 -> "${c.selectedCount} selected"
        c.resultDeclared -> "declared${c.resultOn?.let { " on ${it.take(10)}" } ?: ""}: 0 B.Tech students selected" +
            (c.resultNote?.let { " ($it)" } ?: "")
        else -> "not declared yet"
    }

    private fun DMPlacementCompany.toAgentDetail(): Map<String, Any?> = mapOf(
        "company" to name,
        "season" to season,
        "offerTypes" to offerTypes,
        "openFor" to programmes,
        "drives" to drives.map { d ->
            mapOf(
                "title" to d.title, "offerType" to d.offerType, "location" to d.location, "ugCpiCutoff" to d.ugCpi, "pgCpiCutoff" to d.pgCpi,
                "openFor" to d.openForLabels, "driveDate" to d.driveDate?.take(10), "registration" to listOfNotNull(d.registrationStart?.take(10), d.registrationEnd?.take(10)).joinToString(" to ").ifEmpty { null },
                "bond" to d.bond,
                "roles" to d.profiles.map { p ->
                    mapOf(
                        "role" to p.name, "ugPackageLpa" to p.ugPackage, "pgPackageLpa" to p.pgPackage, "stipendPerMonth" to p.stipend,
                        "jobDescription" to p.description?.let { if (it.length > DESCRIPTION_CHARS) it.take(DESCRIPTION_CHARS) + "…" else it }
                    )
                },
                "jobDescriptionFiles" to d.jdFiles.map { f ->
                    mapOf("file" to f.file, "text" to if (f.text.length > JD_FILE_CHARS) f.text.take(JD_FILE_CHARS) + "…" else f.text)
                }.ifEmpty { null },
                "portalLink" to d.source
            )
        },
        "packagesFromMails" to ctcNotes.ifEmpty { null },
        "selected" to selections.map { s ->
            mapOf("name" to s.name, "rollNumber" to s.studentId, "kind" to s.kind, "role" to s.role, "ctc" to s.ctc, "stipend" to s.stipend, "announcedOn" to s.announcedOn)
        },
        "selectedCount" to selectedCount,
        "result" to resultStatus(this),
        "note" to if (!onPortal) "Not on the placement portal this season (a PPO from a summer internship or an off-campus drive); details come from the results only." else null
    )
}
