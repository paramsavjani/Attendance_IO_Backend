package com.attendanceio.api.model.poll

import java.time.LocalDateTime

data class CreatePollRequest(
    val subjectId: Long,
    val title: String,
    val description: String? = null,
    val pollType: PollType = PollType.SINGLE_CHOICE,
    val options: List<String>,
    val endDate: LocalDateTime? = null,
    val notifyStudents: Boolean = true
)

data class CastVoteRequest(
    val optionIds: List<Long>
)

data class ReportPollRequest(
    val reason: PollReportReason = PollReportReason.SPAM,
    val details: String? = null,
    val reportAuthorAsSpammer: Boolean = false
)

data class PollNotificationPreferencesDto(
    val notifyNewPolls: Boolean = true,
    val notifyPollResults: Boolean = true
)

data class PollCreatorDto(
    val id: String,
    val name: String,
    val pictureUrl: String? = null,
    val sid: String? = null
)

data class PollOptionDto(
    val id: String,
    val pollId: String,
    val optionText: String,
    val displayOrder: Int,
    val voteCount: Int
)

data class PollResponse(
    val id: String,
    val subjectId: String,
    val subjectCode: String? = null,
    val subjectName: String? = null,
    val createdBy: PollCreatorDto,
    val title: String,
    val description: String? = null,
    val pollType: PollType,
    val status: PollStatus,
    val endDate: String? = null,
    val createdAt: String,
    val updatedAt: String? = null,
    val options: List<PollOptionDto>,
    val totalVotes: Int,
    val userVotedOptionIds: List<String> = emptyList(),
    val reportCount: Int = 0,
    val isFlaggedSpam: Boolean = false,
    val hasReported: Boolean = false
)

data class ReportPollResponse(
    val isHidden: Boolean,
    val reportCount: Int,
    val message: String
)
