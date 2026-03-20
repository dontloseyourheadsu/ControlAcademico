package com.udlap.controlacademico.data

import android.content.Context

class Prefs(context: Context) {
    private val sharedPreferences =
        context.getSharedPreferences("ControlAcademicoPrefs", Context.MODE_PRIVATE)

    fun saveCredentials(email: String, password: String) {
        sharedPreferences.edit()
            .putString(KEY_EMAIL, email)
            .putString(KEY_PASSWORD, password)
            .apply()
    }

    fun getEmail(): String = sharedPreferences.getString(KEY_EMAIL, "") ?: ""

    fun getPassword(): String = sharedPreferences.getString(KEY_PASSWORD, "") ?: ""

    fun hasCredentials(): Boolean = getEmail().isNotBlank() && getPassword().isNotBlank()

    fun clearCredentials() {
        sharedPreferences.edit()
            .remove(KEY_EMAIL)
            .remove(KEY_PASSWORD)
            .apply()
    }

    companion object {
        private const val KEY_EMAIL = "USER_EMAIL"
        private const val KEY_PASSWORD = "USER_PASS"
    }
}
