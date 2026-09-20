package com.attendanceio.api.model.app

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * `latest.json` in the bundle directory, written by the frontend's publish workflow. One file
 * describes the single bundle every phone should be on; older zips stay next to it only so a
 * phone mid-download is not cut off.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class WebBundleManifest(
    /** Bundle version the plugin compares with the one it is running, e.g. `1.0.312`. Any change means "download". */
    val version: String,
    /** Zip file name inside the bundle directory. */
    val file: String,
    /** Lowercase SHA-256 hex of the zip, verified by the plugin after download. */
    val sha256: String? = null,
    /** Phones whose native `versionCode` is below this need a store update first; the bundle is withheld from them. */
    val minNativeVersionCode: Int = 0,
    val commit: String? = null,
    val publishedAt: String? = null
)

/** What `@capgo/capacitor-updater` POSTs on every update check (snake_case, as the plugin sends it). */
@JsonIgnoreProperties(ignoreUnknown = true)
data class WebUpdateRequest(
    val platform: String? = null,
    @JsonProperty("app_id") val appId: String? = null,
    @JsonProperty("device_id") val deviceId: String? = null,
    @JsonProperty("plugin_version") val pluginVersion: String? = null,
    /** Native app versionName, e.g. `1.0.298`. */
    @JsonProperty("version_build") val versionBuild: String? = null,
    /** Native app versionCode, sent as a string. */
    @JsonProperty("version_code") val versionCode: String? = null,
    /** Bundle version currently running, or `builtin` for the one shipped in the APK. */
    @JsonProperty("version_name") val versionName: String? = null,
    @JsonProperty("version_os") val versionOs: String? = null,
    @JsonProperty("is_prod") val isProd: Boolean? = null,
    @JsonProperty("is_emulator") val isEmulator: Boolean? = null
)

/**
 * Reply the plugin understands: `url` present -> download it; otherwise `error`/`message` say
 * why not, and `breaking = true` tells it a store update is needed instead.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class WebUpdateResponse(
    val version: String,
    val url: String? = null,
    val checksum: String? = null,
    val error: String? = null,
    val message: String? = null,
    val breaking: Boolean? = null
)
