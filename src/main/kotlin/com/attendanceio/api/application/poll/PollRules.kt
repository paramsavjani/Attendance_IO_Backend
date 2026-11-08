package com.attendanceio.api.application.poll

import java.time.Duration

/** Limits for class polls, kept in one place so the API and its error messages agree. */
object PollRules {
    const val QUESTION_MIN = 3
    const val QUESTION_MAX = 200
    const val OPTION_MAX = 80
    const val MIN_OPTIONS = 2
    const val MAX_OPTIONS = 6

    /** How long a poll can stay open: 1 hour, 6 hours, 1 day, 3 days, 1 week. */
    val DURATION_HOURS = listOf(1, 6, 24, 72, 168)

    /** Each poll pings a whole class, so creating them is capped per rolling day. */
    const val DAILY_CREATE_LIMIT = 3

    /** Distinct classmates whose reports hide a poll from everyone but its creator. */
    const val REPORTS_TO_HIDE = 3

    /** Ended polls stay in the feed this long so people can see how they turned out. */
    val ENDED_VISIBLE_FOR: Duration = Duration.ofDays(3)

    const val FEED_LIMIT = 50
}

/** A rejected poll request, carrying the HTTP status the controller should answer with. */
class PollException(val status: Int, message: String) : RuntimeException(message) {
    companion object {
        fun badRequest(message: String) = PollException(400, message)
        fun forbidden(message: String) = PollException(403, message)
        fun notFound() = PollException(404, "Poll not found")
        fun conflict(message: String) = PollException(409, message)
        fun tooMany(message: String) = PollException(429, message)
    }
}
