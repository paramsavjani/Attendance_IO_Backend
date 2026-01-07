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
    private val mobileAuthCodeService: MobileAuthCodeService,
    private val jwtService: JwtService
) : SimpleUrlAuthenticationSuccessHandler() {

    private val log = LoggerFactory.getLogger(CustomOAuth2SuccessHandler::class.java)
    
    override fun onAuthenticationSuccess(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authentication: Authentication
    ) {
        val redirectUri = request.session.getAttribute(MobileAuthController.SESSION_REDIRECT_URI_KEY) as? String
        val principal = authentication.principal as? OAuth2User
        val email = principal?.getAttribute<String>("email")
        val sessionId = request.getSession(false)?.id
        log.info(
            "OAuth2 SUCCESS: sessionId={}, email={}, mobileRedirectPresent={}, path={}, remote={}",
            sessionId,
            email,
            !redirectUri.isNullOrBlank(),
            request.requestURI,
            request.remoteAddr
        )

        if (email == null) {
            log.warn("OAuth2 SUCCESS: email is null, cannot generate token")
            response.sendRedirect("$frontendUrl/login?error=authentication_failed")
            return
        }

        // Generate JWT token for new clients
        val token = jwtService.generateToken(email)
        log.debug("OAuth2 SUCCESS: Generated JWT token for email={}. Session also created for backward compatibility.", email)

        // Note: Session is automatically created by Spring Security for backward compatibility
        // New clients will use JWT token, old clients will continue using session cookies

        // Mobile flow: redirect back to app with a one-time code and JWT token
        if (!redirectUri.isNullOrBlank()) {
            request.session.removeAttribute(MobileAuthController.SESSION_REDIRECT_URI_KEY)
            if (principal != null) {
                val code = mobileAuthCodeService.createFromOAuth2User(principal)
                val separator = if (redirectUri.contains("?")) "&" else "?"
                log.info("OAuth2 SUCCESS (mobile): redirecting back to app. redirectUri={}", redirectUri)
                // Include both code (for old clients) and token (for new clients)
                response.sendRedirect("$redirectUri${separator}code=$code&token=$token")
                return
            }
            log.warn("OAuth2 SUCCESS (mobile): principal is not OAuth2User; falling back to web redirect.")
        }

        // Web flow: redirect to frontend with JWT token
        // Session cookie is also set automatically for backward compatibility
        log.info("OAuth2 SUCCESS (web): redirecting to frontend with token. Session cookie also set.")
        response.sendRedirect("$frontendUrl/login?token=$token")
    }
}
