package com.udlap.controlacademico

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.udlap.controlacademico.data.FirestoreRepository
import com.udlap.controlacademico.model.UserProfile

/**
 * Collects initial profile fields after account registration.
 *
 * The screen reads name/matricula input, writes a [UserProfile] to Firestore,
 * and then routes the user to Home when setup is complete.
 */
class ProfileSetupActivity : AppCompatActivity() {
    /** Firebase auth instance used to identify current user and read email/uid. */
    private lateinit var auth: FirebaseAuth

    /** Repository responsible for persisting profile document in Firestore. */
    private val repository = FirestoreRepository()

    /**
     * Binds inputs and wires save action for profile creation.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile_setup)

        auth = FirebaseAuth.getInstance()

        val etNombre = findViewById<EditText>(R.id.etNombre)
        val etMatricula = findViewById<EditText>(R.id.etMatricula)
        val btnGuardar = findViewById<Button>(R.id.btnGuardarPerfil)

        btnGuardar.setOnClickListener {
            val user = auth.currentUser
            if (user == null) {
                showToast(getString(R.string.msg_session_missing))
                backToLogin()
                return@setOnClickListener
            }

            val nombre = etNombre.text.toString().trim()
            val matricula = etMatricula.text.toString().trim()

            if (nombre.isBlank() || matricula.isBlank()) {
                showToast(getString(R.string.msg_profile_required))
                return@setOnClickListener
            }

            val profile = UserProfile(
                uid = user.uid,
                nombre = nombre,
                matricula = matricula,
                correo = user.email ?: "",
                rol = "alumno"
            )

            repository.saveUserProfile(profile) { ok, error ->
                if (ok) {
                    val intent = Intent(this, HomeActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                    startActivity(intent)
                    finish()
                } else {
                    showToast(error ?: getString(R.string.msg_save_profile_error))
                }
            }
        }
    }

    /**
     * Sends user back to login when profile setup cannot proceed.
     */
    private fun backToLogin() {
        val intent = Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }

    /**
     * Displays short feedback text for validation/save outcomes.
     */
    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
