package com.udlap.controlacademico

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.udlap.controlacademico.data.FirestoreRepository
import com.udlap.controlacademico.data.Prefs
import com.udlap.controlacademico.model.UserProfile

class HomeActivity : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var prefs: Prefs
    private val repository = FirestoreRepository()

    private lateinit var tvUser: TextView
    private lateinit var btnRolePanel: Button
    private lateinit var btnRefresh: Button
    private lateinit var btnLogout: Button

    private var currentProfile: UserProfile? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        auth = FirebaseAuth.getInstance()
        prefs = Prefs(this)

        tvUser = findViewById(R.id.tvUserData)
        btnRolePanel = findViewById(R.id.btnRolePanel)
        btnRefresh = findViewById(R.id.btnRefresh)
        btnLogout = findViewById(R.id.btnLogout)

        btnRefresh.setOnClickListener { loadProfile() }
        btnLogout.setOnClickListener { logout() }
        btnRolePanel.setOnClickListener { openRolePanel() }
    }

    override fun onResume() {
        super.onResume()
        loadProfile()
    }

    private fun loadProfile() {
        val user = auth.currentUser
        if (user == null) {
            goToLogin()
            return
        }

        repository.getUserProfile(user.uid) { profile, error ->
            if (profile == null) {
                if (error != null) {
                    showToast(error)
                }
                val intent = Intent(this, ProfileSetupActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                startActivity(intent)
                finish()
                return@getUserProfile
            }

            currentProfile = profile
            val normalizedRole = normalizeRole(profile.rol)
            tvUser.text = getString(
                R.string.user_resume,
                profile.nombre,
                profile.correo,
                profile.matricula,
                normalizedRole
            )
            btnRolePanel.text = when (normalizedRole) {
                "admin" -> getString(R.string.open_admin_panel)
                "profesor" -> getString(R.string.open_professor_panel)
                else -> getString(R.string.open_student_panel)
            }
        }
    }

    private fun openRolePanel() {
        val role = normalizeRole(currentProfile?.rol)
        val target = when (role) {
            "admin" -> AdminActivity::class.java
            "profesor" -> ProfessorActivity::class.java
            else -> StudentActivity::class.java
        }
        startActivity(Intent(this, target))
    }

    private fun logout() {
        auth.signOut()
        prefs.clearCredentials()
        goToLogin()
    }

    private fun goToLogin() {
        val intent = Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
