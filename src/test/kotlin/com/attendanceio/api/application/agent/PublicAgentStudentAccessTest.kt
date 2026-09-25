package com.attendanceio.api.application.agent

import com.attendanceio.api.application.agent.`public`.PublicAgentGoogleVerifier
import com.attendanceio.api.application.agent.`public`.PublicAgentIdentity
import com.attendanceio.api.model.student.DMStudent
import com.attendanceio.api.repository.student.StudentRepositoryAppAction
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.mock.web.MockHttpServletRequest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The demo gives a student of the institute the app's own view of their data, and gives everyone
 * else published information and nothing more. That is a boundary, so every way of landing on the
 * wrong side of it is pinned here: the wrong domain, an unverified address, a Workspace account at
 * a lookalike domain, and an institute address with no student behind it.
 *
 * Each of those falls back to the ordinary signed-in tier rather than failing the request — someone
 * mis-recognised should still get an answer about placements.
 */
class PublicAgentStudentAccessTest {
    private val token = "a.b.c"

    private fun account(
        email: String?,
        verified: Boolean = true,
        hostedDomain: String? = "dau.ac.in"
    ) = PublicAgentGoogleVerifier.GoogleAccount(
        subject = "subject-1",
        name = "A Student",
        emailVerified = verified,
        email = email,
        hostedDomain = hostedDomain
    )

    private fun student(id: Long = 790L, sid: String = "202301001") = DMStudent().apply {
        this.id = id
        this.sid = sid
        this.name = "A Student"
    }

    private fun identity(
        account: PublicAgentGoogleVerifier.GoogleAccount?,
        rows: Map<String, DMStudent> = emptyMap(),
        domains: String = "dau.ac.in,daiict.ac.in"
    ): PublicAgentIdentity {
        val verifier = Mockito.mock(PublicAgentGoogleVerifier::class.java)
        Mockito.`when`(verifier.verify(token)).thenReturn(account)
        val students = Mockito.mock(StudentRepositoryAppAction::class.java)
        rows.forEach { (email, row) -> Mockito.`when`(students.findByEmail(email)).thenReturn(row) }
        return PublicAgentIdentity(verifier, students, domains)
    }

    private fun request(bearer: String? = token) = MockHttpServletRequest().apply {
        remoteAddr = "49.36.10.7"
        if (bearer != null) addHeader("Authorization", "Bearer $bearer")
    }

    @Test
    fun `an institute account with a student row gets the app's own view`() {
        val email = "student@dau.ac.in"
        val identity = identity(account(email), mapOf(email to student()))

        val visitor = identity.resolve(request())
        assertEquals(PublicAgentIdentity.Tier.STUDENT, visitor.tier)
        assertTrue(visitor.isStudent)

        val caller = identity.caller(visitor)
        assertFalse(caller.isPublic, "a student is not on the public side — this is what unlocks their own data")
        assertEquals(790L, caller.studentId)
        assertEquals("202301001", caller.rollNumber)
        assertEquals(email, caller.email)
    }

    @Test
    fun `the older daiict address is an institute account too`() {
        val email = "student@daiict.ac.in"
        val identity = identity(account(email, hostedDomain = "daiict.ac.in"), mapOf(email to student()))

        val visitor = identity.resolve(request())
        assertEquals(PublicAgentIdentity.Tier.STUDENT, visitor.tier)
        assertFalse(identity.caller(visitor).isPublic)
    }

    @Test
    fun `an address at one domain with the other's hd is still ours`() {
        // Google states the account's domain; both spellings belong to the institute, so a mismatch
        // between the two of them is not somebody else's Workspace.
        val email = "student@daiict.ac.in"
        val identity = identity(account(email, hostedDomain = "dau.ac.in"), mapOf(email to student()))

        assertEquals(PublicAgentIdentity.Tier.STUDENT, identity.resolve(request()).tier)
    }

    @Test
    fun `a personal Google account sees published information only`() {
        val identity = identity(account("someone@gmail.com", hostedDomain = null))

        val visitor = identity.resolve(request())
        assertEquals(PublicAgentIdentity.Tier.SIGNED_IN, visitor.tier)

        val caller = identity.caller(visitor)
        assertTrue(caller.isPublic)
        assertNull(caller.studentId)
    }

    @Test
    fun `an unverified institute address is not enough`() {
        val email = "student@dau.ac.in"
        val identity = identity(account(email, verified = false), mapOf(email to student()))

        assertEquals(PublicAgentIdentity.Tier.SIGNED_IN, identity.resolve(request()).tier)
    }

    @Test
    fun `a Workspace account at another domain cannot ride in on the address`() {
        val email = "student@dau.ac.in"
        val identity = identity(account(email, hostedDomain = "example.com"), mapOf(email to student()))

        assertEquals(PublicAgentIdentity.Tier.SIGNED_IN, identity.resolve(request()).tier)
    }

    @Test
    fun `a lookalike domain does not end with the institute's`() {
        val email = "student@notdau.ac.in"
        val identity = identity(account(email, hostedDomain = null), mapOf(email to student()))

        assertEquals(PublicAgentIdentity.Tier.SIGNED_IN, identity.resolve(request()).tier)
    }

    @Test
    fun `an institute address with no student row gets nothing extra`() {
        val identity = identity(account("staff@dau.ac.in"))

        assertEquals(PublicAgentIdentity.Tier.SIGNED_IN, identity.resolve(request()).tier)
        assertTrue(identity.caller(identity.resolve(request())).isPublic)
    }

    @Test
    fun `a blank institute domain turns the student tier off entirely`() {
        val email = "student@dau.ac.in"
        val identity = identity(account(email), mapOf(email to student()), domains = "")

        assertEquals(PublicAgentIdentity.Tier.SIGNED_IN, identity.resolve(request()).tier)
    }

    @Test
    fun `no token at all is still anonymous`() {
        val identity = identity(account("student@dau.ac.in"))

        val visitor = identity.resolve(request(bearer = null))
        assertEquals(PublicAgentIdentity.Tier.ANONYMOUS, visitor.tier)
        assertFalse(visitor.signedIn)
    }

    @Test
    fun `both signed-in tiers share one allowance`() {
        val email = "student@dau.ac.in"
        val asStudent = identity(account(email), mapOf(email to student())).resolve(request())
        val asVisitor = identity(account("someone@gmail.com", hostedDomain = null)).resolve(request())

        assertTrue(asStudent.signedIn, "the student tier still counts as signed in for the daily cap")
        assertTrue(asVisitor.signedIn)
    }
}
