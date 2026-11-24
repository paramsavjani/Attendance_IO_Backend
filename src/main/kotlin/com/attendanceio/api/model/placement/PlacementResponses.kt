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
    /** One per student (their best offer); allOffers counts every offer, including a student's second one. */
    val offers: Int,
    val allOffers: Int = 0,
    val ppos: Int,
    val campusSelections: Int,
    val studentsSelected: Int,
    /** The B.Tech batch this season places (e.g. "2023"), its size in our student list, and how many of them have an offer. */
    val batchYear: String? = null,
    val batchStudents: Int? = null,
    val batchPlaced: Int? = null,
    /** batchPlaced / batchStudents as a percentage, one decimal. */
    val placedPercent: Double? = null,
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
    /**
     * Companies that came for B.Tech each month (the month their registration first opened) and the average of
     * their advertised packages. A plain average over companies, not over placed students; no PPOs.
     */
    val companiesByMonth: List<PlacementMonth> = emptyList(),
    /** The same average over every company in companiesByMonth. */
    val averageCompanyCtcLpa: Double? = null,
    /**
     * Students placed on campus each month (the month their best offer was announced) and the average of their
     * packages. No PPOs, and only from the month the first campus drive opened, so off-portal results before the
     * season are left out.
     */
    val placedByMonth: List<PlacementPlacedMonth> = emptyList(),
    /** The average package over every student in placedByMonth whose package is known. */
    val averagePlacedByMonthCtcLpa: Double? = null,
    val coverage: String
)

data class PlacementCompanyOffers(val company: String, val offers: Int, val ctc: String?, val stipend: String? = null)

/** [month] is "2026-07"; [companies] counts only those with an announced package. */
data class PlacementMonth(val month: String, val companies: Int, val averageCtcLpa: Double)

/** [students] counts everyone placed that month; the average is over those whose package is known (null when none is). */
data class PlacementPlacedMonth(val month: String, val students: Int, val averageCtcLpa: Double?)

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
