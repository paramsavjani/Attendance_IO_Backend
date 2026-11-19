package com.attendanceio.api.application.placement

import com.attendanceio.api.application.agent.AgentCaller

/**
 * Placement data (companies, packages, job descriptions and who was selected) comes from the
 * placement portal, which needs a student login, and it names students. So it is shown only to a
 * signed-in DAU student: an `@dau.ac.in` account that is not the shared demo login and not a visitor
 * of the public assistant. Checked in code at every entry point — the REST endpoints, each assistant
 * tool, and the tool router — never left to the system prompt.
 */
object PlacementAccess {
    const val DOMAIN = "@dau.ac.in"
    const val DENIED = "Placement data is only for DAU students signed in with their @dau.ac.in account."

    fun allowed(email: String?, isDemo: Boolean = false, isPublic: Boolean = false): Boolean =
        !isDemo && !isPublic && email != null && email.trim().lowercase().endsWith(DOMAIN)

    fun allowed(caller: AgentCaller): Boolean = allowed(caller.email, caller.isDemo, caller.isPublic)
}
