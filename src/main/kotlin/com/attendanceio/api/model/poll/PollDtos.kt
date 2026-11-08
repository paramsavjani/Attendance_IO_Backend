package com.attendanceio.api.model.poll

data class CreatePollRequest(
    val subjectId: Long?,
    val question: String?,
    val options: List<String>?,
    val allowMultiple: Boolean?,
    val anonymous: Boolean?,
    /** One of [com.attendanceio.api.application.poll.PollRules.DURATION_HOURS]. */
    val durationHours: Int?
)

data class CastVoteRequest(val optionIds: List<Long>?)

data class ReportPollRequest(val reason: String?)

data class PollSubjectResponse(
    val id: Long,
    val code: String,
    val name: String,
    val color: String
)

data class PollOptionResponse(
    val id: Long,
    val text: String,
    val votes: Int
)

data class PollResponse(
    val id: Long,
    val subject: PollSubjectResponse,
    val question: String,
    val options: List<PollOptionResponse>,
    val allowMultiple: Boolean,
    val anonymous: Boolean,
    /** Null when the poll is anonymous and the caller is not its creator. */
    val creatorName: String?,
    val isMine: Boolean,
    /** Distinct students who voted. */
    val totalVoters: Int,
    val myOptionIds: List<Long>,
    val isOpen: Boolean,
    /** Hidden by classmate reports. After that only its creator keeps seeing it. */
    val hidden: Boolean,
    val reportedByMe: Boolean,
    /** Epoch millis, so clients never have to guess the server's time zone. */
    val createdAt: Long,
    val endsAt: Long
)

data class PollFeedResponse(
    val polls: List<PollResponse>,
    /** Open polls the caller can still vote on. Drives the badge on the home screen. */
    val unvotedOpenCount: Int,
    val canCreate: Boolean,
    /** Too many of the caller's polls were hidden by reports; they can no longer ask new ones. */
    val blocked: Boolean,
    val createdToday: Int,
    val dailyLimit: Int
)
