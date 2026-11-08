package com.attendanceio.api.config

import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component

/**
 * Schema bits ddl-auto cannot express. poll_vote keeps every ballot ever cast (old choices are
 * stamped removed_at instead of deleted), so "one vote per option per student" may only hold for
 * the active rows: a partial unique index. Databases that got the earlier full unique constraint
 * have it dropped here, and polls from before creator_name get it backfilled. Every statement is
 * idempotent, so this is safe on each start.
 */
@Component
class PollSchemaPatch(private val jdbcTemplate: JdbcTemplate) : ApplicationRunner {
    private val log = LoggerFactory.getLogger(PollSchemaPatch::class.java)

    override fun run(args: ApplicationArguments) {
        try {
            jdbcTemplate.execute("ALTER TABLE poll_vote DROP CONSTRAINT IF EXISTS uk_poll_vote_option_student")
            jdbcTemplate.execute(
                "CREATE UNIQUE INDEX IF NOT EXISTS uk_poll_vote_active " +
                    "ON poll_vote (option_id, student_id) WHERE removed_at IS NULL"
            )
            // Polls asked before creator_name existed get it filled in from the student record.
            jdbcTemplate.update(
                "UPDATE poll p SET creator_name = s.name FROM student s " +
                    "WHERE p.creator_id = s.id AND p.creator_name IS NULL"
            )
        } catch (e: Exception) {
            log.error("Poll schema patch failed: {}", e.message)
        }
    }
}
