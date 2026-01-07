package com.attendanceio.api.service

import com.attendanceio.api.repository.student.StudentRepositoryAppAction
import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.stereotype.Service
import java.util.Date
import javax.crypto.SecretKey

@Service
class JwtService(
    @Value("\${app.jwt.secret}") private val jwtSecret: String,
    @Value("\${app.jwt.expiration-months:2}") private val expirationMonths: Long,
    private val studentRepositoryAppAction: StudentRepositoryAppAction
) {
    private val log = LoggerFactory.getLogger(JwtService::class.java)
    
    private val signingKey: SecretKey by lazy {
        Keys.hmacShaKeyFor(jwtSecret.toByteArray())
    }
    
    /**
     * Generate a JWT token for a user email
     */
    fun generateToken(email: String): String {
        val now = Date()
        val expiration = Date(now.time + expirationMonths * 30L * 24L * 60L * 60L * 1000L) // Convert months to milliseconds
        
        return Jwts.builder()
            .subject(email)
            .issuedAt(now)
            .expiration(expiration)
            .signWith(signingKey)
            .compact()
    }
    
    /**
     * Extract email from JWT token
     */
    fun extractEmail(token: String): String? {
        return try {
            val claims = extractAllClaims(token)
            claims.subject
        } catch (e: Exception) {
            log.debug("Failed to extract email from token: {}", e.message)
            null
        }
    }
    
    /**
     * Validate JWT token and return UserDetails if valid
     */
    fun validateTokenAndGetUserDetails(token: String): UserDetails? {
        return try {
            val email = extractEmail(token) ?: return null
            val student = studentRepositoryAppAction.findByEmail(email) ?: return null
            
            User.builder()
                .username(email)
                .password("") // Not used in JWT authentication
                .authorities(listOf(SimpleGrantedAuthority("ROLE_USER")))
                .build()
        } catch (e: Exception) {
            log.debug("Failed to validate token: {}", e.message)
            null
        }
    }
    
    /**
     * Check if token is expired
     */
    fun isTokenExpired(token: String): Boolean {
        return try {
            val claims = extractAllClaims(token)
            claims.expiration.before(Date())
        } catch (e: Exception) {
            true
        }
    }
    
    /**
     * Extract all claims from token
     */
    private fun extractAllClaims(token: String): Claims {
        return Jwts.parser()
            .verifyWith(signingKey)
            .build()
            .parseSignedClaims(token)
            .payload
    }
}
