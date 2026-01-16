package com.attendanceio.api.util

import org.springframework.security.core.GrantedAuthority
import org.springframework.security.oauth2.core.user.OAuth2User

object DemoUserUtil {
    /**
     * Demo student ID - demo users will see this student's data
     */
    const val DEMO_STUDENT_ID = 790L
    
    /**
     * Check if the current user is a demo user
     */
    fun isDemoUser(oauth2User: OAuth2User?): Boolean {
        if (oauth2User == null) return false
        
        val email = oauth2User.getAttribute<String>("email") ?: ""
        
        // Check if email doesn't end with @dau.ac.in
        return !email.endsWith("@dau.ac.in")
    }
    
    /**
     * Get the student ID to use - returns DEMO_STUDENT_ID for demo users, actual student ID for regular users
     */
    fun getStudentIdForUser(oauth2User: OAuth2User?, actualStudentId: Long?): Long? {
        return if (isDemoUser(oauth2User)) {
            DEMO_STUDENT_ID
        } else {
            actualStudentId
        }
    }
    
    /**
     * Get demo error response
     */
    fun getDemoErrorResponse(): Map<String, Any> {
        return mapOf(
            "error" to "Demo mode",
            "message" to "This action is not available in demo mode. Demo users can only view data.",
            "isDemo" to true
        )
    }
}
