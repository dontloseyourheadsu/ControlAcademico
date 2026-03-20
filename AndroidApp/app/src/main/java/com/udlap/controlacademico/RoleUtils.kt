package com.udlap.controlacademico

fun normalizeRole(role: String?): String {
    return when (role?.trim()?.lowercase()) {
        "admin", "administrador" -> "admin"
        "profesor", "professor" -> "profesor"
        else -> "alumno"
    }
}
