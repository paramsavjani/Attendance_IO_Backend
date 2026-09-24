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

    /**
     * The client's address, taken from the **last** hop of X-Forwarded-For rather than the first.
     *
     * Caddy appends the connecting address to whatever the request already carried, so the first
     * entry is whatever the caller chose to send — a visitor who wants a fresh daily allowance only
     * has to add the header. The last entry is the one our own proxy wrote.
     */
    fun clientAddress(request: HttpServletRequest): String =
        request.getHeader("X-Forwarded-For")
            ?.split(',')
            ?.map { it.trim() }
            ?.lastOrNull { it.isNotBlank() }
            ?: request.remoteAddr
            ?: "unknown"

    fun fingerprint(request: HttpServletRequest): String = fingerprintOf(clientAddress(request))

    fun fingerprintOf(address: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest("$salt|${normalise(address)}".toByteArray())
            .take(6)
            .joinToString("") { "%02x".format(it) }

    /**
     * One visitor, one allowance — as close as an address gets you.
     *
     * IPv6 is handed out a /64 at a time, so counting whole addresses would let the same person (or
     * the same script) start over as often as they liked; the prefix is counted instead. IPv4
     * addresses are counted whole. Neither is proof of anything, which is why the overall daily
     * ceiling is what actually caps the bill.
     */
    fun normalise(address: String): String {
        var bare = address.substringBefore('%').trim()
        // "[2401:4900::1]:443" -> "2401:4900::1"
        if (bare.startsWith("[")) bare = bare.removePrefix("[").substringBefore("]")
        // "49.36.10.7:51234" -> "49.36.10.7"
        if (bare.count { it == ':' } == 1 && bare.contains('.')) bare = bare.substringBefore(':')
        // Plain IPv4, and the IPv4-mapped form "::ffff:49.36.10.7", are counted whole.
        val tail = bare.substringAfterLast(':')
        if (tail.count { it == '.' } == 3) return tail
        if (!bare.contains(':')) return bare
        // "2401:4900:1c31:abcd:1:2:3:4" -> "2401:4900:1c31:abcd::/64"
        return bare.split(':').take(4).joinToString(":") + "::/64"
    }
}
