package com.attendanceio.api.application.subject.actions

import com.attendanceio.api.model.subject.AbsenceCount
import com.attendanceio.api.model.subject.AttendanceBucket
import com.attendanceio.api.model.subject.SubjectAttendanceStats
import com.attendanceio.api.model.subject.SubjectStudentEntry
import kotlin.math.floor
import kotlin.math.sqrt

/**
 * Distribution statistics for one subject's official attendance, used by the
 * per-subject analysis page (histogram, quartiles, outlier-free average, etc.).
 */
object SubjectAttendanceStatsCalculator {
    private const val PASS_THRESHOLD = 70.0
    private const val BORDERLINE_UPPER = 75.0

    private val BANDS = listOf(
        "Below 50%" to (0.0 to 50.0),
        "50-60%" to (50.0 to 60.0),
        "60-70%" to (60.0 to 70.0),
        "70-80%" to (70.0 to 80.0),
        "80-90%" to (80.0 to 90.0),
        "90-100%" to (90.0 to 100.0)
    )

    fun compute(students: List<SubjectStudentEntry>): SubjectAttendanceStats? {
        if (students.isEmpty()) return null
        val values = students.map { it.attendancePercentage }.sorted()
        val n = values.size

        val mean = values.average()
        val variance = values.sumOf { (it - mean) * (it - mean) } / n
        val q1 = percentile(values, 25.0)
        val q3 = percentile(values, 75.0)
        val iqr = q3 - q1
        val lowerBound = q1 - 1.5 * iqr
        val upperBound = q3 + 1.5 * iqr
        val inliers = values.filter { it in lowerBound..upperBound }

        val histogram = (0 until 10).map { i ->
            val from = i * 10.0
            val to = from + 10.0
            val count = values.count { bucketIndex(it) == i }
            AttendanceBucket("${from.toInt()}-${to.toInt()}", from, to, count, pct(count, n))
        }
        val mode = histogram.maxByOrNull { it.count }!!

        val bands = BANDS.map { (label, range) ->
            val (from, to) = range
            val count = values.count { v ->
                if (to >= 100.0) v >= from else v >= from && v < to
            }
            AttendanceBucket(label, from, to, count, pct(count, n))
        }

        val absences = students.map { it.absentClasses }.sorted()
        val absenceDistribution = absences.groupingBy { it }.eachCount()
            .toSortedMap()
            .map { (absent, count) -> AbsenceCount(absent, count) }

        return SubjectAttendanceStats(
            mean = round2(mean),
            median = round2(percentile(values, 50.0)),
            standardDeviation = round2(sqrt(variance)),
            min = round2(values.first()),
            max = round2(values.last()),
            p10 = round2(percentile(values, 10.0)),
            q1 = round2(q1),
            q3 = round2(q3),
            p90 = round2(percentile(values, 90.0)),
            iqr = round2(iqr),
            trimmedMean = round2(if (inliers.isNotEmpty()) inliers.average() else mean),
            outlierLowerBound = round2(lowerBound.coerceAtLeast(0.0)),
            outlierUpperBound = round2(upperBound.coerceAtMost(100.0)),
            lowOutlierCount = values.count { it < lowerBound },
            highOutlierCount = values.count { it > upperBound },
            modeRange = mode.label,
            modeRangeCount = mode.count,
            below70Count = values.count { it < PASS_THRESHOLD },
            borderlineCount = values.count { it >= PASS_THRESHOLD && it < BORDERLINE_UPPER },
            fullAttendanceCount = values.count { it >= 100.0 },
            maxClassesConducted = students.maxOf { it.totalClasses },
            averageAbsences = round2(absences.average()),
            medianAbsences = round2(percentile(absences.map { it.toDouble() }, 50.0)),
            histogram = histogram,
            bands = bands,
            absenceDistribution = absenceDistribution
        )
    }

    /** 100% falls into the last bucket (90-100) rather than a bucket of its own. */
    private fun bucketIndex(value: Double): Int = floor(value / 10.0).toInt().coerceIn(0, 9)

    /** Linear-interpolated percentile over an already sorted list. */
    private fun percentile(sorted: List<Double>, p: Double): Double {
        if (sorted.size == 1) return sorted[0]
        val rank = p / 100.0 * (sorted.size - 1)
        val lo = floor(rank).toInt()
        val hi = (lo + 1).coerceAtMost(sorted.size - 1)
        return sorted[lo] + (sorted[hi] - sorted[lo]) * (rank - lo)
    }

    private fun pct(count: Int, total: Int): Double = round2(count * 100.0 / total)

    private fun round2(v: Double): Double = Math.round(v * 100.0) / 100.0
}
