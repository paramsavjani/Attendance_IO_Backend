package com.underworldnetwork.api.repository.subject

import com.underworldnetwork.api.model.subject.DMSubject
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface SubjectRepository : JpaRepository<DMSubject, Long> {
    fun findByCode(code: String): DMSubject?
    fun findBySemesterId(semesterId: Long): List<DMSubject>
    fun findByCodeAndSemesterId(code: String, semesterId: Long): DMSubject?
}
