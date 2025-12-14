package com.underworldnetwork.api.repository.student

import com.underworldnetwork.api.model.student.DMStudent
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface StudentRepository : JpaRepository<DMStudent, Long> {
    fun findByEmail(email: String): DMStudent?
    fun findBySid(sid: String): DMStudent?
}
