package com.udlap.controlacademico.model

data class GradeRecord(
    val id: String = "",
    val subjectId: String = "",
    val studentUid: String = "",
    val professorUid: String = "",
    val calificacion: Double = 0.0,
    val updatedAt: Long = 0L
)
