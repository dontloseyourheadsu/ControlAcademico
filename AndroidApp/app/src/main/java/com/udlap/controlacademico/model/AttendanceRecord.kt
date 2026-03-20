package com.udlap.controlacademico.model

data class AttendanceRecord(
    val id: String = "",
    val subjectId: String = "",
    val studentUid: String = "",
    val professorUid: String = "",
    val timestamp: Long = 0L
)
