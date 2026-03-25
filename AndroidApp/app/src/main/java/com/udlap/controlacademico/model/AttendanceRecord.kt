package com.udlap.controlacademico.model

/**
 * Attendance entry created when a professor validates a student QR scan.
 *
 * @property id Firestore document id for this attendance row.
 * @property subjectId Subject where attendance was registered.
 * @property studentUid Student who attended.
 * @property professorUid Professor who validated attendance.
 * @property timestamp Unix epoch millis used to sort attendance chronologically.
 */
data class AttendanceRecord(
    val id: String = "",
    val subjectId: String = "",
    val studentUid: String = "",
    val professorUid: String = "",
    val timestamp: Long = 0L
)
