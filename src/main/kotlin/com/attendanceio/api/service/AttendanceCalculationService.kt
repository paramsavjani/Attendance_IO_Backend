package com.attendanceio.api.service

import org.springframework.stereotype.Service
import kotlin.math.ceil
import kotlin.math.max

@Service
class AttendanceCalculationService {

    fun calculateClassesNeeded(present: Int, total: Int, minRequired: Int): Int {
        if (total == 0) return 1
        val currentPercentage = (present.toDouble() / total) * 100
        if (currentPercentage >= minRequired) return 0

        var needed = 0
        var testPresent = present
        var testTotal = total
        while ((testPresent.toDouble() / testTotal) * 100 < minRequired && needed < 1000) {
            testPresent++
            testTotal++
            needed++
        }
        return needed
    }

    fun calculateBunkableClasses(present: Int, currentTotal: Int, totalUntilEndDate: Int, minRequired: Int): Int {
        if (totalUntilEndDate == 0 || currentTotal >= totalUntilEndDate) return 0
        val remainingClasses = totalUntilEndDate - currentTotal
        if (minRequired == 0) return remainingClasses
        val minPresentNeeded = (minRequired * totalUntilEndDate) / 100.0
        val minRemainingPresentNeeded = ceil(minPresentNeeded - present).toInt()
        if (minRemainingPresentNeeded > remainingClasses || minRemainingPresentNeeded < 0) return 0
        return max(0, remainingClasses - minRemainingPresentNeeded)
    }

    fun calculatePercentage(present: Int, total: Int): Double =
        if (total == 0) 0.0 else (present.toDouble() / total) * 100
}
