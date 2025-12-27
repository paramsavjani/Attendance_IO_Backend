package com.attendanceio.api.controller.contributor

import com.attendanceio.api.model.contributor.ContributorResponse
import com.attendanceio.api.model.contributor.ContributorType
import com.attendanceio.api.repository.contributor.ContributorRepositoryAppAction
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/contributors")
class ContributorController(
    private val contributorRepositoryAppAction: ContributorRepositoryAppAction
) {
    @GetMapping
    fun getContributors(
        @RequestParam(required = false) type: String?
    ): ResponseEntity<List<ContributorResponse>> {
        return try {
            val contributors = if (type != null) {
                val contributorType = try {
                    ContributorType.valueOf(type.uppercase())
                } catch (e: IllegalArgumentException) {
                    return ResponseEntity.status(400).build()
                }
                contributorRepositoryAppAction.findByTypeOfHelp(contributorType)
            } else {
                contributorRepositoryAppAction.findAll()
            }

            val responses = contributors.map { contributor ->
                ContributorResponse(
                    id = contributor.id ?: 0,
                    name = contributor.name,
                    typeOfHelp = contributor.typeOfHelp.name
                )
            }

            ResponseEntity.ok(responses)
        } catch (e: Exception) {
            ResponseEntity.status(500).build()
        }
    }
}

