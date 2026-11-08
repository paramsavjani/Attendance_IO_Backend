package com.attendanceio.api.controller.app

import com.attendanceio.api.model.app.AppUpdateResponse
import com.attendanceio.api.model.app.AppVersionRequest
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.ClassPathResource
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import tools.jackson.databind.ObjectMapper

@RestController
@RequestMapping("/api/app")
class AppController(
    @Value("\${app.update.latest-build:121}") private val latestBuild: Int,
    @Value("\${app.update.critical-build:100}") private val criticalBuild: Int,
    @Value("\${app.update.title:Update Available}") private val defaultTitle: String,
    @Value("\${app.update.message:A new version of the app is available. Please update to continue.}") private val defaultMessage: String,
    @Value("\${app.update.critical-title:Update Required}") private val criticalTitle: String,
    @Value("\${app.update.critical-message:This version is no longer supported. Please update the app to continue.}") private val criticalMessage: String,
    private val objectMapper: ObjectMapper
) {
    
    @PostMapping("/check-update")
    fun checkUpdate(@RequestBody request: AppVersionRequest): ResponseEntity<AppUpdateResponse> {
        val currentBuild = request.buildNumber
        
        // If current build is less than critical build, it's a critical update
        if (currentBuild < criticalBuild) {
            return ResponseEntity.ok(
                AppUpdateResponse(
                    isUpdateRequired = true,
                    isCritical = true,
                    title = criticalTitle,
                    message = criticalMessage
                )
            )
        }
        
        // If current build is less than latest build, it's a non-critical update
        if (currentBuild < latestBuild) {
            return ResponseEntity.ok(
                AppUpdateResponse(
                    isUpdateRequired = true,
                    isCritical = false,
                    title = defaultTitle,
                    message = defaultMessage
                )
            )
        }
        
        // App is up to date
        return ResponseEntity.ok(
            AppUpdateResponse(
                isUpdateRequired = false,
                isCritical = false,
                title = "",
                message = ""
            )
        )
    }

    /**
     * Enabled popups. A popup may list `requires` (feature names); it is only sent to clients
     * whose `supports` query lists all of them, so an app still on an older web bundle never
     * announces a feature it doesn't have yet.
     */
    @GetMapping("/popups")
    fun getActivePopups(@RequestParam(required = false) supports: String?): ResponseEntity<Map<String, Any>> {
        val clientFeatures = supports.orEmpty().split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        return try {
            val resource = ClassPathResource("data/popups.json")
            if (!resource.exists()) {
                return ResponseEntity.ok(mapOf("popups" to emptyList<Any>()))
            }
            val data = resource.inputStream.use {
                objectMapper.readValue(it, Map::class.java) as Map<String, Any>
            }
            val popups = (data["popups"] as? List<*>)?.filterNotNull()?.filter { popup ->
                val map = popup as? Map<*, *> ?: return@filter false
                val requires = (map["requires"] as? List<*>)?.mapNotNull { it as? String }.orEmpty()
                map["enabled"] == true && clientFeatures.containsAll(requires)
            } ?: emptyList()
            ResponseEntity.ok(mapOf("popups" to popups))
        } catch (e: Exception) {
            ResponseEntity.ok(mapOf("popups" to emptyList<Any>()))
        }
    }
}
