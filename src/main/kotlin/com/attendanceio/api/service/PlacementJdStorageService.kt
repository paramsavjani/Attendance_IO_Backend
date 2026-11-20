package com.attendanceio.api.service

import com.attendanceio.api.config.StorageProperties
import io.minio.GetObjectArgs
import io.minio.MinioClient
import io.minio.errors.ErrorResponseException
import org.springframework.stereotype.Service

/**
 * Job description PDFs as the companies sent them, in the private MinIO bucket under
 * `placements/jd/<eventId>/<file>` (uploaded by college-data/tools/sync_jd_pdfs.sh). Only read here;
 * [com.attendanceio.api.controller.placement.PlacementController] checks the caller first.
 */
@Service
class PlacementJdStorageService(private val properties: StorageProperties) {
    private val client: MinioClient? by lazy {
        if (!properties.enabled) return@lazy null
        MinioClient.builder().endpoint(properties.endpoint).credentials(properties.accessKey, properties.secretKey).build()
    }

    /** The PDF's bytes, or null when storage is off or the file is not there. */
    fun read(eventId: Int, file: String): ByteArray? {
        val minio = client ?: return null
        return try {
            minio.getObject(GetObjectArgs.builder().bucket(properties.bucket).`object`("placements/jd/$eventId/$file").build())
                .use { it.readAllBytes() }
        } catch (e: ErrorResponseException) {
            if (e.errorResponse().code() == "NoSuchKey") null else throw e
        }
    }
}
