package com.attendanceio.api.service

import com.attendanceio.api.controller.authentication.MobileAuthController
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler
import org.springframework.stereotype.Component

@Component
class CustomOAuth2SuccessHandler(
    @Value("\${app.frontend.url:https://attendanceio.paramsavjani.in}") private val frontendUrl: String,
    private val mobileAuthCodeService: MobileAuthCodeService
) : SimpleUrlAuthenticationSuccessHandler() {
    
    override fun onAuthenticationSuccess(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authentication: Authentication
    ) {
        val redirectUri = request.session.getAttribute(MobileAuthController.SESSION_REDIRECT_URI_KEY) as? String

        // Mobile flow: redirect back to app with a one-time code
        if (!redirectUri.isNullOrBlank()) {
            request.session.removeAttribute(MobileAuthController.SESSION_REDIRECT_URI_KEY)
            val principal = authentication.principal as? OAuth2User
            if (principal != null) {
                val code = mobileAuthCodeService.createFromOAuth2User(principal)
                val separator = if (redirectUri.contains("?")) "&" else "?"
                response.sendRedirect("$redirectUri${separator}code=$code")
                return
            }
        }

        // Web flow: redirect to frontend dashboard after successful login
        response.sendRedirect("$frontendUrl/dashboard")
    }
}
