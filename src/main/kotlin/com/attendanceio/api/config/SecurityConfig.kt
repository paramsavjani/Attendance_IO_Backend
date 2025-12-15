package com.attendanceio.api.config

import com.attendanceio.api.service.CustomOAuth2FailureHandler
import com.attendanceio.api.service.CustomOAuth2SuccessHandler
import com.attendanceio.api.service.CustomOAuth2UserService
import jakarta.servlet.SessionCookieConfig
import org.springframework.boot.web.servlet.ServletContextInitializer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.web.SecurityFilterChain

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
            .authorizeHttpRequests { requests ->
                requests
                    .requestMatchers("/", "/login", "/oauth2/**", "/error", "/api/user/check", "/api/semester/current", "/api/search/**", "/api/subjects/current").permitAll()
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
