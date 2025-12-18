package com.attendanceio.api.controller.authentication

import com.attendanceio.api.service.MobileAuthCodeService
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.ResponseEntity
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken
import org.springframework.security.oauth2.core.user.DefaultOAuth2User
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.security.web.context.HttpSessionSecurityContextRepository
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.net.URI

@RestController
@RequestMapping("/api/auth/mobile")
class MobileAuthController(
    private val mobileAuthCodeService: MobileAuthCodeService
) {
    companion object {
        const val SESSION_REDIRECT_URI_KEY = "MOBILE_REDIRECT_URI"
        private const val ALLOWED_REDIRECT_SCHEME = "com.attendanceio.app"
        private const val ALLOWED_REDIRECT_HOST = "auth"
    }

    /**
     * Starts Google OAuth in the system browser and records a deep-link redirect_uri
     * in the browser session, so the OAuth success handler knows where to redirect.
     */
    @GetMapping("/google/start")
    fun startGoogleLogin(
        @RequestParam("redirect_uri") redirectUri: String,
        request: HttpServletRequest,
        response: HttpServletResponse
    ) {
        val uri = runCatching { URI.create(redirectUri) }.getOrNull()
            ?: run {
                response.sendError(400, "Invalid redirect_uri")
                return
            }

        if (uri.scheme != ALLOWED_REDIRECT_SCHEME || uri.host != ALLOWED_REDIRECT_HOST) {
            response.sendError(400, "redirect_uri not allowed")
            return
        }

        request.session.setAttribute(SESSION_REDIRECT_URI_KEY, redirectUri)
        response.sendRedirect("/oauth2/authorization/google")
    }

    data class ExchangeRequest(val code: String)

    /**
     * Exchanges the one-time code (received via deep link) into a server session.
     * The Capacitor WebView then uses that session cookie for future API requests.
     */
    @PostMapping("/exchange")
    fun exchange(
        @RequestBody body: ExchangeRequest,
        request: HttpServletRequest
    ): ResponseEntity<Map<String, Any>> {
        val consumed = mobileAuthCodeService.consume(body.code)
            ?: return ResponseEntity.status(401).body(mapOf("error" to "Invalid or expired code"))

        val authorities = listOf(SimpleGrantedAuthority("ROLE_USER"))
        val nameAttributeKey = if (consumed.attributes.containsKey("sub")) "sub" else "email"

        val principal: OAuth2User = DefaultOAuth2User(authorities, consumed.attributes, nameAttributeKey)
        val auth = OAuth2AuthenticationToken(principal, authorities, "google")

        // Rotate session id (session fixation defense) and persist security context to session.
        request.changeSessionId()
        SecurityContextHolder.getContext().authentication = auth
        request.getSession(true).setAttribute(
            HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
            SecurityContextHolder.getContext()
        )

        return ResponseEntity.ok(mapOf("status" to "ok"))
    }
}



