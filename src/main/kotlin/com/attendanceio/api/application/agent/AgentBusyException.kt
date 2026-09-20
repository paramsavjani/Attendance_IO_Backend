package com.attendanceio.api.application.agent

/** The model provider throttled us and retries ran out; the message is meant for the user. */
class AgentBusyException(message: String, cause: Throwable) : RuntimeException(message, cause)
