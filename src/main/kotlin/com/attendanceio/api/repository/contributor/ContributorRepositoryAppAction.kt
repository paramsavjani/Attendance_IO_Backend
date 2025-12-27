package com.attendanceio.api.repository.contributor

import com.attendanceio.api.model.contributor.DMContributor
import com.attendanceio.api.model.contributor.ContributorType
import org.springframework.stereotype.Component

@Component
class ContributorRepositoryAppAction(
    private val contributorRepository: ContributorRepository
) {
    fun findAll(): List<DMContributor> {
        return contributorRepository.findAllByOrderByNameAsc()
    }

    fun findByTypeOfHelp(typeOfHelp: ContributorType): List<DMContributor> {
        return contributorRepository.findByTypeOfHelp(typeOfHelp)
    }

    fun save(contributor: DMContributor): DMContributor {
        return contributorRepository.save(contributor)
    }
}

