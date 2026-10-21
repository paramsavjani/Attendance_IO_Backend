package com.attendanceio.api.application.subject.actions

import java.time.LocalDate

/** The academic year starts in July: Oct 2026 is AY 2026-27, so the 2026 batch is in 1st year. */
object AcademicCalendar {
    fun startYear(today: LocalDate = LocalDate.now()): Int =
        if (today.monthValue >= 7) today.year else today.year - 1

    fun label(startYear: Int): String = "$startYear-${(startYear + 1) % 100}"

    /** Year of study of an admission batch (first four sid digits) in the academic year [startYear]. */
    fun yearOfStudy(batch: Int, startYear: Int): Int = startYear - batch + 1
}
