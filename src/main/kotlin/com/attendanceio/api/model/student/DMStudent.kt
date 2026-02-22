package com.attendanceio.api.model.student

import com.attendanceio.api.model.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table

@Entity
@Table(name = "student")
class DMStudent : BaseEntity() {
    @Column(name = "name")
    var name: String? = null
    
    @Column(name = "sid", unique = true, nullable = false)
    var sid: String = ""
    
    @Column(name = "phone")
    var phone: String? = null
    
    @Column(name = "email", unique = true)
    var email: String? = null
    
    @Column(name = "google_id", unique = true)
    var googleId: String? = null
    
    @Column(name = "picture_url")
    var pictureUrl: String? = null
    
    @Column(name = "sleep_duration_hours", nullable = false)
    var sleepDurationHours: Int = 8 // Default 8 hours
    
    @Column(name = "fcm_token")
    var fcmToken: String? = null

    /** Send daily reminder at 6 PM IST. */
    @Column(name = "daily_reminder_at_18", nullable = false)
    var dailyReminderAt18: Boolean = false

    /** Send daily reminder at 8 PM IST. */
    @Column(name = "daily_reminder_at_20", nullable = false)
    var dailyReminderAt20: Boolean = false

    /** Send daily reminder at 10 PM IST. */
    @Column(name = "daily_reminder_at_22", nullable = false)
    var dailyReminderAt22: Boolean = false

    /**
     * If true, send reminder 5 minutes after each lecture ends if attendance not marked. Default true.
     */
    @Column(name = "after_lecture_reminder_enabled", nullable = false)
    var afterLectureReminderEnabled: Boolean = true
}
