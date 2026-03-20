package com.udlap.controlacademico.model

data class Subject(
    val id: String = "",
    val nombre: String = "",
    val horario: String = "",
    val profesorUid: String = "",
    val alumnosUids: List<String> = emptyList()
)
