package com.attendanceio.api.application.agent.`public`

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.core.OAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtValidators
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.stereotype.Component

/**
 * Checks the Google ID token the demo page sends after someone signs in.
 *
 * Google Identity Services hands the browser a signed JWT rather than running a redirect dance, so
 * the page can stay on its own domain and nothing has to be shared between it and the app's OAuth
 * login. Verification is the whole point: without it, a made-up token would buy a fresh daily
 * allowance, which is exactly what signing in is supposed to prevent.
 *
 * Signatures are checked against Google's published keys (fetched and cached by the decoder),
 * along with expiry, issuer, and — the one people forget — the audience: a token minted for some
 * other application is a valid Google token and still not one for us.
 */
@Component
class PublicAgentGoogleVerifier(
    @Value("\${app.agent.public.google-client-id:}") private val clientId: String
) {
    private val logger = LoggerFactory.getLogger(PublicAgentGoogleVerifier::class.java)

    /** Sign-in is simply off when no client id is configured; the page then stays anonymous-only. */
    fun isEnabled(): Boolean = clientId.isNotBlank()

    fun clientId(): String = clientId

    private val decoder: JwtDecoder? by lazy {
        if (!isEnabled()) return@lazy null
        NimbusJwtDecoder.withJwkSetUri(GOOGLE_KEYS).build().apply {
            setJwtValidator(
                DelegatingOAuth2TokenValidator(
                    JwtValidators.createDefault(),
                    IssuedByGoogle,
                    AudienceIs(clientId)
                )
            )
        }
    }

    /** The account behind [idToken], or null if the token is missing, expired, forged or not ours. */
    fun verify(idToken: String?): GoogleAccount? {
        val token = idToken?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val jwt = runCatching { decoder?.decode(token) }
            .onFailure { logger.debug("public=GOOGLE_TOKEN_REJECTED reason={}", it.message) }
            .getOrNull() ?: return null
        val subject = jwt.subject?.takeIf { it.isNotBlank() } ?: return null
        return GoogleAccount(
            subject = subject,
            name = jwt.getClaimAsString("name"),
            emailVerified = jwt.getClaimAsBoolean("email_verified") ?: false
        )
    }

    /** Who signed in, as far as this page needs to know. The email is deliberately not kept. */
    data class GoogleAccount(val subject: String, val name: String?, val emailVerified: Boolean)

    private object IssuedByGoogle : OAuth2TokenValidator<Jwt> {
        // Google issues both spellings and has done for years; accepting one of them breaks at random.
        private val ACCEPTED = setOf("https://accounts.google.com", "accounts.google.com")

        override fun validate(token: Jwt): OAuth2TokenValidatorResult =
            if (token.issuer?.toString() in ACCEPTED) OAuth2TokenValidatorResult.success()
            else OAuth2TokenValidatorResult.failure(OAuth2Error("invalid_issuer", "not issued by Google", null))
    }

    private class AudienceIs(private val clientId: String) : OAuth2TokenValidator<Jwt> {
        override fun validate(token: Jwt): OAuth2TokenValidatorResult =
            if (token.audience?.contains(clientId) == true) OAuth2TokenValidatorResult.success()
            else OAuth2TokenValidatorResult.failure(OAuth2Error("invalid_audience", "token was minted for another app", null))
    }

    private companion object {
        const val GOOGLE_KEYS = "https://www.googleapis.com/oauth2/v3/certs"
    }
}
