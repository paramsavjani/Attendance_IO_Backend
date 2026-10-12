package com.attendanceio.api.application.feedback.actions

import com.attendanceio.api.model.feedback.DMFeedback
import com.attendanceio.api.model.feedback.FeedbackReply
import com.attendanceio.api.model.feedback.MyFeedbackItem
import com.attendanceio.api.model.feedback.MyFeedbackResponse
import com.attendanceio.api.repository.feedback.FeedbackRepositoryAppAction
import org.springframework.stereotype.Component

/**
 * A student's own feedback threads, newest first, each with the replies written from the admin
 * site. Scoped to one student id by the caller — nothing here takes a student from the request.
 */
@Component
class GetMyFeedbackAppAction(
    private val feedback: FeedbackRepositoryAppAction
) {
    fun execute(studentId: Long): MyFeedbackResponse {
        val threads = feedback.findMine(studentId)
        val messages = feedback.messagesFor(threads.mapNotNull { it.id })

        val items = threads.map { thread -> toItem(thread, messages) }
        val unread = items.filter { it.hasUnreadReply }
        return MyFeedbackResponse(
            items = items,
            unreadCount = unread.size,
            latestUnread = unread.firstOrNull()
        )
    }

    private fun toItem(
        thread: DMFeedback,
        messages: Map<Long, List<com.attendanceio.api.model.feedback.DMFeedbackMessage>>
    ): MyFeedbackItem {
        val id = thread.id ?: 0L
        return MyFeedbackItem(
            id = id,
            type = thread.type.name,
            title = thread.title,
            description = thread.description,
            status = thread.status?.name ?: "PENDING",
            createdAt = thread.createdAt?.toString() ?: "",
            replies = messages[id].orEmpty().map { message ->
                FeedbackReply(
                    id = message.id ?: 0L,
                    body = message.body,
                    author = message.author.name,
                    createdAt = message.createdAt?.toString() ?: ""
                )
            },
            hasUnreadReply = thread.hasUnreadReply
        )
    }
}
