package com.udlap.controlacademico

/**
 * Normalizes role variants to the internal routing set used by the app.
 *
 * @param role Raw role text from storage or external source.
 * @return One of `admin`, `profesor`, or `alumno`.
 */
fun normalizeRole(role: String?): String {
    return when (role?.trim()?.lowercase()) {
        "admin", "administrador" -> "admin"
        "profesor", "professor" -> "profesor"
        else -> "alumno"
    }
}
