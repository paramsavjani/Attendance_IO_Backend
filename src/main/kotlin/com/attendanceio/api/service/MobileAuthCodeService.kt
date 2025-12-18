package com.attendanceio.api.service

import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class MobileAuthCode(
    val code: String,
    val attributes: Map<String, Any?>,
    val expiresAt: Instant
)

@Service
class MobileAuthCodeService {
    private val ttl: Duration = Duration.ofMinutes(5)
    private val codes = ConcurrentHashMap<String, MobileAuthCode>()

    fun createFromOAuth2User(oauth2User: OAuth2User): String {
        val code = UUID.randomUUID().toString()
        val expiresAt = Instant.now().plus(ttl)
        codes[code] = MobileAuthCode(
            code = code,
            attributes = oauth2User.attributes,
            expiresAt = expiresAt
        )
        return code
    }

    /**
     * One-time consume. Returns null if missing or expired.
     */
    fun consume(code: String): MobileAuthCode? {
        val entry = codes.remove(code) ?: return null
        if (Instant.now().isAfter(entry.expiresAt)) return null
        return entry
    }
}



