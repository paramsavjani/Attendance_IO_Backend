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
import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import jakarta.annotation.PreDestroy
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam

/**
 * Profile photos in MinIO, one random object name per upload, in two copies:
 * - `profile-pictures/thumbs/<name>`: a small JPEG for avatars and lists;
 * - `profile-pictures/<name>`: the HD photo, opened (and zoomed) when someone taps it.
 *
 * The HD copy is stored before the upload returns, so the new photo shows at once; the thumbnail
 * follows on a background thread, and until it exists the thumbnail route serves the HD copy.
 */
@Service
class ProfilePictureStorageService(private val properties: StorageProperties) {
    companion object {
        private const val PREFIX = "profile-pictures/"
        private const val THUMBS = "thumbs/"
        /** Short side of a thumbnail: a 112px avatar on a 3x phone screen. */
        private const val THUMB_SIDE = 360
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

    /** Thumbnails, written after the upload has already answered. */
    private val background: ExecutorService = Executors.newFixedThreadPool(2)

    @PreDestroy
    fun shutdown() {
        background.shutdown()
    }

    val enabled: Boolean get() = properties.enabled

    /** Stores the HD photo now and its thumbnail in the background. Returns the URL users load the photo from. */
    fun upload(bytes: ByteArray, type: ImageType): String {
        val minio = client ?: throw IllegalStateException("Photo storage is not configured")
        val name = "${UUID.randomUUID()}.${type.extension}"
        put(minio, PREFIX + name, bytes, type.contentType)
        background.execute {
            try {
                thumbnail(bytes)?.let { thumb -> put(minio, PREFIX + THUMBS + name, thumb, "image/jpeg") }
            } catch (e: Exception) {
                log.warn("Thumbnail of {} failed: {}", name, e.message)
            }
        }
        return "${properties.publicUrl.trimEnd('/')}/$name"
    }

    /** Removes a photo this service uploaded; other URLs (Google's) are ignored. */
    fun deleteByUrl(url: String?) {
        val minio = client ?: return
        val name = url?.takeIf { it.startsWith(properties.publicUrl.trimEnd('/') + "/") }?.substringAfterLast('/') ?: return
        if (!NAME.matches(name)) return
        try {
            minio.removeObject(RemoveObjectArgs.builder().bucket(properties.bucket).`object`(PREFIX + name).build())
            minio.removeObject(RemoveObjectArgs.builder().bucket(properties.bucket).`object`(PREFIX + THUMBS + name).build())
        } catch (e: Exception) {
            log.warn("Could not delete old profile picture {}: {}", name, e.message)
        }
    }

    /** The stored photo, or null when the name is not one of ours or no longer exists. */
    fun open(name: String): GetObjectResponse? = if (NAME.matches(name)) get(PREFIX + name) else null

    /** The photo's thumbnail; the full photo when it has none (a WebP upload, or one from before thumbnails). */
    fun openThumbnail(name: String): GetObjectResponse? =
        if (NAME.matches(name)) get(PREFIX + THUMBS + name) ?: get(PREFIX + name) else null

    private fun get(key: String): GetObjectResponse? {
        val minio = client ?: return null
        return try {
            minio.getObject(GetObjectArgs.builder().bucket(properties.bucket).`object`(key).build())
        } catch (e: ErrorResponseException) {
            if (e.errorResponse().code() == "NoSuchKey") null else throw e
        }
    }

    private fun put(minio: MinioClient, key: String, bytes: ByteArray, contentType: String) {
        minio.putObject(
            PutObjectArgs.builder()
                .bucket(properties.bucket)
                .`object`(key)
                .stream(ByteArrayInputStream(bytes), bytes.size.toLong(), -1)
                .contentType(contentType)
                .build()
        )
    }

    /** A JPEG whose short side is [THUMB_SIDE]; null when Java can't decode the format (WebP). */
    private fun thumbnail(bytes: ByteArray): ByteArray? = try {
        val source = ImageIO.read(ByteArrayInputStream(bytes))
        if (source == null) {
            null
        } else {
            val scale = minOf(1.0, THUMB_SIDE.toDouble() / minOf(source.width, source.height))
            val w = (source.width * scale).toInt().coerceAtLeast(1)
            val h = (source.height * scale).toInt().coerceAtLeast(1)
            val scaled = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
            scaled.createGraphics().apply {
                setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
                setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
                drawImage(source, 0, 0, w, h, Color.WHITE, null) // white under any PNG transparency
                dispose()
            }
            val out = ByteArrayOutputStream()
            val writer = ImageIO.getImageWritersByFormatName("jpg").next()
            ImageIO.createImageOutputStream(out).use { stream ->
                writer.output = stream
                val params = writer.defaultWriteParam.apply {
                    compressionMode = ImageWriteParam.MODE_EXPLICIT
                    compressionQuality = 0.82f
                }
                writer.write(null, IIOImage(scaled, null, null), params)
                writer.dispose()
            }
            out.toByteArray()
        }
    } catch (e: Exception) {
        log.warn("Could not make a thumbnail: {}", e.message)
        null
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
