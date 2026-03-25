package com.udlap.controlacademico

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.google.firebase.auth.FirebaseAuth
import com.udlap.controlacademico.data.Prefs
import com.udlap.controlacademico.viewmodel.HomeNavigation
import com.udlap.controlacademico.viewmodel.HomeViewModel

class HomeActivity : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var prefs: Prefs
    private lateinit var viewModel: HomeViewModel

    private lateinit var tvUser: TextView
    private lateinit var btnRolePanel: Button
    private lateinit var btnRefresh: Button
    private lateinit var btnLogout: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        auth = FirebaseAuth.getInstance()
        prefs = Prefs(this)
        viewModel = ViewModelProvider(this)[HomeViewModel::class.java]

        tvUser = findViewById(R.id.tvUserData)
        btnRolePanel = findViewById(R.id.btnRolePanel)
        btnRefresh = findViewById(R.id.btnRefresh)
        btnLogout = findViewById(R.id.btnLogout)

        observeViewModel()

        btnRefresh.setOnClickListener { viewModel.loadProfile(auth.currentUser?.uid) }
        btnLogout.setOnClickListener { logout() }
        btnRolePanel.setOnClickListener { viewModel.openRolePanelRequested() }
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadProfile(auth.currentUser?.uid)
    }

    private fun observeViewModel() {
        viewModel.state.observe(this) { state ->
            val profile = state.profile
            if (profile != null) {
                tvUser.text = getString(
                    R.string.user_resume,
                    profile.nombre,
                    profile.correo,
                    profile.matricula,
                    state.normalizedRole
                )
                btnRolePanel.text = when (state.normalizedRole) {
                    "admin" -> getString(R.string.open_admin_panel)
                    "profesor" -> getString(R.string.open_professor_panel)
                    else -> getString(R.string.open_student_panel)
                }
            }

            btnRolePanel.isEnabled = !state.isLoading && profile != null
            btnRefresh.isEnabled = !state.isLoading
        }

        viewModel.toastEvent.observe(this) { event ->
            event.getContentIfNotHandled()?.let { showToast(it) }
        }

        viewModel.navigationEvent.observe(this) { event ->
            when (val nav = event.getContentIfNotHandled()) {
                HomeNavigation.ToLogin -> goToLogin()
                HomeNavigation.ToProfileSetup -> {
                    val intent = Intent(this, ProfileSetupActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                    startActivity(intent)
                    finish()
                }
                is HomeNavigation.ToRolePanel -> {
                    val target = when (nav.normalizedRole) {
                        "admin" -> AdminActivity::class.java
                        "profesor" -> ProfessorActivity::class.java
                        else -> StudentActivity::class.java
                    }
                    startActivity(Intent(this, target))
                }
                null -> Unit
            }
        }
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
