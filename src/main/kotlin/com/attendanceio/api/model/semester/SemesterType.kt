package com.attendanceio.api.model.semester

enum class SemesterType {
    SUMMER,
    WINTER;

    companion object {
        /**
         * The name students use: SUMMER is the July–November term (Autumn of the academic year that starts then),
         * WINTER the January–May term (Winter of the year that started the July before). SUMMER 2026 → "Autumn 2026-27".
         */
        fun displayName(type: String, year: Int): String = when (type.uppercase()) {
            "SUMMER" -> "Autumn $year-${(year + 1) % 100}"
            "WINTER" -> "Winter ${year - 1}-${year % 100}"
            else -> "$type $year"
        }
    }
}

