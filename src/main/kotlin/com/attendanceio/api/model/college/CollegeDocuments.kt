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

/** One institute holiday from the yearly circular. */
@Document(collection = "holidays")
data class DMHoliday(
    @Id val id: String,
    val year: Int,
    val srNo: Int,
    val holiday: String,
    val dateText: String,
    val date: Instant? = null,
    val day: String? = null,
    val source: String? = null
)

/** A person or office a student may need to reach (wardens, deans, medical centre, registrar…). */
@Document(collection = "staff_contacts")
data class DMStaffContact(
    @Id val id: String,
    val name: String? = null,
    /** Primary title; [designations] keeps every title the person appears under across pages. */
    val designation: String,
    val designations: List<String> = emptyList(),
    val office: String? = null,
    val category: String,
    val room: String? = null,
    val phones: List<String> = emptyList(),
    val email: String? = null,
    val emails: List<String> = emptyList(),
    val notes: String? = null,
    val sources: List<String> = emptyList()
)

/** Semester-wise course structure of one programme. */
@Document(collection = "curriculum")
data class DMCurriculum(
    @Id val id: String,
    val programme: String,
    val pageTitle: String? = null,
    val totalCredits: Double? = null,
    val structureNotes: String? = null,
    val semesters: List<CurriculumSemester> = emptyList(),
    val electivePools: List<Map<String, Any?>> = emptyList(),
    val courseCount: Int = 0,
    val source: String? = null
)

data class CurriculumSemester(
    val semester: Int? = null,
    val label: String? = null,
    val courses: List<CurriculumCourse> = emptyList()
)

data class CurriculumCourse(
    val code: String? = null,
    val title: String,
    val credits: Double? = null,
    val ltpc: String? = null,
    val type: String? = null,
    val description: String? = null
)

/** An institute-level body (anti-ragging committee, ICC, grievance cell, academic council…). */
@Document(collection = "institute_committees")
data class DMInstituteCommittee(
    @Id val id: String,
    val name: String,
    val purpose: String? = null,
    val howToReach: String? = null,
    val validFor: String? = null,
    val members: List<Map<String, Any?>> = emptyList(),
    val memberCount: Int = 0,
    val source: String? = null
)

@Document(collection = "scholarships")
data class DMScholarship(
    @Id val id: String,
    val name: String,
    val sponsor: String? = null,
    val programmes: List<String> = emptyList(),
    val eligibility: String? = null,
    val benefit: String? = null,
    val numberOfAwards: String? = null,
    val duration: String? = null,
    val continuationCondition: String? = null,
    val howToApply: String? = null,
    val validFor: String? = null,
    val source: String? = null
)

/** A company visit / drive / session announced by the placement cell. */
@Document(collection = "placement_events")
data class DMPlacementEvent(
    @Id val id: String,
    val company: String? = null,
    val eventType: String? = null,
    val date: Instant? = null,
    val dateText: String? = null,
    val summary: String,
    val roles: List<String> = emptyList(),
    val ctc: String? = null,
    val source: String? = null
)

/** A campus service, facility, hostel procedure or rule set. */
@Document(collection = "campus_services")
data class DMCampusService(
    @Id val id: String,
    val name: String,
    val category: String? = null,
    val summary: String,
    val timings: String? = null,
    val location: String? = null,
    val contact: String? = null,
    val fee: String? = null,
    val steps: List<String> = emptyList(),
    val rules: List<String> = emptyList(),
    val validFor: String? = null,
    val pageTitle: String? = null,
    val source: String? = null
)

@Document(collection = "programs")
data class DMProgram(
    @Id val id: String,
    val name: String,
    val degree: String? = null,
    val level: String? = null,
    val school: String? = null,
    val durationYears: Double? = null,
    val intake: String? = null,
    val startedYear: Int? = null,
    val description: String? = null,
    val specialisations: List<String> = emptyList(),
    val admissionRoute: String? = null,
    val eligibility: String? = null,
    val fees: String? = null,
    val sourceUrl: String? = null
)
