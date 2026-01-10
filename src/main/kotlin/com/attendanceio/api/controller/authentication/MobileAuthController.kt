package com.attendanceio.api.controller.authentication

import com.attendanceio.api.service.JwtService
import com.attendanceio.api.service.MobileAuthCodeService
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
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
    private val mobileAuthCodeService: MobileAuthCodeService,
    private val jwtService: JwtService
) {
    private val log = LoggerFactory.getLogger(MobileAuthController::class.java)

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
        @RequestParam("buildNumber", required = false) buildNumber: Int?,
        request: HttpServletRequest,
        response: HttpServletResponse
    ) {
        log.info(
            "Mobile OAuth start: redirectUri={}, buildNumber={}, remote={}",
            redirectUri,
            buildNumber,
            request.remoteAddr
        )
        
        // Validate build number is provided
        if (buildNumber == null) {
            log.warn("Mobile OAuth start: buildNumber is missing. App needs to be updated.")
            response.sendError(400, "App needs to be updated. Please update to the latest version.")
            return
        }
        
        val uri = runCatching { URI.create(redirectUri) }.getOrNull()
            ?: run {
                log.warn("Mobile OAuth start: invalid redirect_uri={}", redirectUri)
                response.sendError(400, "Invalid redirect_uri")
                return
            }

        if (uri.scheme != ALLOWED_REDIRECT_SCHEME || uri.host != ALLOWED_REDIRECT_HOST) {
            log.warn("Mobile OAuth start: redirect_uri not allowed. redirectUri={}", redirectUri)
            response.sendError(400, "redirect_uri not allowed")
            return
        }

        // Store redirect URI in session for OAuth success handler
        request.getSession(true).setAttribute(SESSION_REDIRECT_URI_KEY, redirectUri)
        response.sendRedirect("/oauth2/authorization/google")
    }

    data class ExchangeRequest(val code: String, val token: String?, val buildNumber: Int?)

    /**
     * Exchanges the one-time code (received via deep link) into a JWT token.
     * Also creates a session for backward compatibility with old clients.
     * If token is already provided (from OAuth redirect), validates and returns it.
     * Otherwise, generates a new token from the code.
     */
    @PostMapping("/exchange")
    fun exchange(
        @RequestBody body: ExchangeRequest,
        request: HttpServletRequest
    ): ResponseEntity<Map<String, Any>> {
        // Validate build number is provided
        if (body.buildNumber == null) {
            log.warn("Mobile OAuth exchange: buildNumber is missing. App needs to be updated.")
            return ResponseEntity.status(400).body(mapOf("error" to "App needs to be updated. Please update to the latest version."))
        }
        
        val codePreview = body.code.take(8)
        log.info(
            "Mobile OAuth exchange: codePrefix={}, hasToken={}, buildNumber={}, remote={}",
            codePreview,
            !body.token.isNullOrBlank(),
            body.buildNumber,
            request.remoteAddr
        )
        
        // If token is provided, validate it and return (also create session for backward compatibility)
        if (!body.token.isNullOrBlank()) {
            val email = jwtService.extractEmail(body.token)
            if (email != null && !jwtService.isTokenExpired(body.token)) {
                log.info("Mobile OAuth exchange: using provided token. email={}", email)
                // Also create session for backward compatibility
                createSessionFromToken(email, request)
                return ResponseEntity.ok(mapOf("status" to "ok", "token" to body.token))
            } else {
                log.warn("Mobile OAuth exchange: provided token is invalid or expired")
            }
        }
        
        // Otherwise, exchange code for token
        val consumed = mobileAuthCodeService.consume(body.code)
            ?: run {
                log.warn("Mobile OAuth exchange: invalid/expired codePrefix={}", codePreview)
                return ResponseEntity.status(401).body(mapOf("error" to "Invalid or expired code"))
            }

        val email = consumed.attributes["email"] as? String
        if (email == null) {
            log.warn("Mobile OAuth exchange: email not found in consumed attributes")
            return ResponseEntity.status(401).body(mapOf("error" to "Email not found"))
        }

        // Generate JWT token for new clients
        val token = jwtService.generateToken(email)
        
        // Create session for backward compatibility with old clients
        createSessionFromOAuth2User(consumed, request)
        
        log.info("Mobile OAuth exchange: success. email={}, sessionCreated={}", email, request.getSession(false)?.id)
        return ResponseEntity.ok(mapOf("status" to "ok", "token" to token))
    }
    
    /**
     * Create a session from OAuth2 user attributes (for backward compatibility)
     */
    private fun createSessionFromOAuth2User(consumed: com.attendanceio.api.service.MobileAuthCode, request: HttpServletRequest) {
        val authorities = listOf(SimpleGrantedAuthority("ROLE_USER"))
        val nameAttributeKey = if (consumed.attributes.containsKey("sub")) "sub" else "email"
        val principal: OAuth2User = DefaultOAuth2User(authorities, consumed.attributes, nameAttributeKey)
        val auth = OAuth2AuthenticationToken(principal, authorities, "google")
        
        // Rotate session id (session fixation defense) and persist security context to session
        request.changeSessionId()
        SecurityContextHolder.getContext().authentication = auth
        request.getSession(true).setAttribute(
            HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
            SecurityContextHolder.getContext()
        )
    }
    
    /**
     * Create a session from JWT token email (for backward compatibility)
     */
    private fun createSessionFromToken(email: String, request: HttpServletRequest) {
        val authorities = listOf(SimpleGrantedAuthority("ROLE_USER"))
        val attributes = mapOf(
            "email" to email,
            "sub" to email,
            "name" to "",
            "picture" to ""
        )
        val principal: OAuth2User = DefaultOAuth2User(authorities, attributes, "email")
        val auth = OAuth2AuthenticationToken(principal, authorities, "google")
        
        request.changeSessionId()
        SecurityContextHolder.getContext().authentication = auth
        request.getSession(true).setAttribute(
            HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
            SecurityContextHolder.getContext()
        )
    }
}



