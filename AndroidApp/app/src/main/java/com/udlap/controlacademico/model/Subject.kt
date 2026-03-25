package com.udlap.controlacademico.model

/**
 * Subject/course assignment managed by admins and consumed by professor/student flows.
 *
 * @property id Firestore document id for this subject.
 * @property nombre Human-readable subject name shown in spinners.
 * @property horario Schedule text rendered in professor/student screens.
 * @property profesorUid Owner professor uid used for authorization checks.
 * @property alumnosUids Student ids currently enrolled in the subject.
 */
data class Subject(
    val id: String = "",
    val nombre: String = "",
    val horario: String = "",
    val profesorUid: String = "",
    val alumnosUids: List<String> = emptyList()
)
