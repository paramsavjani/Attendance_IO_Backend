package com.attendanceio.api.repository.feedback

import com.attendanceio.api.model.feedback.DMFeedback
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface FeedbackRepository : JpaRepository<DMFeedback, Long>

