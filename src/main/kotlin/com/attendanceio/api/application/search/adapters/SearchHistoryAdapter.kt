package com.attendanceio.api.application.search.adapters

import com.attendanceio.api.model.search.DMSearchHistory
import com.attendanceio.api.model.search.SearchHistoryResponse

fun DMSearchHistory.toResponse(): SearchHistoryResponse? {
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
