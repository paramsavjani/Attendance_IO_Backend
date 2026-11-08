package com.attendanceio.api.application.poll

import com.attendanceio.api.model.poll.DMPoll
import com.attendanceio.api.repository.student.StudentSubjectRepositoryAppAction
import com.attendanceio.api.service.FcmNotificationService
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Tells a subject's class that someone asked a new poll. Sending to a whole class takes a while,
 * so it runs on its own thread after the poll is saved and never delays the creator's request.
 */
@Component
class PollNotifier(
    private val studentSubjectRepositoryAppAction: StudentSubjectRepositoryAppAction,
    private val fcmNotificationService: FcmNotificationService
) {
    private val log = LoggerFactory.getLogger(PollNotifier::class.java)
    private val executor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "poll-notifier").apply { isDaemon = true }
    }

    fun notifyNewPoll(poll: DMPoll) {
        val pollId = poll.id ?: return
        val subject = poll.subject ?: return
        val subjectId = subject.id ?: return
        val creatorId = poll.creator?.id
        val title = "New poll in ${subject.name}"
        val body = if (poll.anonymous) poll.question
        else "${PollViewAssembler.displayName(poll.creator?.name)} asks: ${poll.question}"

        executor.submit {
            try {
                val tokens = studentSubjectRepositoryAppAction.findBySubjectId(subjectId)
                    .mapNotNull { it.student }
                    .filter { it.id != creatorId && it.pollNotificationsEnabled }
                    .mapNotNull { it.fcmToken?.takeIf(String::isNotBlank) }
                    .distinct()
                if (tokens.isEmpty()) return@submit
                val sent = fcmNotificationService.sendNotificationToMultiple(
                    fcmTokens = tokens,
                    title = title,
                    body = body,
                    data = mapOf("type" to "poll", "pollId" to pollId.toString())
                )
                log.info("Poll {} notification sent to {}/{} classmates in {}", pollId, sent, tokens.size, subject.code)
            } catch (e: Exception) {
                log.warn("Poll {} notification failed: {}", pollId, e.message)
            }
        }
    }

    @PreDestroy
    fun shutdown() {
        executor.shutdown()
    }
}
