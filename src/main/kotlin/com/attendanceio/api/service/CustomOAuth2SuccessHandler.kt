package com.attendanceio.api.service

import com.attendanceio.api.controller.authentication.MobileAuthController
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
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

    private val log = LoggerFactory.getLogger(CustomOAuth2SuccessHandler::class.java)
    
    override fun onAuthenticationSuccess(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authentication: Authentication
    ) {
        val redirectUri = request.session.getAttribute(MobileAuthController.SESSION_REDIRECT_URI_KEY) as? String
        val sessionId = request.getSession(false)?.id
        val principal = authentication.principal as? OAuth2User
        val email = principal?.getAttribute<String>("email")
        log.info(
            "OAuth2 SUCCESS: sessionId={}, email={}, mobileRedirectPresent={}, path={}, remote={}",
            sessionId,
            email,
            !redirectUri.isNullOrBlank(),
            request.requestURI,
            request.remoteAddr
        )

        // Mobile flow: redirect back to app with a one-time code
        if (!redirectUri.isNullOrBlank()) {
            request.session.removeAttribute(MobileAuthController.SESSION_REDIRECT_URI_KEY)
            if (principal != null) {
                val code = mobileAuthCodeService.createFromOAuth2User(principal)
                val separator = if (redirectUri.contains("?")) "&" else "?"
                log.info("OAuth2 SUCCESS (mobile): redirecting back to app. redirectUri={}", redirectUri)
                response.sendRedirect("$redirectUri${separator}code=$code")
                return
            }
            log.warn("OAuth2 SUCCESS (mobile): principal is not OAuth2User; falling back to web redirect.")
        }

        // Web flow: redirect to frontend dashboard after successful login
        log.info("OAuth2 SUCCESS (web): redirecting to {}", "$frontendUrl/dashboard")
        response.sendRedirect("$frontendUrl/dashboard")
    }
}
