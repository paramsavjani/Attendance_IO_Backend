package com.attendanceio.api.model.timetable

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalTime

@Entity
@Table(name = "time_slots")
class DMTimeSlot {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    var id: Short? = null

    @Column(name = "start_time", nullable = false)
    var startTime: LocalTime? = null

    @Column(name = "end_time", nullable = false)
    var endTime: LocalTime? = null
}

