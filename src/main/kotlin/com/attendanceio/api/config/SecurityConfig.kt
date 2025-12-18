package com.attendanceio.api.config

import com.attendanceio.api.service.CustomOAuth2FailureHandler
import com.attendanceio.api.service.CustomOAuth2SuccessHandler
import com.attendanceio.api.service.CustomOAuth2UserService
import org.springframework.http.HttpStatus
import org.springframework.boot.web.servlet.ServletContextInitializer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.web.authentication.HttpStatusEntryPoint
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.util.matcher.RequestMatcher

@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val customOAuth2UserService: CustomOAuth2UserService,
    private val customOAuth2SuccessHandler: CustomOAuth2SuccessHandler,
    private val customOAuth2FailureHandler: CustomOAuth2FailureHandler
) {
    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .cors { }
            .authorizeHttpRequests { requests ->
                requests
                    .requestMatchers(
                        "/",
                        "/login",
                        "/actuator/health",
                        "/actuator/info",
                        "/oauth2/**",
                        "/error",
                        "/api/semester/current",
                        "/api/search/**",
                        "/api/subjects/current",
                        "/api/auth/mobile/**"
                    ).permitAll()
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
                session
                    .sessionCreationPolicy(org.springframework.security.config.http.SessionCreationPolicy.IF_REQUIRED)
            }
            .csrf { csrf -> csrf.disable() } // Disable CSRF for API (enable if needed for web forms)

        return http.build()
    }

    @Bean
    fun servletContextInitializer(): ServletContextInitializer {
        return ServletContextInitializer { servletContext ->
            val sessionCookieConfig = servletContext.sessionCookieConfig
            sessionCookieConfig.maxAge = 2592000 // 30 days in seconds
            sessionCookieConfig.isHttpOnly = true
            sessionCookieConfig.name = "JSESSIONID"
        }
    }
}
