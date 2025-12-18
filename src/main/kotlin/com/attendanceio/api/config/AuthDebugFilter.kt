package com.attendanceio.api.config

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class AuthDebugFilter(
    @Value("\${app.auth-debug:false}") private val enabled: Boolean
) : OncePerRequestFilter() {

    private val log = LoggerFactory.getLogger(AuthDebugFilter::class.java)

    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        if (!enabled) return true
        val path = request.requestURI ?: return true
        return !(path.startsWith("/oauth2/")
            || path.startsWith("/login/oauth2/")
            || path.startsWith("/api/auth/mobile/")
            || path.startsWith("/api/user/me"))
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val cookieHeader = request.getHeader("Cookie")
        val hasJSessionId = cookieHeader?.contains("JSESSIONID=") == true
        val requestedSessionId = request.requestedSessionId
        val isRequestedSessionIdValid = runCatching { request.isRequestedSessionIdValid }.getOrDefault(false)

        log.info(
            "AUTH_DEBUG: {} {} host={} scheme={} xfProto={} xfHost={} hasJSESSIONID={} requestedSessionIdPresent={} requestedSessionIdValid={} remote={}",
            request.method,
            request.requestURI,
            request.getHeader("Host"),
            request.scheme,
            request.getHeader("X-Forwarded-Proto"),
            request.getHeader("X-Forwarded-Host"),
            hasJSessionId,
            !requestedSessionId.isNullOrBlank(),
            isRequestedSessionIdValid,
            request.remoteAddr
        )

        filterChain.doFilter(request, response)
    }
}


