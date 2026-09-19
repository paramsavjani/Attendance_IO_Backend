package com.attendanceio.api.application.agent

/** Thrown before a turn starts when the caller has used today's message quota. Maps to HTTP 429. */
class AgentDailyLimitExceededException(val limit: Int, val used: Long) : RuntimeException(
    "You've used all $limit messages for today. Come back after 12 midnight for a fresh set."
)
