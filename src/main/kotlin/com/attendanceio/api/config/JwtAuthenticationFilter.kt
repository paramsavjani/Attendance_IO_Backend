package com.attendanceio.api.config

import com.attendanceio.api.repository.student.StudentRepositoryAppAction
import com.attendanceio.api.service.JwtService
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken
import org.springframework.security.oauth2.core.user.DefaultOAuth2User
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class JwtAuthenticationFilter(
    private val jwtService: JwtService,
    private val studentRepositoryAppAction: StudentRepositoryAppAction
) : OncePerRequestFilter() {
    
    private val log = LoggerFactory.getLogger(JwtAuthenticationFilter::class.java)
    
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        // Check if there's already an authentication (e.g., from session or OAuth2)
        val existingAuth = SecurityContextHolder.getContext().authentication
        
        // If already authenticated via session, skip JWT processing (backward compatibility)
        if (existingAuth != null && existingAuth.isAuthenticated) {
            filterChain.doFilter(request, response)
            return
        }
        
        // Only process JWT if Authorization header is present
        val authHeader = request.getHeader("Authorization")
        
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            // No JWT token - let session-based auth handle it (backward compatibility)
            filterChain.doFilter(request, response)
            return
        }
        
        val token = authHeader.substring(7)
        
        try {
            val email = jwtService.extractEmail(token)
            
            if (email != null && !jwtService.isTokenExpired(token)) {
                val student = studentRepositoryAppAction.findByEmail(email)
                
                if (student != null) {
                    // Create OAuth2User-like principal for compatibility with existing code
                    val attributes = mapOf(
                        "email" to email,
                        "sub" to (student.googleId ?: email),
                        "name" to (student.name ?: ""),
                        "picture" to (student.pictureUrl ?: "")
                    )
                    
                    val authorities = listOf(SimpleGrantedAuthority("ROLE_USER"))
                    val principal: OAuth2User = DefaultOAuth2User(authorities, attributes, "email")
                    val auth = OAuth2AuthenticationToken(principal, authorities, "google")
                    
                    SecurityContextHolder.getContext().authentication = auth
                    log.debug("JWT authentication successful for email: {}", email)
                }
            }
        } catch (e: Exception) {
            log.debug("JWT authentication failed: {}", e.message)
            // Don't fail the request - let session-based auth try if available
        }
        
        filterChain.doFilter(request, response)
    }
}
