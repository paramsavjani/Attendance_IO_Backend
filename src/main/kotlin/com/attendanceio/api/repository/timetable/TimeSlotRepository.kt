package com.attendanceio.api.repository.timetable

import com.attendanceio.api.model.timetable.DMTimeSlot
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface TimeSlotRepository : JpaRepository<DMTimeSlot, Short> {
}

