package com.attendanceio.api.application.agent

import com.attendanceio.api.application.agent.actions.AgentCampusInfoQueryAppAction
import com.attendanceio.api.application.agent.actions.AgentCollegeQueryAppAction
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import kotlin.test.assertTrue

/**
 * Runs the institute-knowledge queries against a real MongoDB (set KB_MONGO_URI to enable, e.g. an
 * SSH tunnel to the VPS). Not part of the normal build: it checks the document mapping and the
 * lookup heuristics against the imported data, which no mock can.
 */
@DataMongoTest(properties = ["spring.mongodb.uri=\${KB_MONGO_URI}"])
@Import(AgentCollegeQueryAppAction::class, AgentCampusInfoQueryAppAction::class)
@EnabledIfEnvironmentVariable(named = "KB_MONGO_URI", matches = ".+")
class CollegeKnowledgeLiveTest {
    @Autowired private lateinit var college: AgentCollegeQueryAppAction
    @Autowired private lateinit var info: AgentCampusInfoQueryAppAction

    @Test
    fun `clubs and members resolve by short name`() {
        val cult = college.getClub("cult", "convener")
        println("club: ${cult?.name} → ${cult?.members}")
        assertTrue(cult != null && cult.members.any { it.phone != null }, "convener with phone expected")
        val hmc = college.findClubs("HMC", null, 5)
        assertTrue(hmc.items.any { it.name.contains("Hostel", true) }, hmc.toString())
    }

    @Test
    fun `holidays contacts curriculum programmes`() {
        val h = info.holidays(2026, null, null)
        println("holidays: ${h.totalCount} first=${h.items.firstOrNull()}")
        assertTrue(h.totalCount >= 15)

        val w = info.findContacts("warden", null, 10)
        println("wardens: ${w.items.map { "${it.name} ${it.phones} ${it.email}" }}")
        assertTrue(w.items.isNotEmpty() && w.items.any { it.phones.isNotEmpty() })

        val c = info.curriculum("ICT", 3, null)
        println("ICT sem 3: ${(c?.get("semesters") as List<*>).firstOrNull()}")
        assertTrue(c != null && (c["semesters"] as List<*>).isNotEmpty())
        val dsa = info.curriculum("csai", null, "data structures")
        println("CSAI DSA: ${dsa?.get("semesters")}")
        assertTrue(dsa != null && (dsa["semesters"] as List<*>).isNotEmpty())

        val cal = college.academicCalendar(null, "Autumn", "exam")
        println("calendar exams: ${cal.items.map { it.event + " " + it.dates }}")
        assertTrue(cal.items.isNotEmpty())
    }
}
