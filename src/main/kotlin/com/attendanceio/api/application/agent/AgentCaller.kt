package com.attendanceio.api.application.agent

/**
 * Who is asking. Resolved once per request from the authenticated principal and passed to the
 * tools explicitly (through Spring AI's ToolContext), because tool execution may happen off the
 * request thread where there is no security context to read.
 *
 * [studentId] is null for a signed-in account that has no student row (a demo login from a
 * non-institute email); "my …" tools then explain rather than fail.
 */
data class AgentCaller(
    val email: String,
    val studentId: Long?,
    val name: String?,
    val rollNumber: String?,
    val isDemo: Boolean
)
