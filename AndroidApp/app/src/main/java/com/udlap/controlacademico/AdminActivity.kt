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

class AdminActivity : AppCompatActivity() {
    private lateinit var viewModel: AdminViewModel

    private lateinit var spUsers: Spinner
    private lateinit var spRoles: Spinner
    private lateinit var etMateria: EditText
    private lateinit var etHorario: EditText
    private lateinit var etProfesorEmail: EditText
    private lateinit var etAlumnosEmails: EditText
    private lateinit var btnUpdateRole: Button
    private lateinit var btnCreateSubject: Button
    private lateinit var btnReload: Button

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

    private fun setupRoleSpinner() {
        val roles = listOf("alumno", "profesor")
        spRoles.adapter = buildSpinnerAdapter(roles)
    }

    private fun updateRoleForSelectedUser() {
        val selectedIndex = spUsers.selectedItemPosition
        val selectedRole = spRoles.selectedItem?.toString().orEmpty()
        viewModel.updateRoleForSelectedUser(selectedIndex, selectedRole)
    }

    private fun createSubject() {
        val nombre = etMateria.text.toString().trim()
        val horario = etHorario.text.toString().trim()
        val profesorEmail = etProfesorEmail.text.toString().trim()
        val alumnos = etAlumnosEmails.text.toString().split(",").map { it.trim() }.filter { it.isNotBlank() }
        viewModel.createOrUpdateSubject(nombre, horario, profesorEmail, alumnos)
    }

    private fun setUsersAdapter(labels: List<String>) {
        spUsers.adapter = buildSpinnerAdapter(labels)
    }

    private fun buildSpinnerAdapter(labels: List<String>): ArrayAdapter<String> {
        return ArrayAdapter(this, R.layout.spinner_item, labels).also {
            it.setDropDownViewResource(R.layout.spinner_dropdown_item)
        }
    }

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

    private fun clearSubjectForm() {
        etMateria.text.clear()
        etHorario.text.clear()
        etProfesorEmail.text.clear()
        etAlumnosEmails.text.clear()
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
