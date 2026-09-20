package com.attendanceio.api.controller.app

import com.attendanceio.api.model.app.WebUpdateRequest
import com.attendanceio.api.model.app.WebUpdateResponse
import com.attendanceio.api.service.app.WebBundleService
import org.slf4j.LoggerFactory
import org.springframework.core.io.FileSystemResource
import org.springframework.http.CacheControl
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.concurrent.TimeUnit

/** Endpoints `@capgo/capacitor-updater` talks to; both are public, the plugin has no token. */
@RestController
@RequestMapping("/api/app")
class WebBundleController(private val bundles: WebBundleService) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @PostMapping("/update")
    fun checkUpdate(@RequestBody request: WebUpdateRequest): WebUpdateResponse {
        val response = bundles.check(request)
        logger.info(
            "app=BUNDLE_CHECK native={}({}) running={} -> {}",
            request.versionBuild, request.versionCode, request.versionName, response.url?.let { "download ${response.version}" } ?: response.error
        )
        return response
    }

    @GetMapping("/bundles/{name}")
    fun bundle(@PathVariable name: String): ResponseEntity<FileSystemResource> {
        val file = bundles.bundleFile(name) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .cacheControl(CacheControl.maxAge(1, TimeUnit.DAYS).cachePublic()) // file names are unique per version
            .body(FileSystemResource(file))
    }
}
