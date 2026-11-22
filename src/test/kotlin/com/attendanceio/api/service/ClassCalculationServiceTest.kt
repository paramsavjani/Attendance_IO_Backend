package com.attendanceio.api.service

import com.attendanceio.api.repository.student.StudentRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.time.DayOfWeek
import java.time.LocalDate

class ClassCalculationServiceTest {
    private val service = ClassCalculationService(
        "2026-07-22", "2026-11-20",
        "202601|202603|202604|202605|202606|202631|202632=2026-11-27",
        Mockito.mock(StudentRepository::class.java)
    )
    private val nov20 = LocalDate.parse("2026-11-20")
    private val nov27 = LocalDate.parse("2026-11-27")

    @Test
    fun `senior batches and first-year PG end on 20 Nov, first-year BTech and BS-MS on 27 Nov`() {
        assertEquals(nov20, service.endDateFor("202303046"))
        assertEquals(nov20, service.endDateFor("202401001"))
        assertEquals(nov20, service.endDateFor("202501001"))
        assertEquals(nov20, service.endDateFor("202611001"))
        assertEquals(nov27, service.endDateFor("202601001"))
        assertEquals(nov27, service.endDateFor("202631005"))
        assertEquals(nov20, service.endDateFor(null))
    }

    @Test
    fun `classes are over the day after the last class day`() {
        val mon23 = LocalDate.parse("2026-11-23")
        assertFalse(service.classesOverFor("202303046", nov20))
        assertTrue(service.classesOverFor("202303046", mon23))
        assertFalse(service.classesOverFor("202601001", mon23))
        assertTrue(service.classesOverFor("202601001", LocalDate.parse("2026-11-30")))
    }

    @Test
    fun `a first-year total keeps counting the week after 20 Nov`() {
        val mondays = listOf(DayOfWeek.MONDAY)
        val dec1 = LocalDate.parse("2026-12-01")
        val senior = service.calculateTotalClassesFromDays(mondays, dec1, service.endDateFor("202303046"))
        val firstYear = service.calculateTotalClassesFromDays(mondays, dec1, service.endDateFor("202601001"))
        assertEquals(senior + 1, firstYear) // Monday 23 Nov
    }
}
