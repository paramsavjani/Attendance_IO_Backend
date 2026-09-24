package com.attendanceio.api.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
class CorsConfig(
    @Value("\${app.frontend.url:https://attendanceio.paramsavjani.in}") private val frontendUrl: String,
    /** Origins of the public AI demo, which lives on its own domain and calls /api/public/agent. */
    @Value("\${app.agent.public.origins:https://ai.paramsavjani.in}") private val publicDemoOrigins: List<String>
) {
    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val configuration = CorsConfiguration()
        
        // Extract origin from frontend URL
        val allowedOrigin = frontendUrl.trimEnd('/')
        configuration.allowedOrigins = listOf(
            allowedOrigin,
            // Capacitor/Ionic origins (WebView)
            "capacitor://localhost",
            "ionic://localhost",
            "http://localhost",
            "https://localhost"
        ) + publicDemoOrigins.map { it.trim().trimEnd('/') }.filter { it.isNotBlank() }
        
        // Allow credentials (cookies, authorization headers)
        configuration.allowCredentials = true
        
        // Allow all methods
        configuration.allowedMethods = listOf("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH")
        
        // Allow all headers
        configuration.allowedHeaders = listOf("*")
        
        // Expose headers that frontend might need
        configuration.exposedHeaders = listOf("Authorization", "Content-Type")
        
        // Cache preflight requests for 1 hour
        configuration.maxAge = 3600L
        
        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", configuration)
        return source
    }
}

