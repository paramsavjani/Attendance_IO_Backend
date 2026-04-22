package com.attendanceio.api.controller.search

import com.attendanceio.api.model.search.DMSearchHistory
import com.attendanceio.api.repository.search.SearchHistoryRepositoryAppAction
import com.attendanceio.api.repository.student.StudentRepositoryAppAction
import com.attendanceio.api.model.student.DMStudent
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class SaveSearchHistoryRequest(val viewedStudentId: String)

data class SearchHistoryResponse(
    val id: Long,
    val viewedStudentId: String,
    val viewedStudentName: String,
    val viewedStudentRollNumber: String,
    val viewedStudentPictureUrl: String?,
    val createdAt: String?
)

@RestController
@RequestMapping("/api/search/history")
class SearchHistoryController(
    private val studentRepositoryAppAction: StudentRepositoryAppAction,
    private val searchHistoryRepositoryAppAction: SearchHistoryRepositoryAppAction
) {

    private fun resolveStudent(oauth2User: OAuth2User?): DMStudent? {
        if (oauth2User == null) return null
        val email = oauth2User.getAttribute<String>("email") ?: return null
        return studentRepositoryAppAction.findByEmail(email)
    }

    /** GET /api/search/history — returns last 10 viewed profiles, newest first */
    @GetMapping
    fun getHistory(
        @AuthenticationPrincipal oauth2User: OAuth2User?
    ): ResponseEntity<List<SearchHistoryResponse>> {
        val student = resolveStudent(oauth2User) ?: return ResponseEntity.status(401).build()
        val studentId = student.id ?: return ResponseEntity.status(404).build()

        val history = searchHistoryRepositoryAppAction.findRecentByStudentId(studentId)
        return ResponseEntity.ok(history.mapNotNull { it.toResponse() })
    }

    /**
     * POST /api/search/history
     * Body: { viewedStudentId: "123" }
     * Saves the viewed profile; deduplicates by soft-deleting the previous entry first.
     */
    @PostMapping
    fun saveHistory(
        @AuthenticationPrincipal oauth2User: OAuth2User?,
        @RequestBody request: SaveSearchHistoryRequest
    ): ResponseEntity<SearchHistoryResponse> {
        val student = resolveStudent(oauth2User) ?: return ResponseEntity.status(401).build()
        val studentId = student.id ?: return ResponseEntity.status(404).build()

        val viewedStudentId = request.viewedStudentId.toLongOrNull()
            ?: return ResponseEntity.status(400).build()

        val viewedStudent = studentRepositoryAppAction.findById(viewedStudentId)
            ?: return ResponseEntity.status(404).build()

        // Soft-delete existing duplicate so the new one becomes the most recent
        val existing = searchHistoryRepositoryAppAction
            .findByStudentIdAndViewedStudentId(studentId, viewedStudentId)
        if (existing != null) {
            existing.isDeleted = true
            searchHistoryRepositoryAppAction.save(existing)
        }

        val saved = searchHistoryRepositoryAppAction.save(DMSearchHistory().apply {
            this.student = student
            this.viewedStudent = viewedStudent
            this.isDeleted = false
        })

        return ResponseEntity.ok(saved.toResponse() ?: return ResponseEntity.status(500).build())
    }

    /** DELETE /api/search/history/{id} — soft-delete one entry */
    @DeleteMapping("/{id}")
    fun deleteHistory(
        @AuthenticationPrincipal oauth2User: OAuth2User?,
        @PathVariable id: Long
    ): ResponseEntity<Any> {
        val student = resolveStudent(oauth2User) ?: return ResponseEntity.status(401).build()
        val studentId = student.id ?: return ResponseEntity.status(404).build()

        val history = searchHistoryRepositoryAppAction.findById(id)
            ?: return ResponseEntity.status(404).build()

        if (history.student?.id != studentId) return ResponseEntity.status(403).build()

        history.isDeleted = true
        searchHistoryRepositoryAppAction.save(history)
        return ResponseEntity.ok(mapOf("message" to "Deleted"))
    }

    /** DELETE /api/search/history — soft-delete all entries for the current user */
    @DeleteMapping
    fun clearHistory(
        @AuthenticationPrincipal oauth2User: OAuth2User?
    ): ResponseEntity<Any> {
        val student = resolveStudent(oauth2User) ?: return ResponseEntity.status(401).build()
        val studentId = student.id ?: return ResponseEntity.status(404).build()

        val all = searchHistoryRepositoryAppAction.findAllActiveByStudentId(studentId)
        all.forEach { it.isDeleted = true }
        searchHistoryRepositoryAppAction.saveAll(all)
        return ResponseEntity.ok(mapOf("message" to "Cleared", "count" to all.size))
    }

    private fun DMSearchHistory.toResponse(): SearchHistoryResponse? {
        val vs = viewedStudent ?: return null
        return SearchHistoryResponse(
            id = this.id ?: return null,
            viewedStudentId = vs.id?.toString() ?: return null,
            viewedStudentName = vs.name ?: "",
            viewedStudentRollNumber = vs.sid,
            viewedStudentPictureUrl = vs.pictureUrl,
            createdAt = this.createdAt?.toString()
        )
    }
}
