package com.attendanceio.api.model.feedback

/** One reply on a thread, as the app shows it. */
data class FeedbackReply(
    val id: Long,
    val body: String,
    /** OWNER for everything today; the field exists so the app can render sides if students ever reply. */
    val author: String,
    val createdAt: String
)

data class MyFeedbackItem(
    val id: Long,
    val type: String,
    val title: String,
    val description: String,
    val createdAt: String,
    val replies: List<FeedbackReply>,
    /** True when a reply landed after the student last opened this thread. */
    val hasUnreadReply: Boolean
)

data class MyFeedbackResponse(
    val items: List<MyFeedbackItem>,
    val unreadCount: Int,
    /**
     * The newest unread thread, so the app can show the "you got a reply" dialog without
     * scanning the list itself. Null when there is nothing new.
     */
    val latestUnread: MyFeedbackItem?
)
