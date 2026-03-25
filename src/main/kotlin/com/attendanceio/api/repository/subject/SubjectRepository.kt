package com.attendanceio.api.repository.subject

import com.attendanceio.api.model.subject.DMSubject
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface SubjectRepository : JpaRepository<DMSubject, Long> {
    fun findByCode(code: String): DMSubject?

    /** Same code may exist across semesters; never use single-result findByCode for API that must not throw. */
    fun findAllByCodeIgnoreCaseOrderByIdDesc(code: String): List<DMSubject>

    fun findBySemesterId(semesterId: Long): List<DMSubject>
    fun findByCodeAndSemesterId(code: String, semesterId: Long): DMSubject?
}
