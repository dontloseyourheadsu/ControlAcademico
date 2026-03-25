package com.udlap.controlacademico.model

/**
 * User profile persisted after authentication and used for role-based routing.
 *
 * @property uid Firebase Authentication user identifier.
 * @property nombre Full name captured in profile setup.
 * @property matricula Student/academic registration identifier.
 * @property correo Contact email and lookup key for admin flows.
 * @property rol Authorization role used to route to admin/professor/student panels.
 */
data class UserProfile(
    val uid: String = "",
    val nombre: String = "",
    val matricula: String = "",
    val correo: String = "",
    val rol: String = "alumno"
)
