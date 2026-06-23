package com.attendanceio.api.application.attendance.actions

import com.attendanceio.api.model.attendance.AttendanceStatus
import com.attendanceio.api.model.attendance.MyAttendanceResponse
import com.attendanceio.api.model.attendance.SubjectStatsResponse
import com.attendanceio.api.model.attendance.TodayAttendanceRecord
import com.attendanceio.api.repository.attendance.AttendanceRepositoryAppAction
import com.attendanceio.api.repository.attendance.InstituteAttendanceRepositoryAppAction
import com.attendanceio.api.repository.semester.SemesterRepositoryAppAction
import com.attendanceio.api.repository.student.StudentSubjectRepositoryAppAction
import com.attendanceio.api.repository.timetable.StudentLabTimetableRepositoryAppAction
import com.attendanceio.api.repository.timetable.StudentTimetableRepositoryAppAction
import com.attendanceio.api.repository.timetable.StudentTutorialTimetableRepositoryAppAction
import com.attendanceio.api.service.AttendanceCalculationService
import com.attendanceio.api.service.ClassCalculationService
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.LocalTime

@Component
class GetMyAttendanceAppAction(
    private val attendanceRepositoryAppAction: AttendanceRepositoryAppAction,
    private val instituteAttendanceRepositoryAppAction: InstituteAttendanceRepositoryAppAction,
    private val semesterRepositoryAppAction: SemesterRepositoryAppAction,
    private val studentTimetableRepositoryAppAction: StudentTimetableRepositoryAppAction,
    private val studentLabTimetableRepositoryAppAction: StudentLabTimetableRepositoryAppAction,
    private val studentTutorialTimetableRepositoryAppAction: StudentTutorialTimetableRepositoryAppAction,
    private val studentSubjectRepositoryAppAction: StudentSubjectRepositoryAppAction,
    private val classCalculationService: ClassCalculationService,
    private val attendanceCalculationService: AttendanceCalculationService
) {
    fun execute(studentId: Long, targetDate: LocalDate, view: String): MyAttendanceResponse {
        if (view == "official") return buildOfficialView(studentId)

        val officialCutoff = instituteAttendanceRepositoryAppAction.getLatestOfficialCutoffDate()
        val attendanceResults = attendanceRepositoryAppAction.calculateStudentAttendanceBySubject(studentId)

        val currentSemester = semesterRepositoryAppAction.findByIsActive(true).firstOrNull()
        val studentTimetable = if (currentSemester != null)
            studentTimetableRepositoryAppAction.findByStudentIdAndSemesterId(studentId, currentSemester.id!!)
        else emptyList()

        val labTimetable = if (currentSemester != null)
            studentLabTimetableRepositoryAppAction.findByStudentIdAndSemesterId(studentId, currentSemester.id!!)
        else emptyList()
        val tutorialTimetable = if (currentSemester != null)
            studentTutorialTimetableRepositoryAppAction.findByStudentIdAndSemesterId(studentId, currentSemester.id!!)
        else emptyList()

        val allLabTutTimeSlots = buildLabTutTimeSlots(labTimetable, tutorialTimetable)

        val allAttendanceRecords = attendanceRepositoryAppAction.findByStudentId(studentId)
        val lectureOnlyRecords = allAttendanceRecords.filter { att ->
            if (att.customStartTime != null && att.customEndTime != null)
                Pair(att.customStartTime!!, att.customEndTime!!) !in allLabTutTimeSlots
            else true
        }

        val endDate = classCalculationService.getConfiguredEndDate() ?: targetDate

        val subjectStats = attendanceResults.map { result ->
            val subjectId = result.subjectId
            val subjectTimetableEntries = studentTimetable.filter { it.subject?.id == subjectId }

            val lecturesToDate = lectureOnlyRecords.filter {
                it.subject?.id == subjectId && it.lectureDate != null && !it.lectureDate!!.isAfter(targetDate)
            }

            val lecturePresent = lecturesToDate.count { it.status == AttendanceStatus.PRESENT }
            val lectureAbsent = lecturesToDate.count { it.status == AttendanceStatus.ABSENT }

            val customCount = lecturesToDate.count { it.customStartTime != null && it.customEndTime != null && !it.isExtraClass }
            val slotCount = lecturesToDate.count { it.timeSlot != null && it.customStartTime == null && it.customEndTime == null && !it.isExtraClass }
            val extraCount = lecturesToDate.count { it.isExtraClass && it.timeSlot == null && it.customStartTime == null && it.customEndTime == null }
            val cancelledCount = lecturesToDate.count { it.status == AttendanceStatus.CANCELLED }

            val datesWithRecords = lecturesToDate
                .filter { (it.customStartTime != null && it.customEndTime != null) || it.timeSlot != null }
                .mapNotNull { it.lectureDate }.toSet()

            val timetableCount = classCalculationService.countScheduledClassesExcludingDates(
                subjectTimetableEntries, targetDate, datesWithRecords
            )

            val calculatedTotal = maxOf(0, customCount + slotCount + extraCount + timetableCount - cancelledCount)
            val actualAttendanceMin = lecturesToDate.count { it.status != AttendanceStatus.CANCELLED }
            val totalClasses = maxOf(calculatedTotal, actualAttendanceMin)

            // Until-end-date totals for bunkable calculation
            val lecturesToEnd = lectureOnlyRecords.filter {
                it.subject?.id == subjectId && it.lectureDate != null && !it.lectureDate!!.isAfter(endDate)
            }
            val customCountEnd = lecturesToEnd.count { it.customStartTime != null && it.customEndTime != null && !it.isExtraClass }
            val slotCountEnd = lecturesToEnd.count { it.timeSlot != null && it.customStartTime == null && it.customEndTime == null && !it.isExtraClass }
            val extraCountEnd = lecturesToEnd.count { it.isExtraClass && it.timeSlot == null && it.customStartTime == null && it.customEndTime == null }
            val cancelledCountEnd = lecturesToEnd.count { it.status == AttendanceStatus.CANCELLED }
            val datesWithRecordsEnd = lecturesToEnd
                .filter { (it.customStartTime != null && it.customEndTime != null) || it.timeSlot != null }
                .mapNotNull { it.lectureDate }.toSet()
            val timetableCountEnd = classCalculationService.countScheduledClassesExcludingDates(
                subjectTimetableEntries, endDate, datesWithRecordsEnd
            )
            val rawTotalEnd = maxOf(0, customCountEnd + slotCountEnd + extraCountEnd + timetableCountEnd - cancelledCountEnd)

            var finalPresent = lecturePresent
            var finalAbsent = lectureAbsent
            var finalTotal = totalClasses
            var finalTotalUntilEnd = maxOf(totalClasses, rawTotalEnd)

            val hasOfficialBaseline = result.baseTotal > 0 && result.baseCutoffDate != null
            if (hasOfficialBaseline) {
                val cutoff = result.baseCutoffDate!!

                val afterCutoffToDate = lectureOnlyRecords.filter {
                    it.subject?.id == subjectId && it.lectureDate != null &&
                    it.lectureDate!!.isAfter(cutoff) && !it.lectureDate!!.isAfter(targetDate)
                }
                val presentAfter = afterCutoffToDate.count { it.status == AttendanceStatus.PRESENT }
                val absentAfter = afterCutoffToDate.count { it.status == AttendanceStatus.ABSENT }
                val cancelledAfter = afterCutoffToDate.count { it.status == AttendanceStatus.CANCELLED }

                finalPresent = result.basePresent + presentAfter
                finalAbsent = result.baseAbsent + absentAfter
                finalTotal = maxOf(0, result.baseTotal + afterCutoffToDate.size - cancelledAfter)

                val afterCutoffToEnd = lectureOnlyRecords.filter {
                    it.subject?.id == subjectId && it.lectureDate != null &&
                    it.lectureDate!!.isAfter(cutoff) && !it.lectureDate!!.isAfter(endDate)
                }
                val cancelledAfterToEnd = afterCutoffToEnd.count { it.status == AttendanceStatus.CANCELLED }
                val datesAfterCutoff = afterCutoffToEnd.mapNotNull { it.lectureDate }.toSet()

                val timetableAfterCutoff = classCalculationService.countScheduledClassesBetween(
                    subjectTimetableEntries,
                    fromDate = cutoff.plusDays(1),
                    toDate = endDate,
                    excludedDates = datesAfterCutoff
                )

                finalTotalUntilEnd = maxOf(
                    finalTotal,
                    result.baseTotal + afterCutoffToEnd.size - cancelledAfterToEnd + timetableAfterCutoff
                )
            }

            val studentSubject = studentSubjectRepositoryAppAction.findByStudentIdAndSubjectId(studentId, subjectId)
            val minRequired = studentSubject?.minimumCriteria ?: 75
            val percentage = attendanceCalculationService.calculatePercentage(finalPresent, finalTotal)
            val classesNeeded = attendanceCalculationService.calculateClassesNeeded(finalPresent, finalTotal, minRequired)
            val bunkableClasses = attendanceCalculationService.calculateBunkableClasses(finalPresent, finalTotal, finalTotalUntilEnd, minRequired)

            SubjectStatsResponse(
                subjectId = subjectId.toString(),
                present = finalPresent,
                absent = finalAbsent,
                total = finalTotal,
                totalUntilEndDate = finalTotalUntilEnd,
                percentage = percentage,
                classesNeeded = classesNeeded,
                bunkableClasses = bunkableClasses
            )
        }

        val dateRecords = attendanceRepositoryAppAction.findByStudentIdAndLectureDate(studentId, targetDate)
        val extraIndexById = mutableMapOf<Long, Int>()
        dateRecords
            .filter { it.isExtraClass && it.timeSlot == null && it.customStartTime == null && it.customEndTime == null }
            .groupBy { it.subject?.id to it.lectureDate }
            .forEach { (_, list) ->
                list.sortedBy { it.id }.forEachIndexed { idx, rec ->
                    rec.id?.let { extraIndexById[it] = idx }
                }
            }
        val todayAttendance = dateRecords.map { att ->
            TodayAttendanceRecord(
                attendanceId = att.id,
                subjectId = att.subject?.id?.toString() ?: "",
                lectureDate = att.lectureDate?.toString() ?: "",
                status = att.status.name.lowercase(),
                timeSlot = att.timeSlot?.id?.toInt()?.minus(1),
                startTime = att.customStartTime?.toString(),
                endTime = att.customEndTime?.toString(),
                isExtraClass = att.isExtraClass,
                extraClassIndex = att.id?.let { extraIndexById[it] }
            )
        }

        return MyAttendanceResponse(
            subjectStats = subjectStats,
            todayAttendance = todayAttendance,
            viewType = "total",
            officialCutoffDate = officialCutoff?.toString()
        )
    }

    private fun buildOfficialView(studentId: Long): MyAttendanceResponse {
        val records = instituteAttendanceRepositoryAppAction.findByStudentIdAndIsOfficial(studentId, true)
        val cutoff = records.maxByOrNull { it.cutoffDate ?: LocalDate.MIN }?.cutoffDate
        val stats = records.map { rec ->
            val total = rec.totalClasses
            val present = rec.presentClasses
            val percentage = if (total > 0) present * 100.0 / total else 0.0
            SubjectStatsResponse(
                subjectId = rec.subject?.id?.toString() ?: "",
                present = present,
                absent = total - present,
                total = total,
                percentage = percentage
            )
        }
        return MyAttendanceResponse(
            subjectStats = stats,
            todayAttendance = emptyList(),
            viewType = "official",
            officialCutoffDate = cutoff?.toString()
        )
    }

    private fun buildLabTutTimeSlots(labTimetable: List<*>, tutorialTimetable: List<*>): Set<Pair<LocalTime, LocalTime>> {
        val result = mutableSetOf<Pair<LocalTime, LocalTime>>()
        fun addEntry(start: LocalTime?, end: LocalTime?) {
            if (start != null && end != null) result.add(Pair(start, end))
        }
        labTimetable.forEach { entry ->
            if (entry is com.attendanceio.api.model.timetable.DMStudentLabTimetable)
                addEntry(entry.customStartTime ?: entry.slot?.startTime, entry.customEndTime ?: entry.slot?.endTime)
        }
        tutorialTimetable.forEach { entry ->
            if (entry is com.attendanceio.api.model.timetable.DMStudentTutorialTimetable)
                addEntry(entry.customStartTime ?: entry.slot?.startTime, entry.customEndTime ?: entry.slot?.endTime)
        }
        return result
    }
}
