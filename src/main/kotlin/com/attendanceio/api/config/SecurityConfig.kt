package com.attendanceio.api.config

import com.attendanceio.api.service.CustomOAuth2FailureHandler
import com.attendanceio.api.service.CustomOAuth2SuccessHandler
import com.attendanceio.api.service.CustomOAuth2UserService
import org.springframework.http.HttpStatus
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.web.servlet.ServletContextInitializer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.authentication.HttpStatusEntryPoint
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.security.web.util.matcher.RequestMatcher

@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val customOAuth2UserService: CustomOAuth2UserService,
    private val customOAuth2SuccessHandler: CustomOAuth2SuccessHandler,
    private val customOAuth2FailureHandler: CustomOAuth2FailureHandler,
    private val jwtAuthenticationFilter: JwtAuthenticationFilter,
    /**
     * Escape hatch for the /api/search endpoints, which used to be open to the internet: they returned any
     * student's name, roll number, institute email and per-subject attendance to anyone who asked.
     * It is closed now, and app builds that predate the authenticated search call will get 401 on
     * the friend-search screen until they update — flip this to true to hand that back while a new
     * build reaches the store, and turn it off again afterwards.
     */
    @Value("\${app.security.public-student-search:false}") private val publicStudentSearch: Boolean
) {
    /**
     * Paths anyone may call without signing in: the login dance, the health probes, and the handful of
     * endpoints the app needs before it has a session (the update check and its bundles, mobile login,
     * and the institute-wide catalog that carries nobody's personal data).
     *
     * Student search is NOT here: it names students and returns their attendance.
     */
    private fun publicPaths(): List<String> = buildList {
        addAll(
            listOf(
                "/",
                "/login",
                "/actuator/health",
                "/actuator/info",
                "/oauth2/**",
                "/error",
                "/api/semester/current",
                "/api/subjects/current",
                "/api/subjects/analysis/**",
                "/api/time-slots",
                "/api/auth/mobile/**",
                "/api/config/classes-start-date",
                "/api/app/check-update",
                "/api/app/popups",
                "/api/app/update",
                "/api/app/bundles/*",
                // The public AI demo: anonymous by design, restricted to published institute
                // information by PublicAgentToolPolicy and capped by PublicAgentRateLimiter.
                "/api/public/agent/**"
            )
        )
        if (publicStudentSearch) add("/api/search/**")
    }

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .cors { }
            .authorizeHttpRequests { requests ->
                requests
                    .requestMatchers(*publicPaths().toTypedArray()).permitAll()
                    .anyRequest().authenticated()
            }
            .oauth2Login { oauth2 ->
                oauth2
                    .userInfoEndpoint { userInfo ->
                        userInfo.userService(customOAuth2UserService)
                    }
                    .successHandler(customOAuth2SuccessHandler)
                    .failureHandler(customOAuth2FailureHandler)
            }
            // IMPORTANT: Never redirect API (fetch/XHR) calls to Google OAuth.
            // APIs must return 401 so the frontend can decide to navigate for login.
            .exceptionHandling { exceptions ->
                exceptions.defaultAuthenticationEntryPointFor(
                    HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                    RequestMatcher { req -> (req.requestURI ?: "").startsWith("/api/") }
                )
            }
            .sessionManagement { session ->
                // Use IF_REQUIRED to support both JWT (stateless) and session-based (backward compatibility) auth
                session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
            }
            .csrf { csrf -> csrf.disable() } // Disable CSRF for API (enable if needed for web forms)
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)

        return http.build()
    }

    @Bean
    fun servletContextInitializer(): ServletContextInitializer {
        return ServletContextInitializer { servletContext ->
            val sessionCookieConfig = servletContext.sessionCookieConfig
            sessionCookieConfig.maxAge = 2592000 // 30 days in seconds (for backward compatibility)
            sessionCookieConfig.isHttpOnly = true
            sessionCookieConfig.name = "JSESSIONID"
        }
    }
}
