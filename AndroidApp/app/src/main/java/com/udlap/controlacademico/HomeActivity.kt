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

/**
 * Landing screen after login that displays profile summary and routes to role panel.
 *
 * UI <-> MVVM connection:
 * - Requests profile loading from [HomeViewModel].
 * - Renders [com.udlap.controlacademico.viewmodel.HomeViewState] in text/buttons.
 * - Handles navigation events emitted by the ViewModel.
 */
class HomeActivity : AppCompatActivity() {
    /** Firebase auth reference used to resolve current session user and logout. */
    private lateinit var auth: FirebaseAuth

    /** Preferences helper used to clear remembered credentials at logout. */
    private lateinit var prefs: Prefs

    /** ViewModel that coordinates profile loading and navigation decisions. */
    private lateinit var viewModel: HomeViewModel

    /** TextView that renders profile summary data from ViewModel state. */
    private lateinit var tvUser: TextView

    /** Button that opens role-specific panel according to normalized role. */
    private lateinit var btnRolePanel: Button

    /** Button that triggers manual profile refresh from backend. */
    private lateinit var btnRefresh: Button

    /** Button that performs sign-out and returns user to login flow. */
    private lateinit var btnLogout: Button

    /**
     * Binds UI, initializes dependencies, and wires user intents to ViewModel.
     */
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

    /**
     * Refreshes profile whenever screen returns to foreground.
     */
    override fun onResume() {
        super.onResume()
        viewModel.loadProfile(auth.currentUser?.uid)
    }

    /**
     * Connects UI rendering/navigation to ViewModel state and events.
     *
     * - Reads [state] to write TextView and button states.
     * - Reads toast/navigation events to show messages and launch Activities.
     */
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

    /**
     * Clears session and local credentials, then routes user to login.
     */
    private fun logout() {
        auth.signOut()
        prefs.clearCredentials()
        goToLogin()
    }

    /**
     * Starts LoginActivity and clears back stack.
     */
    private fun goToLogin() {
        val intent = Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }

    /**
     * Displays short feedback text from ViewModel events.
     */
    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
