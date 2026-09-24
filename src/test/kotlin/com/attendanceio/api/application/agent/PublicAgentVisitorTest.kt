package com.attendanceio.api.application.agent

import com.attendanceio.api.application.agent.`public`.PublicAgentVisitor
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * The demo's daily allowance is counted per visitor, and a visitor is an address — so how the
 * address is read decides whether the cap means anything.
 */
class PublicAgentVisitorTest {
    @Test
    fun `an IPv6 visitor gets one allowance for their whole prefix, not one per address`() {
        val first = PublicAgentVisitor.normalise("2401:4900:1c31:abcd:1:2:3:4")
        val second = PublicAgentVisitor.normalise("2401:4900:1c31:abcd:9999:8888:7777:6666")
        assertEquals(first, second, "addresses in one /64 are the same visitor")
        assertEquals("2401:4900:1c31:abcd::/64", first)
    }

    @Test
    fun `a different prefix is a different visitor`() =
        assertNotEquals(
            PublicAgentVisitor.normalise("2401:4900:1c31:abcd::1"),
            PublicAgentVisitor.normalise("2401:4900:1c31:0000::1")
        )

    @Test
    fun `IPv4 addresses are counted whole, port and zone ignored`() {
        assertEquals("49.36.10.7", PublicAgentVisitor.normalise("49.36.10.7"))
        assertEquals("49.36.10.7", PublicAgentVisitor.normalise("49.36.10.7:51234"))
        assertEquals("fe80::1::/64", PublicAgentVisitor.normalise("fe80::1%eth0"))
        // The same visitor arriving as an IPv4-mapped IPv6 address must count as that IPv4 address.
        assertEquals("49.36.10.7", PublicAgentVisitor.normalise("::ffff:49.36.10.7"))
        assertEquals("2401:4900:1c31:abcd::/64", PublicAgentVisitor.normalise("[2401:4900:1c31:abcd::1]:443"))
    }

    @Test
    fun `the same address always hashes the same way, a different one does not`() {
        assertEquals(PublicAgentVisitor.fingerprintOf("49.36.10.7"), PublicAgentVisitor.fingerprintOf("49.36.10.7"))
        assertNotEquals(PublicAgentVisitor.fingerprintOf("49.36.10.7"), PublicAgentVisitor.fingerprintOf("49.36.10.8"))
    }

    @Test
    fun `the hash keeps no trace of the address it came from`() {
        val address = "49.36.10.7"
        val fingerprint = PublicAgentVisitor.fingerprintOf(address)
        assertEquals(12, fingerprint.length)
        assertEquals(false, fingerprint.contains("49"), "a fingerprint must not carry the address")
    }
}
