package com.attendanceio.api.repository.semester

import com.attendanceio.api.model.semester.DMSemester
import com.attendanceio.api.repository.semester.SemesterRepository
import org.springframework.stereotype.Component

@Component
class SemesterRepositoryAppAction(
    private val semesterRepository: SemesterRepository
) {
    fun findByIsActive(isActive: Boolean): List<DMSemester> {
        return semesterRepository.findByIsActive(isActive)
    }
}