package com.attendanceio.api.controller.app

import com.attendanceio.api.model.app.AppUpdateResponse
import com.attendanceio.api.model.app.AppVersionRequest
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/app")
class AppController(
    @Value("\${app.update.latest-build:121}") private val latestBuild: Int,
    @Value("\${app.update.critical-build:100}") private val criticalBuild: Int,
    @Value("\${app.update.title:Update Available}") private val defaultTitle: String,
    @Value("\${app.update.message:A new version of the app is available. Please update to continue.}") private val defaultMessage: String,
    @Value("\${app.update.critical-title:Update Required}") private val criticalTitle: String,
    @Value("\${app.update.critical-message:This version is no longer supported. Please update the app to continue.}") private val criticalMessage: String
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
}
