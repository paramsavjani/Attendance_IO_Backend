package com.attendanceio.api.model.college

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

/*
 * Institute knowledge base, stored in MongoDB. Loaded from public DAU sources (daiict.ac.in,
 * sbg.dau.ac.in, placement.daiict.ac.in) by an offline importer; the app only reads it.
 * Field names match the imported JSON one-to-one so a re-import never needs a mapping step.
 */

/** A club, committee or organisation listed by the Student Body Government, with its current members. */
@Document(collection = "clubs")
data class DMClub(
    @Id val id: String,
    val name: String,
    /** club | committee | organisation | other */
    val type: String,
    val group: String? = null,
    val tag: String? = null,
    val description: String? = null,
    val keyActivities: String? = null,
    val email: String? = null,
    val website: String? = null,
    val instagram: String? = null,
    val linkedin: String? = null,
    val youtube: String? = null,
    val members: List<ClubMember> = emptyList(),
    val memberCount: Int = 0,
    val source: String? = null
)

data class ClubMember(
    val name: String,
    val rollNumber: String? = null,
    val designation: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val tenureStart: Instant? = null,
    val tenureEnd: Instant? = null
)

/** A public campus event from the SBG calendar. */
@Document(collection = "campus_events")
data class DMCampusEvent(
    @Id val id: String,
    val name: String,
    val club: String? = null,
    val venue: String? = null,
    val start: Instant,
    val end: Instant,
    val eventType: String? = null,
    val status: String? = null,
    val source: String? = null
)

/** A faculty member's public profile. */
@Document(collection = "faculty")
data class DMFaculty(
    @Id val id: String,
    val name: String,
    /** Faculty | Adjunct Faculty | Adjunct Faculty (International) | Professor of Practice | Distinguished Professor | Institute Professor */
    val category: String,
    val qualification: String? = null,
    val email: String? = null,
    val phone: String? = null,
    val office: String? = null,
    val researchInterests: String? = null,
    val specialization: String? = null,
    val biography: String? = null,
    val teaching: List<String>? = null,
    val research: String? = null,
    val publications: String? = null,
    val profileUrl: String? = null
)

/** One row of the institute's official academic calendar. */
@Document(collection = "academic_calendar")
data class DMAcademicCalendarEntry(
    @Id val id: String,
    val academicYear: String? = null,
    /** Autumn | Winter | Summer */
    val term: String,
    val termLabel: String,
    val srNo: Int,
    val event: String,
    val dateText: String,
    val days: String? = null,
    val startDate: Instant? = null,
    val endDate: Instant? = null,
    val source: String? = null
)

/** Placement figures for one season and level, as published in one official document. */
@Document(collection = "placement_stats")
data class DMPlacementStat(
    @Id val id: String,
    val season: String,
    /** UG | PG | ALL */
    val level: String,
    val audited: Boolean = false,
    val auditor: String? = null,
    val asOf: String? = null,
    val note: String? = null,
    val metrics: Map<String, Any?> = emptyMap(),
    val salaryHeads: List<Map<String, Any?>>? = null,
    val offersBySector: Map<String, Int>? = null,
    val offersByLocation: Map<String, Int>? = null,
    val prominentRecruiters: List<String>? = null,
    val brightSparks: List<String>? = null,
    val starRecruits: List<String>? = null,
    val source: String? = null
)

@Document(collection = "placement_recruiters")
data class DMPlacementRecruiter(
    @Id val id: String,
    val name: String,
    val seasons: List<String> = emptyList(),
    val source: String? = null
)
