package com.attendanceio.api.service

import com.attendanceio.api.config.StorageProperties
import io.minio.BucketExistsArgs
import io.minio.GetObjectArgs
import io.minio.GetObjectResponse
import io.minio.MakeBucketArgs
import io.minio.MinioClient
import io.minio.PutObjectArgs
import io.minio.RemoveObjectArgs
import io.minio.errors.ErrorResponseException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.ByteArrayInputStream
import java.util.UUID

/** Profile photos in MinIO, under `profile-pictures/`, one random object name per upload. */
@Service
class ProfilePictureStorageService(private val properties: StorageProperties) {
    companion object {
        private const val PREFIX = "profile-pictures/"
        /** Object names this service hands out: `<uuid>.<ext>`. Anything else is not ours to serve. */
        private val NAME = Regex("^[0-9a-f-]{36}\\.(jpg|png|webp)$")
    }

    private val log = LoggerFactory.getLogger(javaClass)

    private val client: MinioClient? by lazy {
        if (!properties.enabled) return@lazy null
        MinioClient.builder()
            .endpoint(properties.endpoint)
            .credentials(properties.accessKey, properties.secretKey)
            .build()
            .also { ensureBucket(it) }
    }

    val enabled: Boolean get() = properties.enabled

    /** Stores the photo and returns the URL users load it from. */
    fun upload(bytes: ByteArray, type: ImageType): String {
        val minio = client ?: throw IllegalStateException("Photo storage is not configured")
        val name = "${UUID.randomUUID()}.${type.extension}"
        minio.putObject(
            PutObjectArgs.builder()
                .bucket(properties.bucket)
                .`object`(PREFIX + name)
                .stream(ByteArrayInputStream(bytes), bytes.size.toLong(), -1)
                .contentType(type.contentType)
                .build()
        )
        return "${properties.publicUrl.trimEnd('/')}/$name"
    }

    /** Removes a photo this service uploaded; other URLs (Google's) are ignored. */
    fun deleteByUrl(url: String?) {
        val minio = client ?: return
        val name = url?.takeIf { it.startsWith(properties.publicUrl.trimEnd('/') + "/") }?.substringAfterLast('/') ?: return
        if (!NAME.matches(name)) return
        try {
            minio.removeObject(RemoveObjectArgs.builder().bucket(properties.bucket).`object`(PREFIX + name).build())
        } catch (e: Exception) {
            log.warn("Could not delete old profile picture {}: {}", name, e.message)
        }
    }

    /** The stored photo, or null when the name is not one of ours or no longer exists. */
    fun open(name: String): GetObjectResponse? {
        val minio = client ?: return null
        if (!NAME.matches(name)) return null
        return try {
            minio.getObject(GetObjectArgs.builder().bucket(properties.bucket).`object`(PREFIX + name).build())
        } catch (e: ErrorResponseException) {
            if (e.errorResponse().code() == "NoSuchKey") null else throw e
        }
    }

    private fun ensureBucket(minio: MinioClient) {
        val exists = minio.bucketExists(BucketExistsArgs.builder().bucket(properties.bucket).build())
        if (!exists) {
            minio.makeBucket(MakeBucketArgs.builder().bucket(properties.bucket).build())
            log.info("Created MinIO bucket {}", properties.bucket)
        }
    }

    enum class ImageType(val extension: String, val contentType: String) {
        JPEG("jpg", "image/jpeg"),
        PNG("png", "image/png"),
        WEBP("webp", "image/webp");

        companion object {
            /** Detected from the file's first bytes, so a renamed non-image is rejected. */
            fun detect(bytes: ByteArray): ImageType? = when {
                bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte() -> JPEG
                bytes.size >= 8 && bytes.copyOfRange(0, 8).contentEquals(
                    byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
                ) -> PNG
                bytes.size >= 12 && String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF" &&
                    String(bytes, 8, 4, Charsets.US_ASCII) == "WEBP" -> WEBP
                else -> null
            }
        }
    }
}
