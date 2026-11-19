package com.attendanceio.api.model.placement

data class PlacementListResponse(
    val season: String?,
    val summary: PlacementSeasonSummary,
    val companies: List<PlacementCompanyCard>
)

data class PlacementSeasonSummary(
    val companiesOnPortal: Int,
    val companiesWithSelections: Int,
    val offers: Int,
    val ppos: Int,
    val campusSelections: Int,
    val studentsSelected: Int,
    val highestCtcLpa: Double?,
    val highestCtcCompany: String?,
    val averageCtcLpa: Double?,
    val resultsUpTo: String?,
    val updatedAt: String?,
    val topRecruiters: List<PlacementTopRecruiter>,
    val coverage: String
)

data class PlacementTopRecruiter(val id: String, val name: String, val selected: Int)

/** One row of the company list. */
data class PlacementCompanyCard(
    val id: String,
    val name: String,
    val offerTypes: List<String>,
    val programmes: List<String>,
    val roles: List<String>,
    val maxCtcLpa: Double?,
    val minCtcLpa: Double?,
    val minCpi: Double?,
    val selectedCount: Int,
    val ppoCount: Int,
    val lastActivity: String?,
    val onPortal: Boolean
)

data class PlacedStudent(
    val studentId: String?,
    val name: String?,
    val companyId: String,
    val company: String,
    val kind: String,
    val role: String?,
    val ctc: String?,
    val ctcLpa: Double?,
    val stipend: String?,
    val offerType: String?,
    val announcedOn: String?
)
