package com.attendanceio.api.model.agent

/*
 * Compact shapes of the institute knowledge base (MongoDB) for the model. Summaries carry what a
 * list answer needs; the detail types are returned only when one item was asked for by id.
 */

data class AgentClubSummary(
    val clubId: String,
    val name: String,
    /** club | committee | organisation | other */
    val type: String,
    val tag: String?,
    val email: String?,
    val instagram: String?,
    val website: String?,
    val description: String?,
    val memberCount: Int,
    /** Convener / Dy. Convener names, so "who runs X" needs no second call. */
    val leads: List<String>
)

data class AgentClubDetail(
    val clubId: String,
    val name: String,
    val type: String,
    val tag: String?,
    val description: String?,
    val keyActivities: String?,
    val email: String?,
    val website: String?,
    val instagram: String?,
    val linkedin: String?,
    val youtube: String?,
    val members: List<AgentClubMember>,
    val note: String? = null
)

data class AgentClubMember(
    val name: String,
    val rollNumber: String?,
    val designation: String?,
    val phone: String?,
    val email: String?,
    /** Set only when the member is listed under a different club than the one asked for. */
    val club: String? = null
)

data class AgentCampusEvent(
    val name: String,
    val organiser: String?,
    val venue: String?,
    /** IST, e.g. "Sun 21 Sep 2026 19:00" */
    val start: String,
    val end: String,
    val type: String?
)

data class AgentFacultySummary(
    val facultyId: String,
    val name: String,
    val category: String,
    val qualification: String?,
    val email: String?,
    val phone: String?,
    val office: String?,
    val researchInterests: String?
)

data class AgentFacultyDetail(
    val facultyId: String,
    val name: String,
    val category: String,
    val qualification: String?,
    val email: String?,
    val phone: String?,
    val office: String?,
    val specialization: String?,
    val biography: String?,
    val teaching: List<String>?,
    val research: String?,
    val profileUrl: String?
)

data class AgentCalendarEntry(
    val academicYear: String?,
    val term: String,
    val event: String,
    val dates: String,
    val days: String?
)

data class AgentPlacementStat(
    val season: String,
    val level: String,
    val audited: Boolean,
    val asOf: String?,
    val note: String?,
    val metrics: Map<String, Any?>,
    val salaryHeads: List<Map<String, Any?>>?,
    val offersBySector: Map<String, Int>?,
    val offersByLocation: Map<String, Int>?,
    val prominentRecruiters: List<String>?,
    val notableOffers: List<String>?,
    val source: String?
)

data class AgentRecruiter(
    val name: String,
    val seasons: List<String>
)
