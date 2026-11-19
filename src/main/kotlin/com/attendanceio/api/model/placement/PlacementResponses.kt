package com.attendanceio.api.model.placement

data class PlacementListResponse(
    val season: String?,
    val summary: PlacementSeasonSummary,
    val companies: List<PlacementCompanyCard>
)

data class PlacementSeasonSummary(
    val companiesOnPortal: Int,
    val companiesWithSelections: Int,
    /** Companies whose result is out but selected no B.Tech student. */
    val companiesWithNoSelection: Int = 0,
    val offers: Int,
    val ppos: Int,
    val campusSelections: Int,
    val studentsSelected: Int,
    val highestCtcLpa: Double?,
    val highestCtcCompany: String?,
    val averageCtcLpa: Double?,
    val medianCtcLpa: Double?,
    /** Average over PPOs only, and over campus selections only. */
    val averagePpoCtcLpa: Double?,
    val averageCampusCtcLpa: Double?,
    /** Offers that have a package figure; the averages are over these. */
    val offersWithCtc: Int,
    val resultsUpTo: String?,
    val updatedAt: String?,
    val topRecruiters: List<PlacementTopRecruiter>,
    /** Every company that gave PPOs: how many and at what package. */
    val pposByCompany: List<PlacementCompanyOffers> = emptyList(),
    /** Every company with campus selections: how many, and its package. */
    val campusByCompany: List<PlacementCompanyOffers> = emptyList(),
    /** Campus selections by offer type as the company advertised it, e.g. "Internship": 30. */
    val campusByOfferType: Map<String, Int> = emptyMap(),
    val coverage: String
)

data class PlacementCompanyOffers(val company: String, val offers: Int, val ctc: String?, val stipend: String? = null)

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
    /** Result out (selectedCount may be 0 when the placement cell said no B.Tech student was selected). */
    val resultDeclared: Boolean = false,
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
