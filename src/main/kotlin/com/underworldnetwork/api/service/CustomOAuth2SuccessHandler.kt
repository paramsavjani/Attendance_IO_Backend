package com.underworldnetwork.api.service

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.Authentication
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler
import org.springframework.stereotype.Component

@Component
class CustomOAuth2SuccessHandler : SimpleUrlAuthenticationSuccessHandler() {
    
    init {
        setDefaultTargetUrl("/api/user/me")
    }
    
    override fun onAuthenticationSuccess(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authentication: Authentication
    ) {
        // Redirect to API endpoint after successful login
        super.onAuthenticationSuccess(request, response, authentication)
    }
}
