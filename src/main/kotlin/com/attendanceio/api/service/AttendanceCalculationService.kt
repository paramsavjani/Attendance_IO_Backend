package com.attendanceio.api.service

import org.springframework.stereotype.Service
import kotlin.math.ceil
import kotlin.math.max

@Service
class AttendanceCalculationService {
    
    /**
     * Calculate how many classes are needed to reach the minimum required percentage
     * @param present Number of present classes
     * @param total Total number of classes
     * @param minRequired Minimum required percentage (e.g., 75)
     * @return Number of classes needed, or 0 if already above threshold
     */
    fun calculateClassesNeeded(present: Int, total: Int, minRequired: Int): Int {
        if (total == 0) {
            // If no classes yet, need at least 1 class to start
            return 1
        }
        
        val currentPercentage = (present.toDouble() / total) * 100
        
        // Already above threshold
        if (currentPercentage >= minRequired) {
            return 0
        }
        
        // Calculate how many more classes needed
        // Formula: (present + needed) / (total + needed) >= minRequired / 100
        // Solving: present + needed >= (minRequired / 100) * (total + needed)
        // present + needed >= (minRequired * total) / 100 + (minRequired * needed) / 100
        // present - (minRequired * total) / 100 >= needed * (minRequired - 100) / 100
        // needed <= (present - (minRequired * total) / 100) / ((minRequired - 100) / 100)
        // needed <= (present * 100 - minRequired * total) / (minRequired - 100)
        
        var needed = 0
        var testPresent = present
        var testTotal = total
        
        // Iteratively calculate until we reach threshold
        while ((testPresent.toDouble() / testTotal) * 100 < minRequired && needed < 1000) {
            testPresent++
            testTotal++
            needed++
        }
        
        return needed
    }
    
    /**
     * Calculate how many classes can be bunked while maintaining minimum required percentage
     * @param present Number of present classes
     * @param currentTotal Current total number of classes (classes that have happened so far)
     * @param totalUntilEndDate Total expected classes until end date (e.g., April 30)
     * @param minRequired Minimum required percentage (e.g., 75)
     * @return Number of classes that can be bunked from remaining classes, or 0 if below threshold
     */
    fun calculateBunkableClasses(
        present: Int, 
        currentTotal: Int, 
        totalUntilEndDate: Int, 
        minRequired: Int
    ): Int {
        if (totalUntilEndDate == 0 || currentTotal >= totalUntilEndDate) {
            return 0
        }
        
        val remainingClasses = totalUntilEndDate - currentTotal
        
        // Calculate minimum classes needed to maintain percentage
        // We need: (present + remainingPresent) / totalUntilEndDate >= minRequired / 100
        // Solving: present + remainingPresent >= (minRequired * totalUntilEndDate) / 100
        // remainingPresent >= (minRequired * totalUntilEndDate) / 100 - present
        
        val minPresentNeeded = (minRequired * totalUntilEndDate) / 100.0
        val minRemainingPresentNeeded = ceil(minPresentNeeded - present).toInt()
        
        // If we need more remaining classes than available, cannot bunk any
        if (minRemainingPresentNeeded > remainingClasses || minRemainingPresentNeeded < 0) {
            return 0
        }
        
        // Calculate how many of the remaining classes can be bunked
        // Maximum bunkable = remainingClasses - minRemainingPresentNeeded
        val bunkable = remainingClasses - minRemainingPresentNeeded
        
        return max(0, bunkable)
    }
    
    /**
     * Calculate attendance percentage
     * @param present Number of present classes
     * @param total Total number of classes
     * @return Attendance percentage (0-100)
     */
    fun calculatePercentage(present: Int, total: Int): Double {
        if (total == 0) {
            return 0.0
        }
        return (present.toDouble() / total) * 100
    }
}

