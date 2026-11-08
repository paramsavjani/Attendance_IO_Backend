package com.attendanceio.api.service

import com.attendanceio.api.model.poll.CastVoteRequest
import com.attendanceio.api.model.poll.CreatePollRequest
import com.attendanceio.api.model.poll.DMPoll
import com.attendanceio.api.model.poll.DMPollOption
import com.attendanceio.api.model.poll.DMPollReport
import com.attendanceio.api.model.poll.DMPollVote
import com.attendanceio.api.model.poll.PollCreatorDto
import com.attendanceio.api.model.poll.PollOptionDto
import com.attendanceio.api.model.poll.PollResponse
import com.attendanceio.api.model.poll.PollStatus
import com.attendanceio.api.model.poll.PollType
import com.attendanceio.api.model.poll.ReportPollRequest
import com.attendanceio.api.model.poll.ReportPollResponse
import com.attendanceio.api.model.student.DMStudent
import com.attendanceio.api.repository.poll.PollOptionRepository
import com.attendanceio.api.repository.poll.PollReportRepository
import com.attendanceio.api.repository.poll.PollRepository
import com.attendanceio.api.repository.poll.PollVoteRepository
import com.attendanceio.api.repository.student.StudentSubjectRepository
import com.attendanceio.api.repository.subject.SubjectRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Service
class PollService(
    private val pollRepository: PollRepository,
    private val pollOptionRepository: PollOptionRepository,
    private val pollVoteRepository: PollVoteRepository,
    private val pollReportRepository: PollReportRepository,
    private val subjectRepository: SubjectRepository,
    private val studentSubjectRepository: StudentSubjectRepository,
    private val fcmNotificationService: FcmNotificationService
) {
    private val logger = LoggerFactory.getLogger(PollService::class.java)

    companion object {
        const val SPAM_REPORT_THRESHOLD = 3
    }

    /**
     * Verify if the student is currently enrolled in the given subject.
     */
    fun isStudentEnrolled(studentId: Long, subjectId: Long): Boolean {
        return studentSubjectRepository.findByStudentIdAndSubjectId(studentId, subjectId) != null
    }

    @Transactional
    fun createPoll(student: DMStudent, request: CreatePollRequest): PollResponse {
        val studentId = student.id ?: throw IllegalArgumentException("Invalid student ID")
        val subject = subjectRepository.findById(request.subjectId)
            .orElseThrow { IllegalArgumentException("Subject not found: ${request.subjectId}") }

        if (!isStudentEnrolled(studentId, request.subjectId)) {
            throw IllegalStateException("You must be enrolled in ${subject.code ?: "this subject"} to create polls")
        }

        val cleanTitle = request.title.trim()
        require(cleanTitle.isNotEmpty()) { "Poll title cannot be empty" }
        require(cleanTitle.length <= 255) { "Poll title exceeds maximum length of 255 characters" }

        val cleanOptions = request.options.map { it.trim() }.filter { it.isNotEmpty() }
        require(cleanOptions.size >= 2) { "Polls require at least 2 distinct options" }
        require(cleanOptions.size <= 8) { "Polls can have at most 8 options" }
        require(cleanOptions.distinctBy { it.lowercase() }.size == cleanOptions.size) { "Options must be distinct" }

        request.endDate?.let { end ->
            require(end.isAfter(LocalDateTime.now())) { "Poll end date must be in the future" }
        }

        val poll = DMPoll().apply {
            this.subject = subject
            this.createdBy = student
            this.title = cleanTitle
            this.description = request.description?.trim()?.takeIf { it.isNotEmpty() }
            this.pollType = request.pollType
            this.status = PollStatus.ACTIVE
            this.endDate = request.endDate
            this.reportCount = 0
            this.isFlaggedSpam = false
        }

        val savedPoll = pollRepository.save(poll)

        val optionsList = cleanOptions.mapIndexed { idx, optText ->
            DMPollOption().apply {
                this.poll = savedPoll
                this.optionText = optText
                this.displayOrder = idx
                this.voteCount = 0
            }
        }
        val savedOptions = pollOptionRepository.saveAll(optionsList)
        savedPoll.options = savedOptions.toMutableList()

        // Asynchronously notify enrolled classmates who have poll notifications turned on
        if (request.notifyStudents) {
            try {
                notifyClassmatesNewPoll(savedPoll, student)
            } catch (e: Exception) {
                logger.warn("Failed to dispatch push notification for new poll: ${e.message}", e)
            }
        }

        return toPollResponse(savedPoll, studentId)
    }

    @Transactional(readOnly = true)
    fun getPollsForSubject(subjectId: Long, currentStudentId: Long?): List<PollResponse> {
        val polls = pollRepository.findBySubjectIdOrderByCreatedAtDesc(subjectId)
        return polls.map { toPollResponse(it, currentStudentId) }
    }

    @Transactional(readOnly = true)
    fun getPollDetail(pollId: Long, currentStudentId: Long?): PollResponse {
        val poll = pollRepository.findById(pollId)
            .orElseThrow { IllegalArgumentException("Poll not found: $pollId") }
        return toPollResponse(poll, currentStudentId)
    }

    @Transactional(readOnly = true)
    fun getMyPolls(studentId: Long): List<PollResponse> {
        val polls = pollRepository.findByCreatedByIdOrderByCreatedAtDesc(studentId)
        return polls.map { toPollResponse(it, studentId) }
    }

    @Transactional
    fun castVote(pollId: Long, student: DMStudent, request: CastVoteRequest): PollResponse {
        val studentId = student.id ?: throw IllegalArgumentException("Invalid student ID")
        val poll = pollRepository.findById(pollId)
            .orElseThrow { IllegalArgumentException("Poll not found: $pollId") }

        val subjectId = poll.subject?.id
            ?: throw IllegalStateException("Poll subject is missing")

        if (!isStudentEnrolled(studentId, subjectId)) {
            throw IllegalStateException("You must be enrolled in this subject to vote")
        }

        checkPollIsActive(poll)

        require(request.optionIds.isNotEmpty()) { "At least one option must be selected" }

        if (poll.pollType == PollType.SINGLE_CHOICE || poll.pollType == PollType.YES_NO) {
            require(request.optionIds.size == 1) { "Only single choice voting is allowed for this poll" }
        }

        val validOptionsMap = pollOptionRepository.findByPollIdOrderByDisplayOrderAsc(pollId)
            .associateBy { it.id }

        request.optionIds.forEach { optId ->
            require(validOptionsMap.containsKey(optId)) { "Option $optId does not belong to poll $pollId" }
        }

        // Remove previous votes from this student for this poll
        pollVoteRepository.deleteByPollIdAndStudentId(pollId, studentId)

        // Insert new votes
        val newVotes = request.optionIds.map { optId ->
            DMPollVote().apply {
                this.poll = poll
                this.student = student
                this.option = validOptionsMap[optId]
            }
        }
        pollVoteRepository.saveAll(newVotes)

        // Recalculate vote count on all options
        val allVotesForPoll = pollVoteRepository.findByPollId(pollId)
        val voteCountByOptionId = allVotesForPoll.groupingBy { it.option?.id }.eachCount()

        validOptionsMap.values.forEach { opt ->
            opt.voteCount = voteCountByOptionId[opt.id] ?: 0
        }
        pollOptionRepository.saveAll(validOptionsMap.values)

        return toPollResponse(poll, studentId)
    }

    @Transactional
    fun closePoll(pollId: Long, studentId: Long): PollResponse {
        val poll = pollRepository.findById(pollId)
            .orElseThrow { IllegalArgumentException("Poll not found: $pollId") }

        if (poll.createdBy?.id != studentId) {
            throw IllegalStateException("Only the poll creator can close this poll")
        }

        poll.status = PollStatus.CLOSED
        val updated = pollRepository.save(poll)

        // Notify participants that results are finalized if enabled
        try {
            notifyParticipantsResults(updated)
        } catch (e: Exception) {
            logger.warn("Failed to send poll closure notification: ${e.message}")
        }

        return toPollResponse(updated, studentId)
    }

    @Transactional
    fun deletePoll(pollId: Long, studentId: Long) {
        val poll = pollRepository.findById(pollId)
            .orElseThrow { IllegalArgumentException("Poll not found: $pollId") }

        if (poll.createdBy?.id != studentId) {
            throw IllegalStateException("Only the poll creator can delete this poll")
        }

        pollVoteRepository.deleteByPollId(pollId)
        pollReportRepository.deleteByPollId(pollId)
        pollRepository.delete(poll)
    }

    @Transactional
    fun reportPoll(pollId: Long, student: DMStudent, request: ReportPollRequest): ReportPollResponse {
        val studentId = student.id ?: throw IllegalArgumentException("Invalid student ID")
        val poll = pollRepository.findById(pollId)
            .orElseThrow { IllegalArgumentException("Poll not found: $pollId") }

        if (pollReportRepository.existsByPollIdAndStudentId(pollId, studentId)) {
            return ReportPollResponse(
                isHidden = poll.isFlaggedSpam,
                reportCount = poll.reportCount,
                message = "You have already reported this poll"
            )
        }

        val report = DMPollReport().apply {
            this.poll = poll
            this.student = student
            this.reason = request.reason
            this.details = request.details?.take(500)
            this.reportAuthorAsSpammer = request.reportAuthorAsSpammer
        }
        pollReportRepository.save(report)

        poll.reportCount += 1
        if (poll.reportCount >= SPAM_REPORT_THRESHOLD) {
            poll.isFlaggedSpam = true
        }
        pollRepository.save(poll)

        return ReportPollResponse(
            isHidden = poll.isFlaggedSpam,
            reportCount = poll.reportCount,
            message = if (poll.isFlaggedSpam) "Poll flagged and hidden due to community reports" else "Report recorded"
        )
    }

    private fun checkPollIsActive(poll: DMPoll) {
        if (poll.status == PollStatus.CLOSED) {
            throw IllegalStateException("This poll is closed")
        }
        if (poll.endDate != null && LocalDateTime.now().isAfter(poll.endDate)) {
            poll.status = PollStatus.ENDED
            pollRepository.save(poll)
            throw IllegalStateException("This poll has ended")
        }
    }

    private fun notifyClassmatesNewPoll(poll: DMPoll, creator: DMStudent) {
        val subject = poll.subject ?: return
        val enrollments = studentSubjectRepository.findBySubjectId(subject.id ?: return)
        val candidateTokens = enrollments
            .mapNotNull { it.student }
            .filter { it.id != creator.id && it.pollNotifyNewPolls && !it.fcmToken.isNullOrBlank() }
            .mapNotNull { it.fcmToken }
            .distinct()

        if (candidateTokens.isNotEmpty()) {
            val title = "New Poll: ${subject.code ?: subject.name}"
            val body = "${creator.name ?: "A classmate"} asked: \"${poll.title}\""
            fcmNotificationService.sendNotificationToMultiple(
                fcmTokens = candidateTokens,
                title = title,
                body = body,
                data = mapOf(
                    "type" to "poll",
                    "pollId" to (poll.id?.toString() ?: ""),
                    "subjectId" to (subject.id?.toString() ?: "")
                )
            )
        }
    }

    private fun notifyParticipantsResults(poll: DMPoll) {
        val votes = pollVoteRepository.findByPollId(poll.id ?: return)
        val voterTokens = votes
            .mapNotNull { it.student }
            .filter { it.pollNotifyResults && !it.fcmToken.isNullOrBlank() }
            .mapNotNull { it.fcmToken }
            .distinct()

        if (voterTokens.isNotEmpty()) {
            val title = "Poll Closed: ${poll.subject?.code ?: "Subject"}"
            val body = "Results are in for: \"${poll.title}\""
            fcmNotificationService.sendNotificationToMultiple(
                fcmTokens = voterTokens,
                title = title,
                body = body,
                data = mapOf("type" to "poll_results", "pollId" to (poll.id?.toString() ?: ""))
            )
        }
    }

    private fun toPollResponse(poll: DMPoll, currentStudentId: Long?): PollResponse {
        val options = pollOptionRepository.findByPollIdOrderByDisplayOrderAsc(poll.id ?: 0L)
        val totalVotes = options.sumOf { it.voteCount }

        val userVotedOptionIds = if (currentStudentId != null) {
            pollVoteRepository.findByPollIdAndStudentId(poll.id ?: 0L, currentStudentId)
                .mapNotNull { it.option?.id?.toString() }
        } else emptyList()

        val hasReported = if (currentStudentId != null) {
            pollReportRepository.existsByPollIdAndStudentId(poll.id ?: 0L, currentStudentId)
        } else false

        // Check if poll end date passed
        val effectiveStatus = if (poll.status == PollStatus.ACTIVE && poll.endDate != null && LocalDateTime.now().isAfter(poll.endDate)) {
            PollStatus.ENDED
        } else {
            poll.status
        }

        return PollResponse(
            id = poll.id?.toString() ?: "",
            subjectId = poll.subject?.id?.toString() ?: "",
            subjectCode = poll.subject?.code,
            subjectName = poll.subject?.name,
            createdBy = PollCreatorDto(
                id = poll.createdBy?.id?.toString() ?: "",
                name = poll.createdBy?.name ?: "Student",
                pictureUrl = poll.createdBy?.pictureUrl,
                sid = poll.createdBy?.sid
            ),
            title = poll.title,
            description = poll.description,
            pollType = poll.pollType,
            status = effectiveStatus,
            endDate = poll.endDate?.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
            createdAt = (poll.createdAt ?: LocalDateTime.now()).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
            updatedAt = poll.updatedAt?.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
            options = options.map { opt ->
                PollOptionDto(
                    id = opt.id?.toString() ?: "",
                    pollId = poll.id?.toString() ?: "",
                    optionText = opt.optionText,
                    displayOrder = opt.displayOrder,
                    voteCount = opt.voteCount
                )
            },
            totalVotes = totalVotes,
            userVotedOptionIds = userVotedOptionIds,
            reportCount = poll.reportCount,
            isFlaggedSpam = poll.isFlaggedSpam,
            hasReported = hasReported
        )
    }
}
