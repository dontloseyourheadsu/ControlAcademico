package com.udlap.controlacademico.model

data class UserProfile(
    val uid: String = "",
    val nombre: String = "",
    val matricula: String = "",
    val correo: String = "",
    val rol: String = "alumno"
)
