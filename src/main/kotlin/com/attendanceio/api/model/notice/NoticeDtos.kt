package com.attendanceio.api.model.notice

data class CreateNoticeRequest(
    val subjectId: Long?,
    val title: String?,
    val message: String?,
    val noticeType: NoticeType?
)

data class ReportNoticeRequest(
    val reason: String? = null
)

data class NoticeCreatorDto(
    val id: String,
    val name: String,
    val pictureUrl: String? = null,
    val sid: String? = null
)

data class UrgentNoticeResponse(
    val id: String,
    val subjectId: String,
    val subjectCode: String? = null,
    val subjectName: String? = null,
    val createdBy: NoticeCreatorDto,
    val title: String,
    val message: String,
    val noticeType: NoticeType,
    val createdAt: String,
    val reportCount: Int = 0,
    val isFlaggedSpam: Boolean = false
)
