package com.attendanceio.api.model.timetable

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table

@Entity
@Table(name = "week_days")
class DMWeekDay {
    @Column(name = "name", nullable = false, length = 10)
    var name: String = ""
}

