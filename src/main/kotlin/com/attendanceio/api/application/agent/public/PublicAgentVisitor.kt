package com.attendanceio.api.application.agent.`public`

import com.attendanceio.api.application.agent.AgentCaller
import jakarta.servlet.http.HttpServletRequest
import java.security.MessageDigest

/**
 * Identifies an anonymous visitor of the public demo well enough to keep their conversation and
 * their daily allowance apart from everyone else's — and no better than that.
 *
 * The identity is a short hash of the client address, never the address itself: it is what appears
 * in logs, in Langfuse and as the owner of a demo conversation, so nothing here keeps a record of
 * who visited. Hashes are salted per boot, so they cannot be compared across restarts either.
 */
object PublicAgentVisitor {
    /** Prefix that makes a demo caller obvious wherever callers are logged or traced. */
    const val PREFIX = "public"

    private val salt: String = java.util.UUID.randomUUID().toString()

    fun caller(request: HttpServletRequest): AgentCaller = AgentCaller(
        email = "$PREFIX:${fingerprint(request)}",
        studentId = null,
        name = null,
        rollNumber = null,
        isDemo = false,
        isPublic = true
    )

    /** First hop of X-Forwarded-For (Caddy sets it), else the socket address. */
    fun clientAddress(request: HttpServletRequest): String =
        request.getHeader("X-Forwarded-For")?.split(',')?.firstOrNull()?.trim()?.takeIf { it.isNotBlank() }
            ?: request.remoteAddr
            ?: "unknown"

    fun fingerprint(request: HttpServletRequest): String = fingerprintOf(clientAddress(request))

    fun fingerprintOf(address: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest("$salt|$address".toByteArray())
            .take(6)
            .joinToString("") { "%02x".format(it) }
}
