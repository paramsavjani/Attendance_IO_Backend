package com.attendanceio.api.service.app

import com.attendanceio.api.model.app.WebBundleManifest
import com.attendanceio.api.model.app.WebUpdateRequest
import com.attendanceio.api.model.app.WebUpdateResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import tools.jackson.databind.ObjectMapper
import java.nio.file.Files
import java.nio.file.Path

/**
 * Over-the-air web bundle updates for the Android app. The APK carries a copy of the web build;
 * `@capgo/capacitor-updater` asks [check] on every foreground whether a newer one exists, then
 * downloads the zip from [bundleFile] and swaps it in on the next background. Native code never
 * changes this way, so a bundle that needs newer native code is withheld via
 * [WebBundleManifest.minNativeVersionCode] until the phone has updated from the store.
 *
 * The bundle directory is a host folder mounted into the container; the frontend's publish
 * workflow drops `web-<version>.zip` there and rewrites `latest.json` atomically.
 */
@Service
class WebBundleService(
    @Value("\${app.bundles.dir:./bundles}") dir: String,
    @Value("\${app.bundles.public-url:http://localhost:8081/api/app/bundles}") publicUrl: String,
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val dir: Path = Path.of(dir).toAbsolutePath().normalize()
    private val publicUrl = publicUrl.trimEnd('/')

    companion object {
        const val NO_BUNDLE = "no_bundle_published"
        const val UP_TO_DATE = "no_new_version_available"
        const val NATIVE_TOO_OLD = "store_update_required"
        private val SAFE_FILE = Regex("^[A-Za-z0-9._-]+\\.zip$")
    }

    fun manifest(): WebBundleManifest? {
        val file = dir.resolve("latest.json")
        if (!Files.isRegularFile(file)) return null
        return runCatching { Files.newInputStream(file).use { objectMapper.readValue(it, WebBundleManifest::class.java) } }
            .onFailure { logger.warn("app=BUNDLE unreadable manifest {}: {}", file, it.toString()) }
            .getOrNull()
    }

    fun check(request: WebUpdateRequest): WebUpdateResponse {
        val latest = manifest() ?: return WebUpdateResponse(version = "builtin", error = NO_BUNDLE, message = "No web bundle published")
        val nativeCode = request.versionCode?.trim()?.toIntOrNull() ?: 0
        return when {
            nativeCode < latest.minNativeVersionCode ->
                WebUpdateResponse(version = latest.version, error = NATIVE_TOO_OLD, message = "Update the app from the Play Store", breaking = true)
            request.versionName == latest.version ->
                WebUpdateResponse(version = latest.version, error = UP_TO_DATE, message = "No new version available")
            else -> WebUpdateResponse(version = latest.version, url = "$publicUrl/${latest.file}", checksum = latest.sha256)
        }
    }

    /** The zip for [name] if it exists inside the bundle directory (no path tricks accepted). */
    fun bundleFile(name: String): Path? {
        if (!SAFE_FILE.matches(name)) return null
        val file = dir.resolve(name).normalize()
        return file.takeIf { it.parent == dir && Files.isRegularFile(it) }
    }
}
