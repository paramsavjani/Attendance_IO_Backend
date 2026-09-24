package com.attendanceio.api.application.agent.`public`

import com.attendanceio.api.application.agent.AgentCaller
import jakarta.servlet.http.HttpServletRequest
import org.springframework.stereotype.Component

/**
 * Who is asking on the public page, and how much they get to ask.
 *
 * Two tiers, because the two problems are different. Anyone can ask a few questions with no account
 * at all — that is the whole point of a page you can link to, and somebody who has to sign in before
 * seeing anything work mostly just leaves. Past that the page asks for a Google account, which turns
 * "open a new incognito window" into "make a new Google account" and is the difference between a
 * daily allowance that means something and one that does not.
 *
 * Neither tier is trusted with an identity beyond a short hash — an address for visitors, the Google
 * subject id for members. Nothing here stores who anybody is.
 */
@Component
class PublicAgentIdentity(
    private val verifier: PublicAgentGoogleVerifier
) {
    enum class Tier { ANONYMOUS, SIGNED_IN }

    data class Visitor(
        /** Stable, opaque, and the key everything is counted against. */
        val key: String,
        val tier: Tier,
        /** Their Google display name, when they signed in — used to greet them, never stored. */
        val name: String? = null
    ) {
        val signedIn: Boolean get() = tier == Tier.SIGNED_IN
    }

    /**
     * Resolves the caller from the `Authorization: Bearer <Google ID token>` header when it carries a
     * token we can verify, and from the connecting address otherwise. An unverifiable token is not an
     * error: it simply means this request is anonymous, and the daily allowance says the rest.
     */
    fun resolve(request: HttpServletRequest): Visitor {
        val account = verifier.verify(bearer(request))
        return if (account != null) {
            Visitor(
                key = "g:${PublicAgentVisitor.fingerprintOf("google|${account.subject}")}",
                tier = Tier.SIGNED_IN,
                name = account.name
            )
        } else {
            Visitor(key = "ip:${PublicAgentVisitor.fingerprint(request)}", tier = Tier.ANONYMOUS)
        }
    }

    /** The agent's view of the same visitor: anonymous either way, with no student behind them. */
    fun caller(visitor: Visitor): AgentCaller = AgentCaller(
        email = "${PublicAgentVisitor.PREFIX}:${visitor.key}",
        studentId = null,
        name = null,
        rollNumber = null,
        isDemo = false,
        isPublic = true
    )

    private fun bearer(request: HttpServletRequest): String? =
        request.getHeader("Authorization")?.takeIf { it.startsWith("Bearer ", ignoreCase = true) }?.substring(7)
}
