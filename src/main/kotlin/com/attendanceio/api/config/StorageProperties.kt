package com.attendanceio.api.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * The MinIO bucket that holds uploaded profile photos (`app.storage.*`). MinIO stays on the
 * VPS's private Docker network: photos reach users through `/api/profile-pictures/{name}`, so
 * the bucket never needs a public port, domain or certificate.
 */
@ConfigurationProperties(prefix = "app.storage")
data class StorageProperties(
    /** MinIO's S3 endpoint as the backend container sees it, e.g. `http://minio:9000`. Blank disables uploads. */
    val endpoint: String = "",
    val accessKey: String = "",
    val secretKey: String = "",
    val bucket: String = "attendance-io",
    /** Where browsers fetch a photo from; the object name is appended. */
    val publicUrl: String = "http://localhost:8081/api/profile-pictures",
    /** Largest HD photo accepted, after the app has already cropped and resized it (at most 1600px). */
    val maxPictureBytes: Long = 4 * 1024 * 1024
) {
    val enabled: Boolean get() = endpoint.isNotBlank()
}
