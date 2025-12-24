package com.attendanceio.api.service

import com.attendanceio.api.controller.authentication.MobileAuthController
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
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

    private val log = LoggerFactory.getLogger(CustomOAuth2FailureHandler::class.java)
    
    override fun onAuthenticationFailure(
        request: HttpServletRequest,
        response: HttpServletResponse,
        exception: AuthenticationException
    ) {
        val session = request.getSession(false)
        val sessionId = session?.id
        val redirectUri = session?.getAttribute(MobileAuthController.SESSION_REDIRECT_URI_KEY) as? String
        log.warn(
            "OAuth2 FAILURE: sessionId={}, mobileRedirectPresent={}, path={}, remote={}, exType={}, exMsg={}",
            sessionId,
            !redirectUri.isNullOrBlank(),
            request.requestURI,
            request.remoteAddr,
            exception::class.java.name,
            exception.message
        )
        // Useful for root-cause (redirect_uri_mismatch etc.)
        exception.cause?.let { cause ->
            log.warn("OAuth2 FAILURE cause: {}: {}", cause::class.java.name, cause.message)
        }

        // Clear cookies and invalidate session on authentication failure
        clearCookiesAndSession(request, response)

        val errorMessage = when {
            exception.message?.contains("@dau.ac.in") == true -> 
                "Only @dau.ac.in Gmail accounts are allowed"
            else -> 
                "Authentication failed. Please try again."
        }
        
        val encodedError = URLEncoder.encode(errorMessage, StandardCharsets.UTF_8)

        if (!redirectUri.isNullOrBlank()) {
            val separator = if (redirectUri.contains("?")) "&" else "?"
            log.info("OAuth2 FAILURE (mobile): redirecting back to app. redirectUri={}", redirectUri)
            response.sendRedirect("$redirectUri${separator}error=$encodedError")
            return
        }

        val redirectUrl = "$frontendUrl/login?error=$encodedError"
        log.info("OAuth2 FAILURE (web): redirecting to {}", redirectUrl)
        response.sendRedirect(redirectUrl)
    }
    
    private fun clearCookiesAndSession(request: HttpServletRequest, response: HttpServletResponse) {
        // Invalidate the session
        val session = request.getSession(false)
        session?.let {
            try {
                it.invalidate()
                log.debug("Session invalidated: sessionId={}", it.id)
            } catch (e: IllegalStateException) {
                // Session already invalidated, ignore
                log.debug("Session already invalidated")
            }
        }
        
        // Clear JSESSIONID cookie
        val jsessionIdCookie = jakarta.servlet.http.Cookie("JSESSIONID", "")
        jsessionIdCookie.maxAge = 0
        jsessionIdCookie.path = "/"
        jsessionIdCookie.isHttpOnly = true
        // Set secure flag based on request scheme (HTTPS = secure)
        jsessionIdCookie.secure = request.isSecure || request.getHeader("X-Forwarded-Proto") == "https"
        response.addCookie(jsessionIdCookie)
        
        // Clear any other potential OAuth2-related cookies
        // Spring Security OAuth2 might set cookies with names like "OAUTH2_AUTHORIZATION_REQUEST" or similar
        request.cookies?.forEach { cookie ->
            if (cookie.name.startsWith("OAUTH2_") || 
                cookie.name.startsWith("SPRING_SECURITY_") ||
                cookie.name.contains("SESSION", ignoreCase = true)) {
                val clearCookie = jakarta.servlet.http.Cookie(cookie.name, "")
                clearCookie.maxAge = 0
                clearCookie.path = cookie.path ?: "/"
                clearCookie.domain = cookie.domain
                clearCookie.isHttpOnly = cookie.isHttpOnly
                clearCookie.secure = cookie.secure
                response.addCookie(clearCookie)
                log.debug("Cleared cookie: {}", cookie.name)
            }
        }
        
        log.info("Cleared cookies and invalidated session due to authentication failure")
    }
}

