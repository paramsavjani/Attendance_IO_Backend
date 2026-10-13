package com.attendanceio.api.application.agent

import com.attendanceio.api.application.agent.actions.AgentCollegeQueryAppAction
import com.attendanceio.api.model.college.DMAcademicCalendarEntry
import com.attendanceio.api.repository.college.AcademicCalendarRepository
import com.attendanceio.api.repository.college.CampusEventRepository
import com.attendanceio.api.repository.college.ClubRepository
import com.attendanceio.api.repository.college.FacultyRepository
import com.attendanceio.api.repository.college.PlacementRecruiterRepository
import com.attendanceio.api.repository.college.PlacementStatRepository
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Students say "mid-sem"; the calendar says "In-Semester Examination". A search that found nothing
 * used to send the model through every spelling it could think of and then round again.
 */
class AgentCalendarSearchTest {
    private val calendar = mock(AcademicCalendarRepository::class.java)
    private val college = AgentCollegeQueryAppAction(
        mock(ClubRepository::class.java), mock(CampusEventRepository::class.java), mock(FacultyRepository::class.java),
        calendar, mock(PlacementStatRepository::class.java), mock(PlacementRecruiterRepository::class.java)
    )

    private fun entry(n: Int, event: String) =
        DMAcademicCalendarEntry(id = "$n", academicYear = "2026-27", term = "Autumn", termLabel = "Autumn 2026-27", srNo = n, event = event, dateText = "")

    init {
        `when`(calendar.findAll()).thenReturn(
            listOf(
                entry(1, "First In-Semester Examination"),
                entry(2, "Second In-Semester Examination and PhD Comprehensive Examination"),
                entry(3, "End-Semester Examination"),
                entry(4, "Last date for Add-Drop")
            )
        )
    }

    private fun events(query: String) = college.academicCalendar(null, null, query).items.map { it.event }

    @Test
    fun `the names students use find the calendar's own`() {
        listOf("mid-sem", "midsem", "mid semester", "insem", "mid sem exams").forEach { q ->
            assertEquals(
                listOf("First In-Semester Examination", "Second In-Semester Examination and PhD Comprehensive Examination"),
                events(q), q
            )
        }
        assertEquals(listOf("End-Semester Examination"), events("end sem"))
        assertEquals(listOf("Last date for Add-Drop"), events("add drop"))
    }

    @Test
    fun `no match returns every event so there is nothing left to guess`() {
        val result = college.academicCalendar(null, null, "convocation")
        assertEquals(4, result.items.size)
        assertTrue("do not search again" in result.note.orEmpty(), result.note)
    }
}
