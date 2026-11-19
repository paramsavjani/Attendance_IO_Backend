package com.attendanceio.api.model.college

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document

/*
 * The current placement season, one document per company. Built offline by
 * college-data/tools/build_placements.py from the placement portal's company pages (roles, CTC,
 * stipend, eligibility, programmes) and the results sheet (who was selected), then loaded with
 * mongoimport. Field names match that JSON one-to-one; the app only reads it.
 */

@Document(collection = "placement_companies")
data class DMPlacementCompany(
    @Id val id: String,
    val season: String,
    val name: String,
    val aliases: List<String> = emptyList(),
    /** Job | Internship | PPO */
    val offerTypes: List<String> = emptyList(),
    /** Programmes the drives were open for, e.g. "B.Tech", "B.Tech MnC", "M.Tech". */
    val programmes: List<String> = emptyList(),
    val roles: List<String> = emptyList(),
    val locations: List<String> = emptyList(),
    val minCpi: Double? = null,
    val maxCtcLpa: Double? = null,
    val minCtcLpa: Double? = null,
    val selectedCount: Int = 0,
    val ppoCount: Int = 0,
    /** The result is out: someone was selected, or the placement cell announced no B.Tech student was. */
    val resultDeclared: Boolean = false,
    /** Why a declared result has no selections, e.g. "The placement cell announced that no one was selected." */
    val resultNote: String? = null,
    val resultOn: String? = null,
    /** ISO date (or date-time) of the latest drive or result. */
    val lastActivity: String? = null,
    /** False for PPO-only and off-portal companies that appear only in the results sheet. */
    val onPortal: Boolean = false,
    val drives: List<PlacementDrive> = emptyList(),
    val selections: List<PlacementSelection> = emptyList(),
    val ctcNotes: List<PlacementCtcNote> = emptyList(),
    val updatedAt: String? = null
)

/** One company page on the placement portal (a company can visit more than once). */
data class PlacementDrive(
    val eventId: Int,
    val title: String,
    val offerType: String? = null,
    val location: String? = null,
    val category: String? = null,
    val ugCpi: Double? = null,
    val pgCpi: Double? = null,
    val openFor: List<String> = emptyList(),
    val openForLabels: List<String> = emptyList(),
    val driveDate: String? = null,
    val registrationStart: String? = null,
    val registrationEnd: String? = null,
    val bond: String? = null,
    val hasJdFile: Boolean = false,
    /** Text of the job description files attached to the drive (PDF / Word), one entry per file. */
    val jdFiles: List<PlacementJdFile> = emptyList(),
    val profiles: List<PlacementProfile> = emptyList(),
    val source: String? = null
)

/** One job description file from a drive, as text; [transcribed] when it was a scanned image. */
data class PlacementJdFile(
    val file: String,
    val text: String,
    val transcribed: Boolean = false
)

/** One role in a drive, with its package as the portal states it. */
data class PlacementProfile(
    val name: String? = null,
    val ugPackage: String? = null,
    val pgPackage: String? = null,
    val ugCtcMin: Double? = null,
    val ugCtcMax: Double? = null,
    val pgCtcMin: Double? = null,
    val pgCtcMax: Double? = null,
    val stipend: String? = null,
    /** The job description text: CTC breakdown, eligibility, rounds, skills. */
    val description: String? = null
)

/** One student's offer from this company: a PPO or a campus selection. */
data class PlacementSelection(
    val studentId: String? = null,
    val name: String? = null,
    /** PPO | Campus */
    val kind: String,
    val role: String? = null,
    val ctc: String? = null,
    val ctcLpa: Double? = null,
    val stipend: String? = null,
    val offerType: String? = null,
    val announcedOn: String? = null,
    val programme: String? = null,
    val notes: String? = null
)

/** Package per role as compiled from the placement cell's registration mails. */
data class PlacementCtcNote(
    val role: String? = null,
    val ctc: String? = null,
    val stipend: String? = null,
    val offerType: String? = null,
    val notes: String? = null
)
