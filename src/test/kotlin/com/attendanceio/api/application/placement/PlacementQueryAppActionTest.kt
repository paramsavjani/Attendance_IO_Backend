package com.attendanceio.api.application.placement

import com.attendanceio.api.model.college.DMPlacementCompany
import com.attendanceio.api.model.college.PlacementDrive
import com.attendanceio.api.model.college.PlacementProfile
import com.attendanceio.api.model.college.PlacementSelection
import com.attendanceio.api.model.placement.PlacementMonth
import com.attendanceio.api.repository.college.PlacementCompanyRepository
import com.attendanceio.api.repository.student.StudentRepositoryAppAction
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import kotlin.test.assertEquals

class PlacementQueryAppActionTest {
    private fun sel(name: String, kind: String, ctc: Double?, type: String? = null) =
        PlacementSelection(studentId = name, name = name, kind = kind, ctc = ctc?.toString(), ctcLpa = ctc, offerType = type)

    private val repo = mock(PlacementCompanyRepository::class.java).also {
        `when`(it.findAll()).thenReturn(listOf(
            DMPlacementCompany("google", "2026-27", "Google", selections = listOf(sel("a", "PPO", 55.0), sel("b", "PPO", 55.0)), selectedCount = 2, ppoCount = 2),
            DMPlacementCompany("tekion", "2026-27", "Tekion", onPortal = true, selections = listOf(sel("c", "Campus", 20.0, "Internship"), sel("d", "Campus", null, "Internship")), selectedCount = 2),
            DMPlacementCompany("e2m", "2026-27", "E2M", onPortal = true, selections = listOf(sel("e", "Campus", 7.0, "Job, Internship")), selectedCount = 1)
        ))
    }
    private val action = PlacementQueryAppAction(repo, mock(StudentRepositoryAppAction::class.java))

    @Test
    fun `season summary splits PPO and campus and skips offers without a package`() {
        val s = action.summary()
        assertEquals(5, s.offers)
        assertEquals(2, s.ppos)
        assertEquals(3, s.campusSelections)
        assertEquals(4, s.offersWithCtc)
        assertEquals(34.25, s.averageCtcLpa)
        assertEquals(37.5, s.medianCtcLpa)
        assertEquals(55.0, s.averagePpoCtcLpa)
        assertEquals(13.5, s.averageCampusCtcLpa)
        assertEquals("Google", s.highestCtcCompany)
        assertEquals(listOf("Google" to 2), s.pposByCompany.map { it.company to it.offers })
        assertEquals(listOf("Tekion" to 2, "E2M" to 1), s.campusByCompany.map { it.company to it.offers })
        assertEquals(mapOf("Internship" to 2, "Job, Internship" to 1), s.campusByOfferType)
    }

    @Test
    fun `placed students match every word of the name or the roll number`() {
        assertEquals(listOf("Google"), action.placedStudents("a", null).map { it.company })
        assertEquals(0, action.placedStudents("zzz", null).size)
    }

    @Test
    fun `placed percent is the batch's B Tech students with any offer over the batch size`() {
        val ids = listOf("202301001", "202301002", "202303001", "202301001", "202501009")
        val repo2 = mock(PlacementCompanyRepository::class.java).also {
            `when`(it.findAll()).thenReturn(listOf(
                DMPlacementCompany("x", "2026-27", "X", selections = ids.map { id -> PlacementSelection(studentId = id, name = id, kind = "Campus") }, selectedCount = ids.size)
            ))
        }
        val students = mock(StudentRepositoryAppAction::class.java).also {
            `when`(it.countBySidPattern("^2023(01|03|04|05|06)[0-9]{3}$")).thenReturn(40L)
        }
        val s = PlacementQueryAppAction(repo2, students).summary()
        assertEquals("2023", s.batchYear)
        assertEquals(40, s.batchStudents)
        assertEquals(3, s.batchPlaced)
        assertEquals(7.5, s.placedPercent)
    }

    @Test
    fun `a student with two offers counts once at the better one`() {
        val repo2 = mock(PlacementCompanyRepository::class.java).also {
            `when`(it.findAll()).thenReturn(listOf(
                DMPlacementCompany("infosys", "2026-27", "Infosys", selections = listOf(
                    PlacementSelection(studentId = "202303010", name = "Zeel", kind = "Campus", ctc = "7", ctcLpa = 7.0),
                    PlacementSelection(studentId = "202301001", name = "A", kind = "Campus", ctc = "7", ctcLpa = 7.0)
                ), selectedCount = 2),
                DMPlacementCompany("juspay", "2026-27", "JusPay", selections = listOf(
                    PlacementSelection(studentId = "202303010", name = "Zeel", kind = "Campus", ctc = "27", ctcLpa = 27.0)
                ), selectedCount = 1)
            ))
        }
        val s = PlacementQueryAppAction(repo2, mock(StudentRepositoryAppAction::class.java)).summary()
        assertEquals(2, s.offers)
        assertEquals(3, s.allOffers)
        assertEquals(2, s.studentsSelected)
        assertEquals(17.0, s.averageCtcLpa)
        assertEquals(27.0, s.highestCtcLpa)
    }

    @Test
    fun `companies by month average the packages of companies whose B Tech registration opened that month`() {
        fun drive(start: String?, openFor: String, vararg ctc: Double?) = PlacementDrive(
            eventId = 1, title = "x", registrationStart = start, openFor = listOf(openFor),
            profiles = ctc.map { PlacementProfile(ugCtcMin = it) }
        )
        fun company(id: String, minLpa: Double?, vararg drives: PlacementDrive) =
            DMPlacementCompany(id, "2026-27", id, minCtcLpa = minLpa, drives = drives.toList())
        val repo2 = mock(PlacementCompanyRepository::class.java).also {
            `when`(it.findAll()).thenReturn(listOf(
                // Two roles: the lower package; a second drive later stays in the first month
                company("a", 12.0, drive("2026-07-10T10:00:00+05:30", "BTech", 12.0, 25.0), drive("2026-09-01T10:00:00+05:30", "BTech", 30.0)),
                company("b", 20.0, drive("2026-07-20T10:00:00+05:30", "BTechMnC", 20.0)),
                // Package known only at company level
                company("c", 8.5, drive("2026-08-02T10:00:00+05:30", "BTech", null)),
                // Not counted: master's only, no package, PPO-only (no drive)
                company("d", 50.0, drive("2026-08-02T10:00:00+05:30", "MTech", null)),
                company("e", null, drive("2026-08-02T10:00:00+05:30", "BTech", null)),
                company("google", 53.0)
            ))
        }
        val s = PlacementQueryAppAction(repo2, mock(StudentRepositoryAppAction::class.java)).summary()
        assertEquals(listOf(PlacementMonth("2026-07", 2, 16.0), PlacementMonth("2026-08", 1, 8.5)), s.companiesByMonth)
        assertEquals(13.5, s.averageCompanyCtcLpa)
    }
}
