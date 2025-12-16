package com.attendanceio.api.repository.timetable

import com.attendanceio.api.model.timetable.DMWeekDay
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface WeekDayRepository : JpaRepository<DMWeekDay, Short> {
    fun findByName(name: String): DMWeekDay?
}

