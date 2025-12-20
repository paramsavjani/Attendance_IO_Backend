package com.attendanceio.api.service

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.Message
import com.google.firebase.messaging.Notification
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import jakarta.annotation.PostConstruct
import java.io.File
import java.io.FileInputStream

/**
 * Service for sending FCM push notifications to Android devices
 */
@Service
class FcmNotificationService {
    private val logger = LoggerFactory.getLogger(FcmNotificationService::class.java)
    
    @Value("\${firebase.service-account.path:}")
    private val serviceAccountPath: String = ""
    
    @PostConstruct
    fun initialize() {
        try {
            if (serviceAccountPath.isNotBlank()) {
                // Try to resolve the path - check if it's absolute, relative to project root, or in resources
                val serviceAccountFile = resolveServiceAccountFile(serviceAccountPath)
                
                if (serviceAccountFile != null && serviceAccountFile.exists()) {
                    val serviceAccount = FileInputStream(serviceAccountFile)
                    val options = FirebaseOptions.Builder()
                        .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                        .build()
                    
                    if (FirebaseApp.getApps().isEmpty()) {
                        FirebaseApp.initializeApp(options)
                        logger.info("Firebase initialized successfully from service account file: ${serviceAccountFile.absolutePath}")
                    }
                } else {
                    logger.warn("Firebase service account file not found at: $serviceAccountPath. Tried: ${serviceAccountFile?.absolutePath ?: "N/A"}")
                    // Try to use default credentials as fallback
                    if (FirebaseApp.getApps().isEmpty()) {
                        FirebaseApp.initializeApp()
                        logger.info("Firebase initialized with default credentials (fallback)")
                    }
                }
            } else {
                // Try to use default credentials (for cloud environments like GCP)
                if (FirebaseApp.getApps().isEmpty()) {
                    FirebaseApp.initializeApp()
                    logger.info("Firebase initialized with default credentials")
                }
            }
        } catch (e: Exception) {
            logger.warn("Firebase initialization failed. Push notifications may not work. Error: ${e.message}", e)
        }
    }
    
    /**
     * Resolve the service account file path.
     * Tries: absolute path, relative to project root, relative to resources folder
     */
    private fun resolveServiceAccountFile(path: String): File? {
        // Try absolute path first
        val absoluteFile = File(path)
        if (absoluteFile.isAbsolute && absoluteFile.exists()) {
            return absoluteFile
        }
        
        // Try relative to project root (where build.gradle.kts is)
        val projectRoot = File(".").absoluteFile
        val projectRootFile = File(projectRoot, path)
        if (projectRootFile.exists()) {
            return projectRootFile
        }
        
        // Try in parent directory (if running from build/classes)
        val parentFile = File(projectRoot.parent, path)
        if (parentFile.exists()) {
            return parentFile
        }
        
        // Try relative to current working directory
        val cwdFile = File(System.getProperty("user.dir"), path)
        if (cwdFile.exists()) {
            return cwdFile
        }
        
        return null
    }
    
    /**
     * Send a push notification to a specific FCM token
     * @param fcmToken The FCM token of the device
     * @param title Notification title
     * @param body Notification body/message
     * @param data Optional data payload
     * @return true if sent successfully, false otherwise
     */
    fun sendNotification(
        fcmToken: String,
        title: String,
        body: String,
        data: Map<String, String> = emptyMap()
    ): Boolean {
        return try {
            if (FirebaseApp.getApps().isEmpty()) {
                logger.error("Firebase not initialized. Cannot send notification.")
                return false
            }
            
            val notification = Notification.builder()
                .setTitle(title)
                .setBody(body)
                .build()
            
            val messageBuilder = Message.builder()
                .setToken(fcmToken)
                .setNotification(notification)
            
            // Add data payload if provided
            if (data.isNotEmpty()) {
                messageBuilder.putAllData(data)
            }
            
            val message = messageBuilder.build()
            val response = FirebaseMessaging.getInstance().send(message)
            
            logger.info("Successfully sent notification to token ${fcmToken.take(20)}... Message ID: $response")
            true
        } catch (e: Exception) {
            logger.error("Failed to send notification to token ${fcmToken.take(20)}... Error: ${e.message}", e)
            false
        }
    }
    
    /**
     * Send notification to multiple FCM tokens
     * @return Number of successfully sent notifications
     */
    fun sendNotificationToMultiple(
        fcmTokens: List<String>,
        title: String,
        body: String,
        data: Map<String, String> = emptyMap()
    ): Int {
        var successCount = 0
        fcmTokens.forEach { token ->
            if (sendNotification(token, title, body, data)) {
                successCount++
            }
        }
        return successCount
    }
}

