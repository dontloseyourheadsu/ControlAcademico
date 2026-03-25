package com.udlap.controlacademico

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.udlap.controlacademico.viewmodel.AdminStatus
import com.udlap.controlacademico.viewmodel.AdminViewModel

/**
 * Admin panel UI for user role updates and subject assignment management.
 *
 * UI <-> MVVM connection:
 * - Reads text/spinner input from form widgets.
 * - Sends intents to [AdminViewModel].
 * - Observes state/events to render controls, toasts, and form clearing.
 */
class AdminActivity : AppCompatActivity() {
    /** ViewModel that owns admin business logic and remote operations. */
    private lateinit var viewModel: AdminViewModel

    /** Spinner that renders current user labels for role assignment. */
    private lateinit var spUsers: Spinner

    /** Spinner that provides target role choice (`alumno`/`profesor`). */
    private lateinit var spRoles: Spinner

    /** Input where admin types subject name. */
    private lateinit var etMateria: EditText

    /** Input where admin types subject schedule. */
    private lateinit var etHorario: EditText

    /** Input where admin types professor email for subject ownership. */
    private lateinit var etProfesorEmail: EditText

    /** Input where admin types comma-separated student emails to enroll. */
    private lateinit var etAlumnosEmails: EditText

    /** Button that submits role updates for selected user. */
    private lateinit var btnUpdateRole: Button

    /** Button that creates or updates subject assignments from form fields. */
    private lateinit var btnCreateSubject: Button

    /** Button that reloads users list from backend. */
    private lateinit var btnReload: Button

    /**
     * Inflates layout, binds widgets, wires listeners, and starts initial user load.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin)
        viewModel = ViewModelProvider(this)[AdminViewModel::class.java]

        spUsers = findViewById(R.id.spUsers)
        spRoles = findViewById(R.id.spRoles)
        etMateria = findViewById(R.id.etMateriaNombre)
        etHorario = findViewById(R.id.etMateriaHorario)
        etProfesorEmail = findViewById(R.id.etProfesorEmail)
        etAlumnosEmails = findViewById(R.id.etAlumnosEmails)

        btnUpdateRole = findViewById(R.id.btnUpdateRole)
        btnCreateSubject = findViewById(R.id.btnCreateSubject)
        btnReload = findViewById(R.id.btnReloadUsers)
        val btnBack = findViewById<Button>(R.id.btnBackFromAdmin)  // FIX: was never wired up

        setupRoleSpinner()
        setUsersAdapter(emptyList())
        observeViewModel()
        viewModel.loadUsers()

        btnBack.setOnClickListener { finish() }
        btnReload.setOnClickListener { viewModel.loadUsers() }
        btnUpdateRole.setOnClickListener { updateRoleForSelectedUser() }
        btnCreateSubject.setOnClickListener { createSubject() }
    }

    /**
     * Populates role spinner with fixed role options used in update flow.
     */
    private fun setupRoleSpinner() {
        val roles = listOf("alumno", "profesor")
        spRoles.adapter = buildSpinnerAdapter(roles)
    }

    /**
     * Reads selected user/role from spinners and forwards intent to ViewModel.
     */
    private fun updateRoleForSelectedUser() {
        val selectedIndex = spUsers.selectedItemPosition
        val selectedRole = spRoles.selectedItem?.toString().orEmpty()
        viewModel.updateRoleForSelectedUser(selectedIndex, selectedRole)
    }

    /**
     * Reads subject form inputs and submits create/update intent to ViewModel.
     */
    private fun createSubject() {
        val nombre = etMateria.text.toString().trim()
        val horario = etHorario.text.toString().trim()
        val profesorEmail = etProfesorEmail.text.toString().trim()
        val alumnos = etAlumnosEmails.text.toString().split(",").map { it.trim() }.filter { it.isNotBlank() }
        viewModel.createOrUpdateSubject(nombre, horario, profesorEmail, alumnos)
    }

    /**
     * Writes user labels into users spinner adapter.
     */
    private fun setUsersAdapter(labels: List<String>) {
        spUsers.adapter = buildSpinnerAdapter(labels)
    }

    /**
     * Builds themed spinner adapter used by both users and roles spinners.
     */
    private fun buildSpinnerAdapter(labels: List<String>): ArrayAdapter<String> {
        return ArrayAdapter(this, R.layout.spinner_item, labels).also {
            it.setDropDownViewResource(R.layout.spinner_dropdown_item)
        }
    }

    /**
     * Enables/disables controls based on current admin state.
     *
     * Writes to spinner/button enabled flags only.
     */
    private fun applyUiState(status: AdminStatus) {
        val busy = status == AdminStatus.LOADING_USERS ||
            status == AdminStatus.SUBMITTING_ROLE ||
            status == AdminStatus.SUBMITTING_SUBJECT
        val usersReady = status == AdminStatus.USERS_READY

        spUsers.isEnabled = usersReady && !busy
        spRoles.isEnabled = usersReady && !busy
        btnUpdateRole.isEnabled = usersReady && !busy
        btnReload.isEnabled = !busy
        btnCreateSubject.isEnabled = !busy
    }

    /**
     * Subscribes UI to ViewModel state and one-time events.
     *
     * - State observer writes spinner content and enabled states.
     * - Toast observer shows transient messages.
     * - Clear-form observer resets text fields after successful submit.
     */
    private fun observeViewModel() {
        viewModel.state.observe(this) { state ->
            setUsersAdapter(state.userLabels)
            applyUiState(state.status)
        }

        viewModel.toastEvent.observe(this) { event ->
            event.getContentIfNotHandled()?.let { showToast(it) }
        }

        viewModel.clearFormEvent.observe(this) { event ->
            event.getContentIfNotHandled()?.let { clearSubjectForm() }
        }
    }

    /**
     * Clears subject-related form inputs after successful create/update operation.
     */
    private fun clearSubjectForm() {
        etMateria.text.clear()
        etHorario.text.clear()
        etProfesorEmail.text.clear()
        etAlumnosEmails.text.clear()
    }

    /**
     * Displays a short user-facing message.
     */
    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
