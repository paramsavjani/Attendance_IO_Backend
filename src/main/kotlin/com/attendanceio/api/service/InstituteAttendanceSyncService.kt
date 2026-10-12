package com.attendanceio.api.service

import com.attendanceio.api.model.attendance.InstituteAttendanceJson
import com.attendanceio.api.model.attendance.InstituteAttendanceRecordJson
import com.attendanceio.api.repository.attendance.InstituteAttendanceRepositoryAppAction
import com.attendanceio.api.repository.semester.SemesterRepositoryAppAction
import com.attendanceio.api.repository.subject.SubjectRepositoryAppAction
import tools.jackson.databind.ObjectMapper
import jakarta.persistence.EntityManager
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Component
class InstituteAttendanceSyncService(
    private val instituteAttendanceRepositoryAppAction: InstituteAttendanceRepositoryAppAction,
    private val subjectRepositoryAppAction: SubjectRepositoryAppAction,
    private val semesterRepositoryAppAction: SemesterRepositoryAppAction,
    private val mapper: ObjectMapper,
    private val entityManager: EntityManager,
    private val transactionTemplate: TransactionTemplate,
    private val cacheManager: org.springframework.cache.CacheManager
) {
    private val log = LoggerFactory.getLogger(InstituteAttendanceSyncService::class.java)

    @EventListener(ApplicationReadyEvent::class)
    fun onApplicationReady() {
        try {
            transactionTemplate.execute { syncFromJsonFile() }
            cacheManager.getCache("subjectAnalysis")?.clear()
        } catch (e: Exception) {
            log.error("Failed to sync institute attendance data", e)
        }
    }

    fun syncFromJsonFile() {
        val resource = ClassPathResource("data/institute_attendance.json")
        if (!resource.exists()) {
            log.info("No institute_attendance.json found in classpath, skipping sync")
            return
        }

        val data: InstituteAttendanceJson = resource.inputStream.use {
            mapper.readValue(it, InstituteAttendanceJson::class.java)
        }

        if (data.cutoffDate.isNullOrBlank()) {
            log.warn("institute_attendance.json has no cutoffDate, skipping sync")
            return
        }

        val jsonCutoffDate = LocalDate.parse(data.cutoffDate, DateTimeFormatter.ISO_DATE)

        val dbCutoffDate = instituteAttendanceRepositoryAppAction.getLatestOfficialCutoffDate()
        if (dbCutoffDate != null && !jsonCutoffDate.isAfter(dbCutoffDate)) {
            log.info("Institute attendance data is up to date (DB cutoff: {}, JSON cutoff: {})", dbCutoffDate, jsonCutoffDate)
            return
        }

        log.info("Syncing institute attendance: {} records, cutoff {}", data.records.size, jsonCutoffDate)

        val activeSemester = semesterRepositoryAppAction.findByIsActive(true).firstOrNull()
        if (activeSemester == null) {
            log.warn("No active semester found, skipping institute attendance sync")
            return
        }

        val allSubjects = subjectRepositoryAppAction.findBySemesterId(activeSemester.id!!)
        val subjectByCode = mutableMapOf<String, Long>()
        allSubjects.forEach { subj -> subj.id?.let { subjectByCode[subj.code] = it } }

        // Load all existing students into a SID->ID map
        @Suppress("UNCHECKED_CAST")
        val existingStudents = entityManager
            .createNativeQuery("SELECT id, sid FROM student")
            .resultList as List<Array<Any>>
        val studentIdBySid = mutableMapOf<String, Long>()
        existingStudents.forEach { row ->
            val id = (row[0] as Number).toLong()
            val sid = row[1] as String
            studentIdBySid[sid] = id
        }

        // Load existing enrollments
        @Suppress("UNCHECKED_CAST")
        val existingEnrollments = entityManager
            .createNativeQuery("SELECT student_id, subject_id FROM student_subject")
            .resultList as List<Array<Any>>
        val enrollmentSet = mutableSetOf<Pair<Long, Long>>()
        existingEnrollments.forEach { row ->
            enrollmentSet.add(Pair((row[0] as Number).toLong(), (row[1] as Number).toLong()))
        }

        // Load existing official attendance
        @Suppress("UNCHECKED_CAST")
        val existingOfficial = entityManager
            .createNativeQuery("SELECT student_id, subject_id FROM institute_attendance WHERE is_official = true")
            .resultList as List<Array<Any>>
        val officialSet = mutableSetOf<Pair<Long, Long>>()
        existingOfficial.forEach { row ->
            officialSet.add(Pair((row[0] as Number).toLong(), (row[1] as Number).toLong()))
        }

        fun resolveSubjectId(code: String, studentId: Long?): Long? {
            subjectByCode[code]?.let { return it }

            // The institute sheets write programme-specific variants as "PC1(ICT)", while the
            // subjects table stores the same course as "PC1-ICT".
            PROGRAMME_VARIANT.matchEntire(code)?.let { match ->
                val (stem, programme) = match.destructured
                subjectByCode["$stem-$programme"]?.let { return it }
            }

            // A trailing letter is the division ("CT204-A"), not part of the course code.
            val division = DIVISION_SUFFIX.find(code)?.value ?: ""
            val baseCode = code.removeSuffix(division)
            subjectByCode[baseCode]?.let { return it }

            // Courses the institute renumbered for this semester: students' timetables,
            // enrollments and marked lectures still hang off the old code, so the baseline
            // has to land on that same subject row or the two halves won't add up.
            RENUMBERED_SUBJECTS[baseCode]?.let { previousCode ->
                subjectByCode["$previousCode$division"]?.let { return it }
                subjectByCode[previousCode]?.let { return it }
            }

            // "IE420-EL518" is one class cross-listed under two codes, one per programme.
            // Use whichever of the two this student is already enrolled in so their baseline
            // and their own marked lectures sit on the same subject.
            val crossListed = code.split("-").mapNotNull { part -> subjectByCode[part.trim()] }
            if (crossListed.isNotEmpty()) {
                if (studentId != null) {
                    crossListed.firstOrNull { Pair(studentId, it) in enrollmentSet }?.let { return it }
                }
                return crossListed.first()
            }

            if (code.contains(" - ")) {
                code.split(" - ").map { it.trim() }.forEach { part ->
                    subjectByCode[part]?.let { return it }
                }
            }
            return null
        }

        var studentsCreated = 0
        var enrollmentsCreated = 0
        var attendanceCreated = 0
        var skipped = 0

        // Delete existing official records for a clean re-insert
        entityManager.createNativeQuery("DELETE FROM institute_attendance WHERE is_official = true").executeUpdate()
        officialSet.clear()

        // Batch-insert students that don't exist yet
        val newStudentSids = data.records.map { it.rollNumber }.toSet() - studentIdBySid.keys
        val newStudentRecords = data.records
            .filter { it.rollNumber in newStudentSids }
            .distinctBy { it.rollNumber }

        for (batch in newStudentRecords.chunked(100)) {
            if (batch.isEmpty()) continue
            val sql = StringBuilder("INSERT INTO student (sid, name, created_at, updated_at) VALUES ")
            val values = batch.joinToString(",") { "(?, ?, NOW(), NOW())" }
            sql.append(values)
            sql.append(" ON CONFLICT (sid) DO NOTHING RETURNING id, sid")

            val query = entityManager.createNativeQuery(sql.toString())
            var paramIdx = 1
            for (rec in batch) {
                query.setParameter(paramIdx++, rec.rollNumber)
                query.setParameter(paramIdx++, rec.studentName)
            }
            @Suppress("UNCHECKED_CAST")
            val results = query.resultList as List<Array<Any>>
            results.forEach { row ->
                val id = (row[0] as Number).toLong()
                val sid = row[1] as String
                studentIdBySid[sid] = id
                studentsCreated++
            }
        }

        // Re-fetch any students that were created via ON CONFLICT DO NOTHING (already existed but not in our initial cache)
        val missingSids = data.records.map { it.rollNumber }.toSet() - studentIdBySid.keys
        if (missingSids.isNotEmpty()) {
            for (sidBatch in missingSids.chunked(500)) {
                val placeholders = sidBatch.joinToString(",") { "?" }
                val q = entityManager.createNativeQuery("SELECT id, sid FROM student WHERE sid IN ($placeholders)")
                sidBatch.forEachIndexed { idx, sid -> q.setParameter(idx + 1, sid) }
                @Suppress("UNCHECKED_CAST")
                val rows = q.resultList as List<Array<Any>>
                rows.forEach { row ->
                    studentIdBySid[row[1] as String] = (row[0] as Number).toLong()
                }
            }
        }

        // Prepare attendance + enrollment data, deduplicating by (studentId, subjectId) - keep the last occurrence
        data class AttendanceRow(val studentId: Long, val subjectId: Long, val totalClasses: Int, val presentClasses: Int)
        val attendanceMap = mutableMapOf<Pair<Long, Long>, AttendanceRow>()
        val enrollmentsToCreate = mutableSetOf<Pair<Long, Long>>()

        val unresolvedCodes = mutableMapOf<String, Int>()

        for (record in data.records) {
            val studentId = studentIdBySid[record.rollNumber]
            if (studentId == null) {
                skipped++
                continue
            }
            val subjectId = resolveSubjectId(record.subjectCode, studentId)
            if (subjectId == null) {
                unresolvedCodes.merge(record.subjectCode, 1, Int::plus)
                skipped++
                continue
            }

            val key = Pair(studentId, subjectId)
            if (key !in enrollmentSet) {
                enrollmentsToCreate.add(key)
                enrollmentSet.add(key)
            }

            attendanceMap[key] = AttendanceRow(studentId, subjectId, record.totalClasses, record.presentClasses)
        }

        val attendanceRows = attendanceMap.values.toList()

        // Batch-insert enrollments
        for (batch in enrollmentsToCreate.chunked(200)) {
            val sql = StringBuilder("INSERT INTO student_subject (student_id, subject_id, created_at, updated_at) VALUES ")
            sql.append(batch.joinToString(",") { "(?, ?, NOW(), NOW())" })
            sql.append(" ON CONFLICT DO NOTHING")
            val query = entityManager.createNativeQuery(sql.toString())
            var paramIdx = 1
            for ((sid, subId) in batch) {
                query.setParameter(paramIdx++, sid)
                query.setParameter(paramIdx++, subId)
            }
            query.executeUpdate()
            enrollmentsCreated += batch.size
        }

        // Batch-insert official attendance
        for (batch in attendanceRows.chunked(200)) {
            val sql = StringBuilder(
                "INSERT INTO institute_attendance (student_id, subject_id, cutoff_date, total_classes, present_classes, is_official, created_at, updated_at) VALUES "
            )
            sql.append(batch.joinToString(",") { "(?, ?, ?, ?, ?, true, NOW(), NOW())" })
            sql.append(" ON CONFLICT (student_id, subject_id, is_official) DO UPDATE SET cutoff_date = EXCLUDED.cutoff_date, total_classes = EXCLUDED.total_classes, present_classes = EXCLUDED.present_classes, updated_at = NOW()")
            val query = entityManager.createNativeQuery(sql.toString())
            var paramIdx = 1
            for (row in batch) {
                query.setParameter(paramIdx++, row.studentId)
                query.setParameter(paramIdx++, row.subjectId)
                query.setParameter(paramIdx++, jsonCutoffDate)
                query.setParameter(paramIdx++, row.totalClasses)
                query.setParameter(paramIdx++, row.presentClasses)
            }
            query.executeUpdate()
            attendanceCreated += batch.size
        }

        if (unresolvedCodes.isNotEmpty()) {
            val summary = unresolvedCodes.entries
                .sortedByDescending { it.value }
                .joinToString(", ") { "${it.key}=${it.value}" }
            log.warn(
                "Institute attendance: {} subject codes in the sheet match no subject of the active semester, {} records dropped: {}",
                unresolvedCodes.size, unresolvedCodes.values.sum(), summary
            )
        }

        log.info(
            "Institute attendance sync complete: {} processed, {} skipped, " +
            "{} students created, {} enrollments created, {} attendance upserted",
            data.records.size, skipped, studentsCreated, enrollmentsCreated, attendanceCreated
        )
    }

    private companion object {
        val PROGRAMME_VARIANT = Regex("""^([A-Za-z0-9]+)\(([^()]+)\)$""")
        val DIVISION_SUFFIX = Regex("""-[A-Za-z]+$""")

        /**
         * Courses the institute renumbered in the Autumn 2026-27 sheets, mapped back to the code
         * the subjects table (and therefore every timetable and marked lecture) still uses.
         * Names confirmed against the institute's own exam schedules.
         */
        val RENUMBERED_SUBJECTS = mapOf(
            "CT304" to "CT303", // Digital Communication
            "IT305" to "IT304", // Computer Networks
            "IT313" to "IT314", // Software Engineering
            "IT639" to "IT623"  // Data Structures
        )
    }
}
