package com.attendanceio.api.repository.semester

import com.attendanceio.api.model.semester.DMSemester
import com.attendanceio.api.model.semester.SemesterType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface SemesterRepository : JpaRepository<DMSemester, Long> {
    fun findByYearAndType(year: Int, type: SemesterType): DMSemester?
    fun findByIsActive(isActive: Boolean): List<DMSemester>
    fun findByYear(year: Int): List<DMSemester>
    fun findByType(type: SemesterType): List<DMSemester>
}
