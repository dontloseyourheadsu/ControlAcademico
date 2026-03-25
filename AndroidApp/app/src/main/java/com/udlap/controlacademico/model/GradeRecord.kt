package com.udlap.controlacademico.model

/**
 * Grade entity that links one student to one subject and professor.
 *
 * @property id Stable id for upsert behavior (`subjectId_studentUid`).
 * @property subjectId Subject receiving the grade.
 * @property studentUid Student who owns the grade.
 * @property professorUid Professor who assigned the grade.
 * @property calificacion Numeric score from 0 to 100.
 * @property updatedAt Unix epoch millis used to know when grade was last changed.
 */
data class GradeRecord(
    val id: String = "",
    val subjectId: String = "",
    val studentUid: String = "",
    val professorUid: String = "",
    val calificacion: Double = 0.0,
    val updatedAt: Long = 0L
)
