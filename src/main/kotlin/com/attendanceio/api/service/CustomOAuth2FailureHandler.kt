package com.attendanceio.api.service

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler
import org.springframework.stereotype.Component
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@Component
class CustomOAuth2FailureHandler(
    @Value("\${app.frontend.url:https://attendanceio.paramsavjani.in}") private val frontendUrl: String
) : SimpleUrlAuthenticationFailureHandler() {
    
    override fun onAuthenticationFailure(
        request: HttpServletRequest,
        response: HttpServletResponse,
        exception: AuthenticationException
    ) {
        val errorMessage = when {
            exception.message?.contains("@dau.ac.in") == true -> 
                "Only @dau.ac.in Gmail accounts are allowed"
            else -> 
                "Authentication failed. Please try again."
        }
        
        val encodedError = URLEncoder.encode(errorMessage, StandardCharsets.UTF_8)
        val redirectUrl = "$frontendUrl/login?error=$encodedError"
        
        response.sendRedirect(redirectUrl)
    }
}

