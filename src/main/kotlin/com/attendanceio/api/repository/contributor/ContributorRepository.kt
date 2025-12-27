package com.attendanceio.api.repository.contributor

import com.attendanceio.api.model.contributor.DMContributor
import com.attendanceio.api.model.contributor.ContributorType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface ContributorRepository : JpaRepository<DMContributor, Long> {
    fun findByTypeOfHelp(typeOfHelp: ContributorType): List<DMContributor>
    fun findAllByOrderByNameAsc(): List<DMContributor>
}

