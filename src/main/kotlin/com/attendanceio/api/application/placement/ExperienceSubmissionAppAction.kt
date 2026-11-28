package com.attendanceio.api.application.placement

import com.attendanceio.api.model.placement.DMExperienceSubmission
import com.attendanceio.api.model.student.DMStudent
import com.attendanceio.api.repository.placement.ExperienceSubmissionRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.net.URI
import java.time.LocalDateTime

data class ExperienceSubmissionRequest(
    val company: String? = null,
    val role: String? = null,
    val offerType: String? = null,
    val verdict: String? = null,
    /** LINK | TEXT | FILE */
    val format: String? = null,
    val link: String? = null,
    val text: String? = null,
    val fileName: String? = null
)

data class ExperienceSubmissionView(
    val id: Long,
    val company: String,
    val role: String?,
    val offerType: String,
    val verdict: String,
    val format: String,
    val status: String,
    val submittedAt: String?
)

/** Validates and stores interview experiences sent from the app, for review before they are published anonymously. */
@Component
class ExperienceSubmissionAppAction(private val repository: ExperienceSubmissionRepository) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        val OFFER_TYPES = listOf("Placement", "Internship", "PPO")
        val VERDICTS = listOf("Selected", "Not selected", "Awaiting result")
        const val MIN_TEXT = 200
        const val MAX_TEXT = 60_000
        const val DAILY_LIMIT = 5
        private val LINK_HOSTS = setOf("docs.google.com", "drive.google.com")
    }

    /** Returns the saved submission, or throws IllegalArgumentException with a message the app shows as is. */
    fun submit(student: DMStudent, email: String, req: ExperienceSubmissionRequest): ExperienceSubmissionView {
        val studentId = student.id ?: throw IllegalArgumentException("Student not found")
        val company = req.company?.trim()?.take(120).orEmpty()
        require(company.length >= 2) { "Enter the company name." }
        val offerType = OFFER_TYPES.firstOrNull { it.equals(req.offerType?.trim(), true) } ?: throw IllegalArgumentException("Pick placement, internship or PPO.")
        val verdict = VERDICTS.firstOrNull { it.equals(req.verdict?.trim(), true) } ?: throw IllegalArgumentException("Pick how it ended.")
        val format = req.format?.trim()?.uppercase()
        require(format in setOf("LINK", "TEXT", "FILE")) { "Add a link, type it out, or upload a file." }

        var link: String? = null
        var body: String? = null
        if (format == "LINK") {
            link = req.link?.trim()?.takeIf { it.isNotEmpty() } ?: throw IllegalArgumentException("Paste the Google Docs or Drive link.")
            val uri = runCatching { URI(link) }.getOrNull()
            require(uri != null && uri.scheme == "https" && uri.host?.lowercase() in LINK_HOSTS && link.length <= 1000) {
                "Only Google Docs or Google Drive links (https://docs.google.com/… or https://drive.google.com/…) work. Set sharing to \"Anyone with the link\"."
            }
        } else {
            body = req.text?.replace("\r\n", "\n")?.trim().orEmpty()
            require(body.length >= MIN_TEXT) { "Write a bit more: at least $MIN_TEXT characters (rounds, questions, tips)." }
            require(body.length <= MAX_TEXT) { "That's too long: keep it under ${MAX_TEXT / 1000}k characters." }
        }
        require(repository.countByStudentIdAndCreatedAtAfter(studentId, LocalDateTime.now().minusDays(1)) < DAILY_LIMIT) {
            "You've sent $DAILY_LIMIT today. Try again tomorrow."
        }

        val saved = repository.save(DMExperienceSubmission().apply {
            this.studentId = studentId
            rollNumber = student.sid
            studentName = student.name
            userEmail = email
            this.company = company
            role = req.role?.trim()?.take(120)?.takeIf { it.isNotEmpty() }
            this.offerType = offerType
            this.verdict = verdict
            this.format = format!!
            this.link = link
            this.body = body
            fileName = if (format == "FILE") req.fileName?.trim()?.take(255) else null
        })
        log.info("experience submission saved id={} company={} format={}", saved.id, company, format)
        return saved.toView()
    }

    fun mine(studentId: Long): List<ExperienceSubmissionView> =
        repository.findByStudentIdOrderByCreatedAtDesc(studentId).map { it.toView() }

    private fun DMExperienceSubmission.toView() = ExperienceSubmissionView(
        id = id ?: 0, company = company, role = role, offerType = offerType, verdict = verdict, format = format,
        status = status, submittedAt = createdAt?.toString()
    )
}
