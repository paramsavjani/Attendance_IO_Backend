package com.attendanceio.api.application.student.actions

import com.attendanceio.api.config.StorageProperties
import com.attendanceio.api.model.student.DMStudent
import com.attendanceio.api.repository.student.StudentRepositoryAppAction
import com.attendanceio.api.service.ProfilePictureStorageService
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/** A student's own photo replaces the Google one until they remove it. */
@Component
class UpdateProfilePictureAppAction(
    private val studentRepositoryAppAction: StudentRepositoryAppAction,
    private val storage: ProfilePictureStorageService,
    private val properties: StorageProperties
) {
    /** Returns the new picture URL. Throws [IllegalArgumentException] for a file that is not an accepted image. */
    @Transactional
    fun upload(student: DMStudent, bytes: ByteArray): String {
        require(bytes.isNotEmpty()) { "Choose a photo to upload" }
        require(bytes.size <= properties.maxPictureBytes) { "Photo must be under ${properties.maxPictureBytes / (1024 * 1024)} MB" }
        val type = ProfilePictureStorageService.ImageType.detect(bytes)
            ?: throw IllegalArgumentException("Photo must be a JPEG, PNG or WebP image")

        val previous = student.uploadedPictureUrl
        val url = storage.upload(bytes, type)
        student.uploadedPictureUrl = url
        student.pictureUrl = url
        studentRepositoryAppAction.update(student)
        storage.deleteByUrl(previous)
        return url
    }

    /** Back to the Google photo; returns it (null when there is none on record). */
    @Transactional
    fun remove(student: DMStudent): String? {
        val previous = student.uploadedPictureUrl
        student.uploadedPictureUrl = null
        student.pictureUrl = student.googlePictureUrl
        studentRepositoryAppAction.update(student)
        storage.deleteByUrl(previous)
        return student.pictureUrl
    }
}
