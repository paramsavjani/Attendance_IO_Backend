package com.attendanceio.api.application.agent

/**
 * Thrown before a turn starts when the caller has used today's message quota. Maps to HTTP 429, and
 * the message is shown to the user verbatim — the public demo passes its own wording, since "come
 * back after midnight" reads oddly to a visitor who never signed in.
 *
 * [subscriptionOffer] marks a message that carries [AgentSubscriptionOffer]'s question, so the app
 * can show Yes/No buttons under it; an older app just shows the text and the student types the answer.
 */
class AgentDailyLimitExceededException(
    val limit: Int,
    val used: Long,
    message: String = "You've used all $limit messages for today. Come back after 12 midnight for a fresh set.",
    val subscriptionOffer: Boolean = false
) : RuntimeException(message)
