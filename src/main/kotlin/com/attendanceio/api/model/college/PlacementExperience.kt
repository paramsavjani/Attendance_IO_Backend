package com.attendanceio.api.model.college

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document

/*
 * Interview experiences shared by seniors, one document per person per company. Written as anonymous Markdown
 * (placements-data/experiences/<companyId>/<nn>.md: no names, roll numbers or other identifying details) and built by
 * college-data/tools/build_experiences.py, then loaded with mongoimport. The app only reads it.
 */

@Document(collection = "placement_experiences")
data class DMPlacementExperience(
    @Id val id: String,
    /** The company's _id in placement_companies. */
    val companyId: String,
    /** False when the company isn't in this season's placement list, so the app has no page to link to. */
    val inPlacements: Boolean = false,
    val company: String,
    /** The season the interviews happened in, e.g. "2025-26". */
    val season: String? = null,
    /** Internship | Job | Not stated */
    val offerType: String,
    val role: String? = null,
    /** The writer's B.Tech batch, e.g. "2023"; null when unknown. */
    val batch: String? = null,
    /** Selected | Not selected | Not shared */
    val verdict: String,
    val rounds: List<String> = emptyList(),
    val topics: List<String> = emptyList(),
    val summary: String,
    /** The experience itself, as Markdown. */
    val body: String = "",
    val words: Int = 0,
    val readMinutes: Int = 1,
    /** What the source document says about the drive: stipend, ctc, selects, location, process. */
    val drive: ExperienceDrive? = null
)

data class ExperienceDrive(
    val role: String? = null,
    val stipend: String? = null,
    val ctc: String? = null,
    /** A number or a phrase, e.g. "6 + 1 PPO". */
    val selects: String? = null,
    val location: String? = null,
    val process: List<String> = emptyList()
)
