package com.attendanceio.api.model.search

data class StudentSearchResponse(
    val id: String,
    val name: String,
    val rollNumber: String,
    val email: String? = null,
    val pictureUrl: String? = null,
    /** Active-semester lectures counted: the institute's official total, or (without one) the student's own marks. */
    val markedLectures: Int = 0,
    val presentLectures: Int = 0,
    /** present / marked as a percentage; null when nothing has been marked. */
    val attendancePercentage: Double? = null,
    /** INSTITUTE or STUDENT: where the numbers above come from; null when there are none. */
    val attendanceSource: String? = null,
    /** Their best announced offer this season; only sent to signed-in DAU students, null when none is announced. */
    val placement: StudentSearchPlacement? = null
)

data class StudentSearchPlacement(
    val companyId: String,
    val company: String,
    /** PPO | Campus */
    val kind: String,
    val ctcLpa: Double? = null,
    /** All offers announced for them this season (a student can hold more than one). */
    val offers: Int = 1
)
