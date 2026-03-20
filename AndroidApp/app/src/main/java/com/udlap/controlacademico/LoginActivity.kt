package com.udlap.controlacademico

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.udlap.controlacademico.data.Prefs

class LoginActivity : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var prefs: Prefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        auth = FirebaseAuth.getInstance()
        prefs = Prefs(this)

        val etEmail = findViewById<EditText>(R.id.etLoginCorreo)
        val etPassword = findViewById<EditText>(R.id.etLoginPass)
        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val btnRegister = findViewById<Button>(R.id.btnRegister)

        btnLogin.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString()
            if (email.isBlank() || password.isBlank()) {
                showToast(getString(R.string.msg_email_password_required))
                return@setOnClickListener
            }
            loginWithFirebase(email, password)
        }

        btnRegister.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString()
            if (email.isBlank() || password.isBlank()) {
                showToast(getString(R.string.msg_email_password_required))
                return@setOnClickListener
            }
            registerWithFirebase(email, password)
        }

        if (prefs.hasCredentials()) {
            autoLoginWithSavedCredentials()
        }
    }

    private fun loginWithFirebase(email: String, password: String) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    prefs.saveCredentials(email, password)
                    goToHome()
                } else {
                    showToast(task.exception?.localizedMessage ?: getString(R.string.msg_login_error))
                }
            }
    }

    private fun registerWithFirebase(email: String, password: String) {
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    prefs.saveCredentials(email, password)
                    val intent = Intent(this, ProfileSetupActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                    startActivity(intent)
                    finish()
                } else {
                    showToast(task.exception?.localizedMessage ?: getString(R.string.msg_register_error))
                }
            }
    }

    private fun autoLoginWithSavedCredentials() {
        val email = prefs.getEmail()
        val password = prefs.getPassword()

        if (email.isBlank() || password.isBlank()) return

        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    goToHome()
                } else {
                    prefs.clearCredentials()
                }
            }
    }

    private fun goToHome() {
        val intent = Intent(this, HomeActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
