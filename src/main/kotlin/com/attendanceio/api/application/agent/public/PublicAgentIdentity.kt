package com.attendanceio.api.application.agent.`public`

import com.attendanceio.api.application.agent.AgentCaller
import com.attendanceio.api.repository.student.StudentRepositoryAppAction
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.Locale

/**
 * Who is asking on the public page, and how much they get to see.
 *
 * Three tiers, because there are three genuinely different people here.
 *
 * Anyone can ask a few questions with no account at all — that is the whole point of a page you can
 * link to, and somebody who has to sign in before seeing anything work mostly just leaves. Past that
 * the page asks for a Google account, which turns "open a new incognito window" into "make a new
 * Google account" and is the difference between a daily allowance that means something and one that
 * does not. Both of those tiers see published institute information and nothing else.
 *
 * The third tier is a student of the institute signing in with their own `@dau.ac.in` account. They
 * are not a visitor to be shown a brochure — they are the person the app was built for, and there is
 * no reason the same assistant should pretend not to know them because they arrived at the demo's
 * address instead of the app's. So they are handed the caller the app itself would build, with their
 * student row attached, and from that point every difference between the two sites disappears:
 * [AgentCaller.isPublic] is false, so they get the student system prompt, the full tool set, and
 * results that are not redacted.
 *
 * That last point is why the check below is deliberately narrow. Three things must all hold, and any
 * one of them failing leaves someone in the ordinary signed-in tier rather than failing the request:
 *
 *  1. Google says the address is verified;
 *  2. the address is at the institute's domain, and where Google states the account's hosted domain
 *     (`hd`, which personal accounts do not carry) that statement agrees;
 *  3. a student row actually exists for it.
 *
 * Nobody outside the institute can satisfy (2), and an institute address with no student row gets
 * nothing extra, because there would be nothing of theirs to show.
 */
@Component
class PublicAgentIdentity(
    private val verifier: PublicAgentGoogleVerifier,
    private val students: StudentRepositoryAppAction,
    /** The institute's Google Workspace domain. Blank turns the student tier off entirely. */
    @Value("\${app.agent.public.institute-domain:dau.ac.in}") private val instituteDomain: String
) {
    private val logger = LoggerFactory.getLogger(PublicAgentIdentity::class.java)

    enum class Tier {
        /** No account. Published institute information, a handful of questions. */
        ANONYMOUS,

        /** A Google account that is not the institute's. Same information, a larger allowance. */
        SIGNED_IN,

        /** An institute account with a student row behind it. Treated exactly as the app treats them. */
        STUDENT
    }

    /** The student behind an institute account. Held for the length of the request and not stored. */
    data class Student(val email: String, val id: Long, val name: String?, val rollNumber: String?)

    data class Visitor(
        /** Stable, opaque, and the key everything is counted against. */
        val key: String,
        val tier: Tier,
        /** Their Google display name, when they signed in — used to greet them, never stored. */
        val name: String? = null,
        /** Set for [Tier.STUDENT] alone; null is what keeps everyone else on the public side. */
        val student: Student? = null
    ) {
        /** True for both signed-in tiers: they share an allowance, not a level of access. */
        val signedIn: Boolean get() = tier != Tier.ANONYMOUS

        val isStudent: Boolean get() = tier == Tier.STUDENT
    }

    /**
     * Resolves the caller from the `Authorization: Bearer <Google ID token>` header when it carries a
     * token we can verify, and from the connecting address otherwise. An unverifiable token is not an
     * error: it simply means this request is anonymous, and the daily allowance says the rest.
     */
    fun resolve(request: HttpServletRequest): Visitor {
        val account = verifier.verify(bearer(request))
            ?: return Visitor(key = "ip:${PublicAgentVisitor.fingerprint(request)}", tier = Tier.ANONYMOUS)
        val student = instituteStudent(account)
        if (student != null) {
            logger.info("public=STUDENT_ACCESS studentId={} roll={}", student.id, student.rollNumber)
        }
        return Visitor(
            key = "g:${PublicAgentVisitor.fingerprintOf("google|${account.subject}")}",
            tier = if (student != null) Tier.STUDENT else Tier.SIGNED_IN,
            name = account.name,
            student = student
        )
    }

    /**
     * The student row behind an institute account, or null for everybody else — which is the ordinary
     * outcome and never an error.
     */
    private fun instituteStudent(account: PublicAgentGoogleVerifier.GoogleAccount): Student? {
        if (instituteDomain.isBlank() || !account.emailVerified) return null
        val email = account.email?.trim()?.takeIf { it.isNotBlank() } ?: return null
        if (!email.lowercase(Locale.ROOT).endsWith("@${instituteDomain.lowercase(Locale.ROOT)}")) return null
        // Personal accounts carry no `hd` at all; when one is present it has to be the institute's,
        // so a Workspace account at another domain cannot ride in on a lookalike address.
        val hosted = account.hostedDomain?.trim()
        if (!hosted.isNullOrBlank() && !hosted.equals(instituteDomain, ignoreCase = true)) return null
        // Looked up as the token spells it first, since that is how the app's own login wrote the row.
        val row = students.findByEmail(email) ?: students.findByEmail(email.lowercase(Locale.ROOT)) ?: return null
        val id = row.id ?: return null
        return Student(email = email, id = id, name = row.name, rollNumber = row.sid)
    }

    /**
     * The agent's view of the same visitor.
     *
     * For a student this is the caller the app builds for them, `isPublic = false` and all — which is
     * what gives them the student prompt, the unfiltered tool set, and their own attendance. It also
     * keeps them out of the demo's shared answer cache, which only ever holds turns where
     * [AgentCaller.isPublic] is true: an answer about one person's attendance must never be replayed
     * to anybody else.
     *
     * For everyone else it is what it has always been: anonymous, with no student behind them.
     */
    fun caller(visitor: Visitor): AgentCaller {
        val student = visitor.student
        if (student != null) {
            return AgentCaller(
                email = student.email,
                studentId = student.id,
                name = student.name,
                rollNumber = student.rollNumber,
                isDemo = false,
                isPublic = false
            )
        }
        return AgentCaller(
            email = "${PublicAgentVisitor.PREFIX}:${visitor.key}",
            studentId = null,
            name = null,
            rollNumber = null,
            isDemo = false,
            isPublic = true
        )
    }

    private fun bearer(request: HttpServletRequest): String? =
        request.getHeader("Authorization")?.takeIf { it.startsWith("Bearer ", ignoreCase = true) }?.substring(7)
}
