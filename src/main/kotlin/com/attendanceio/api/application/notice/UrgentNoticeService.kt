package com.attendanceio.api.application.notice

import com.attendanceio.api.model.notice.CreateNoticeRequest
import com.attendanceio.api.model.notice.DMUrgentNotice
import com.attendanceio.api.model.notice.DMUrgentNoticeReport
import com.attendanceio.api.model.notice.DMUrgentNoticeSeen
import com.attendanceio.api.model.notice.NoticeCreatorDto
import com.attendanceio.api.model.notice.NoticeType
import com.attendanceio.api.model.notice.UrgentNoticeResponse
import com.attendanceio.api.model.student.DMStudent
import com.attendanceio.api.repository.notice.UrgentNoticeReportRepository
import com.attendanceio.api.repository.notice.UrgentNoticeRepository
import com.attendanceio.api.repository.notice.UrgentNoticeSeenRepository
import com.attendanceio.api.repository.student.StudentSubjectRepositoryAppAction
import com.attendanceio.api.repository.subject.SubjectRepository
import com.attendanceio.api.service.FcmNotificationService
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@Service
class UrgentNoticeService(
    private val urgentNoticeRepository: UrgentNoticeRepository,
    private val urgentNoticeReportRepository: UrgentNoticeReportRepository,
    private val urgentNoticeSeenRepository: UrgentNoticeSeenRepository,
    private val studentSubjectRepositoryAppAction: StudentSubjectRepositoryAppAction,
    private val subjectRepository: SubjectRepository,
    private val fcmNotificationService: FcmNotificationService
) {
    private val log = LoggerFactory.getLogger(UrgentNoticeService::class.java)
    private val executor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "notice-notifier").apply { isDaemon = true }
    }

    @Transactional
    fun createNotice(student: DMStudent, request: CreateNoticeRequest): UrgentNoticeResponse {
        val studentId = student.id ?: throw IllegalArgumentException("Invalid student")
        val subjectId = request.subjectId ?: throw IllegalArgumentException("Pick a subject")
        val noticeType = request.noticeType ?: NoticeType.CANCELLED_LECTURE

        val title = request.title?.trim().orEmpty()
        require(title.isNotEmpty()) { "Title cannot be empty" }
        require(title.length <= 100) { "Title must be under 100 characters" }

        val message = request.message?.trim().orEmpty()
        require(message.isNotEmpty()) { "Message cannot be empty" }
        require(message.length <= 300) { "Message must be under 300 characters" }

        val enrollments = studentSubjectRepositoryAppAction.findByStudentId(studentId)
        val isEnrolled = enrollments.any { it.subject?.id == subjectId }
        if (!isEnrolled) {
            throw IllegalStateException("You can only post notices for subjects you are enrolled in")
        }

        // Spam rule: more than 3 hidden notices = blocked from posting
        if (urgentNoticeRepository.countHiddenBy(studentId) > 3) {
            throw IllegalStateException("You can no longer post urgent notices because multiple notices were hidden after classmates reported them.")
        }

        // Spam rule: max 2 notices a day per student
        val startOfDay = LocalDate.now().atStartOfDay()
        val createdToday = urgentNoticeRepository.countCreatedSince(studentId, startOfDay)
        if (createdToday >= 2) {
            throw IllegalStateException("You can post at most 2 urgent notices a day. Try again tomorrow.")
        }

        // Duplicate check: if someone already posted the same notice type for this subject today
        val activeForSubject = urgentNoticeRepository.findActiveForSubject(subjectId, startOfDay)
        val duplicate = activeForSubject.firstOrNull { it.noticeType == noticeType }
        if (duplicate != null) {
            throw IllegalStateException("A classmate has already posted a notice for this subject today: \"${duplicate.title}\"")
        }

        val subject = subjectRepository.findById(subjectId)
            .orElseThrow { IllegalArgumentException("Subject not found: $subjectId") }

        val notice = DMUrgentNotice().apply {
            this.subject = subject
            this.creator = student
            this.creatorName = student.name
            this.creatorSid = student.sid
            this.title = title
            this.message = message
            this.noticeType = noticeType
            this.reportCount = 0
            this.hidden = false
        }

        val saved = urgentNoticeRepository.save(notice)

        // Asynchronous FCM push notification dispatch
        dispatchPushNotification(saved, student)

        return toResponse(saved)
    }

    @Transactional(readOnly = true)
    fun getActiveNoticesForStudent(studentId: Long): List<UrgentNoticeResponse> {
        val enrolledSubjectIds = studentSubjectRepositoryAppAction.findByStudentId(studentId)
            .mapNotNull { it.subject?.id }
        if (enrolledSubjectIds.isEmpty()) return emptyList()

        val startOfDay = LocalDate.now().atStartOfDay()
        val activeNotices = urgentNoticeRepository.findActiveForSubjects(enrolledSubjectIds, startOfDay)
        if (activeNotices.isEmpty()) return emptyList()

        // Filter out notices the student has already dismissed / clicked "Got it"
        val seenNoticeIds = urgentNoticeSeenRepository.findSeenNoticeIds(studentId, activeNotices.mapNotNull { it.id })
            .toSet()

        return activeNotices
            .filter { it.id !in seenNoticeIds }
            .map { toResponse(it) }
    }

    @Transactional
    fun markNoticeSeen(noticeId: Long, student: DMStudent) {
        val studentId = student.id ?: return
        if (urgentNoticeSeenRepository.existsByNoticeIdAndStudentId(noticeId, studentId)) return

        val notice = urgentNoticeRepository.findById(noticeId).orElse(null) ?: return
        urgentNoticeSeenRepository.save(
            DMUrgentNoticeSeen().apply {
                this.notice = notice
                this.student = student
            }
        )
    }

    @Transactional
    fun reportNotice(noticeId: Long, student: DMStudent, reason: String?): Map<String, Any> {
        val studentId = student.id ?: throw IllegalArgumentException("Invalid student")
        val notice = urgentNoticeRepository.findById(noticeId)
            .orElseThrow { IllegalArgumentException("Notice not found") }

        if (urgentNoticeReportRepository.existsByNoticeIdAndStudentId(noticeId, studentId)) {
            return mapOf(
                "isHidden" to notice.hidden,
                "message" to "You have already reported this notice"
            )
        }

        urgentNoticeReportRepository.save(
            DMUrgentNoticeReport().apply {
                this.notice = notice
                this.student = student
                this.reason = reason
            }
        )

        val subjectId = notice.subject?.id ?: 0L
        val totalStudentsInClass = studentSubjectRepositoryAppAction.findBySubjectId(subjectId).size
        val newReportCount = notice.reportCount + 1
        notice.reportCount = newReportCount

        // Spam rule: auto-hide if reported by 20% of class or at least 3 students
        val threshold = maxOf(3, (totalStudentsInClass * 0.20).toInt())
        val isHidden = newReportCount >= threshold
        if (isHidden) {
            notice.hidden = true
        }
        urgentNoticeRepository.save(notice)

        return mapOf(
            "isHidden" to isHidden,
            "reportCount" to newReportCount,
            "message" to if (isHidden) "Notice hidden after classmate reports" else "Report recorded"
        )
    }

    @Transactional
    fun deleteNotice(noticeId: Long, studentId: Long) {
        val notice = urgentNoticeRepository.findById(noticeId)
            .orElseThrow { IllegalArgumentException("Notice not found") }

        if (notice.creator?.id != studentId) {
            throw IllegalStateException("Only the creator can delete this notice")
        }

        notice.deletedAt = LocalDateTime.now()
        urgentNoticeRepository.save(notice)
    }

    private fun dispatchPushNotification(notice: DMUrgentNotice, creator: DMStudent) {
        val subject = notice.subject ?: return
        val subjectId = subject.id ?: return
        val creatorId = creator.id

        executor.submit {
            try {
                // Respect quiet hours: avoid push notification between 11 PM and 7 AM
                val nowTime = LocalTime.now()
                val isQuietHours = nowTime.isAfter(LocalTime.of(23, 0)) || nowTime.isBefore(LocalTime.of(7, 0))
                if (isQuietHours) {
                    log.info("Notice {} created during quiet hours (11 PM - 7 AM). Push suppressed.", notice.id)
                    return@submit
                }

                val tokens = studentSubjectRepositoryAppAction.findBySubjectId(subjectId)
                    .mapNotNull { it.student }
                    .filter { it.id != creatorId }
                    .mapNotNull { it.fcmToken?.takeIf(String::isNotBlank) }
                    .distinct()

                if (tokens.isEmpty()) return@submit

                val emoji = when (notice.noticeType) {
                    NoticeType.CANCELLED_LECTURE -> "🚫"
                    NoticeType.QUIZ -> "❗"
                    NoticeType.MARKS -> "📊"
                    NoticeType.OTHER -> "📢"
                }

                val title = "$emoji ${notice.title} · ${subject.code ?: subject.name}"
                val body = "${notice.message} · by ${creator.name ?: "Classmate"}"

                val sent = fcmNotificationService.sendNotificationToMultiple(
                    fcmTokens = tokens,
                    title = title,
                    body = body,
                    data = mapOf(
                        "type" to "urgent_notice",
                        "noticeId" to (notice.id?.toString() ?: ""),
                        "subjectId" to (subject.id?.toString() ?: "")
                    )
                )
                log.info("Urgent notice {} push notification sent to {}/{} classmates in {}", notice.id, sent, tokens.size, subject.code)
            } catch (e: Exception) {
                log.warn("Failed to dispatch push notification for notice {}: {}", notice.id, e.message)
            }
        }
    }

    private fun toResponse(notice: DMUrgentNotice): UrgentNoticeResponse {
        return UrgentNoticeResponse(
            id = notice.id?.toString() ?: "",
            subjectId = notice.subject?.id?.toString() ?: "",
            subjectCode = notice.subject?.code,
            subjectName = notice.subject?.name,
            createdBy = NoticeCreatorDto(
                id = notice.creator?.id?.toString() ?: "",
                name = notice.creatorName ?: "Student",
                pictureUrl = notice.creator?.pictureUrl,
                sid = notice.creatorSid ?: notice.creator?.sid
            ),
            title = notice.title,
            message = notice.message,
            noticeType = notice.noticeType,
            createdAt = (notice.createdAt ?: LocalDateTime.now()).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
            reportCount = notice.reportCount,
            isFlaggedSpam = notice.hidden
        )
    }

    @PreDestroy
    fun shutdown() {
        executor.shutdown()
    }
}
