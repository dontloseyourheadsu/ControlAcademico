package com.udlap.controlacademico.data

import android.content.Context

/**
 * Small wrapper around [android.content.SharedPreferences] for login persistence.
 *
 * This class keeps credential read/write logic out of Activities so UI code stays
 * focused on user interactions.
 *
 * @property sharedPreferences Local key-value store for remembered login values.
 */
class Prefs(context: Context) {
    private val sharedPreferences =
        context.getSharedPreferences("ControlAcademicoPrefs", Context.MODE_PRIVATE)

    /**
     * Stores the latest successful email/password pair for silent sign-in.
     */
    fun saveCredentials(email: String, password: String) {
        sharedPreferences.edit()
            .putString(KEY_EMAIL, email)
            .putString(KEY_PASSWORD, password)
            .apply()
    }

    /**
     * Returns stored email string, or empty when not present.
     */
    fun getEmail(): String = sharedPreferences.getString(KEY_EMAIL, "") ?: ""

    /**
     * Returns stored password string, or empty when not present.
     */
    fun getPassword(): String = sharedPreferences.getString(KEY_PASSWORD, "") ?: ""

    /**
     * Indicates whether both credential fields are currently available.
     */
    fun hasCredentials(): Boolean = getEmail().isNotBlank() && getPassword().isNotBlank()

    /**
     * Removes saved credentials, typically on explicit logout or failed silent sign-in.
     */
    fun clearCredentials() {
        sharedPreferences.edit()
            .remove(KEY_EMAIL)
            .remove(KEY_PASSWORD)
            .apply()
    }

    companion object {
        /** SharedPreferences key used to store user email. */
        private const val KEY_EMAIL = "USER_EMAIL"

        /** SharedPreferences key used to store user password. */
        private const val KEY_PASSWORD = "USER_PASS"
    }
}
