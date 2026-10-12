package com.attendanceio.api.repository.agent

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

/**
 * The two columns the agent reports. Numbers here are real rows from the Autumn 2026-27 sync,
 * so a change in the arithmetic shows up as a failure against figures someone can look up.
 */
class SubjectAttendanceStandingTest {

    private fun standing(
        officialPresent: Int = 0,
        officialTotal: Int = 0,
        cutoff: LocalDate? = LocalDate.of(2026, 10, 5),
        presentAfter: Int = 0,
        absentAfter: Int = 0,
        cancelledAfter: Int = 0,
        totalAfter: Int = 0
    ) = SubjectAttendanceStanding(
        semesterId = 40, semesterYear = 2026, semesterType = "SUMMER",
        subjectCode = "IT457", subjectName = "Cloud Computing",
        officialPresent = officialPresent, officialTotal = officialTotal, cutoffDate = cutoff,
        presentAfter = presentAfter, absentAfter = absentAfter,
        cancelledAfter = cancelledAfter, totalAfter = totalAfter
    )

    @Test
    fun `official figures carry through when nothing was marked after the cutoff`() {
        val row = standing(officialPresent = 0, officialTotal = 17)
        assertTrue(row.hasOfficial)
        assertEquals(0, row.combinedPresent)
        assertEquals(17, row.combinedTotal, "the baseline alone is the total")
    }

    @Test
    fun `marked classes after the cutoff are added on top`() {
        // IT457: institute says 2/14 to 5 Oct, then one absent and three cancelled.
        val row = standing(officialPresent = 2, officialTotal = 14, absentAfter = 1, cancelledAfter = 3, totalAfter = 4)
        assertEquals(2, row.combinedPresent)
        assertEquals(15, row.combinedTotal, "14 + 4 marked - 3 cancelled")
    }

    @Test
    fun `cancelled classes never inflate the denominator`() {
        val row = standing(officialPresent = 1, officialTotal = 9, cancelledAfter = 2, totalAfter = 2)
        assertEquals(1, row.combinedPresent)
        assertEquals(9, row.combinedTotal, "two marked, both cancelled, so nothing changes")
    }

    @Test
    fun `a subject with no official row falls back to marked classes alone`() {
        val row = standing(cutoff = null, presentAfter = 27, absentAfter = 18, cancelledAfter = 26, totalAfter = 71)
        assertFalse(row.hasOfficial)
        assertEquals(27, row.combinedPresent)
        assertEquals(45, row.combinedTotal, "71 marked - 26 cancelled")
    }

    @Test
    fun `an empty enrollment reports nothing at all`() {
        val row = standing()
        assertFalse(row.hasOfficial)
        assertFalse(row.hasAnything, "no official figures and no marked classes is not an answer")
    }

    @Test
    fun `the total never goes negative when cancellations outnumber classes`() {
        val row = standing(cutoff = null, cancelledAfter = 5, totalAfter = 2)
        assertEquals(0, row.combinedTotal)
        assertFalse(row.hasAnything)
    }
}
