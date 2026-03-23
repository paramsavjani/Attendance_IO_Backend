package com.attendanceio.api.controller.subject

import com.attendanceio.api.application.subject.actions.GetSubjectAnalysisAppAction
import com.attendanceio.api.model.subject.SubjectAnalysisEntry
import com.attendanceio.api.model.subject.SubjectAnalysisResponse
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/subjects/analysis")
class SubjectAnalysisController(
    private val getSubjectAnalysisAppAction: GetSubjectAnalysisAppAction
) {
    @GetMapping
    fun getSubjectAnalysis(
        @RequestParam(required = false) subjectId: Long?
    ): ResponseEntity<SubjectAnalysisResponse> {
        return try {
            val result = getSubjectAnalysisAppAction.execute(subjectId)
            ResponseEntity.ok(result)
        } catch (e: Exception) {
            ResponseEntity.status(500).build()
        }
    }

    @GetMapping("/{subjectCode}")
    fun getSubjectAnalysisByCode(
        @PathVariable subjectCode: String
    ): ResponseEntity<SubjectAnalysisEntry> {
        return try {
            val result = getSubjectAnalysisAppAction.executeByCode(subjectCode)
            if (result != null) ResponseEntity.ok(result)
            else ResponseEntity.notFound().build()
        } catch (e: Exception) {
            ResponseEntity.status(500).build()
        }
    }
}
