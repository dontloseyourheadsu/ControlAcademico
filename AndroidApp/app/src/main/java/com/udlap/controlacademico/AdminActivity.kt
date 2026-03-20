package com.udlap.controlacademico

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.udlap.controlacademico.data.FirestoreRepository
import com.udlap.controlacademico.model.Subject
import com.udlap.controlacademico.model.UserProfile

class AdminActivity : AppCompatActivity() {
    private val repository = FirestoreRepository()

    private lateinit var spUsers: Spinner
    private lateinit var spRoles: Spinner
    private lateinit var etMateria: EditText
    private lateinit var etHorario: EditText
    private lateinit var etProfesorEmail: EditText
    private lateinit var etAlumnosEmails: EditText

    private var users: List<UserProfile> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin)

        spUsers = findViewById(R.id.spUsers)
        spRoles = findViewById(R.id.spRoles)
        etMateria = findViewById(R.id.etMateriaNombre)
        etHorario = findViewById(R.id.etMateriaHorario)
        etProfesorEmail = findViewById(R.id.etProfesorEmail)
        etAlumnosEmails = findViewById(R.id.etAlumnosEmails)

        val btnUpdateRole = findViewById<Button>(R.id.btnUpdateRole)
        val btnCreateSubject = findViewById<Button>(R.id.btnCreateSubject)
        val btnReload = findViewById<Button>(R.id.btnReloadUsers)

        setupRoleSpinner()
        loadUsers()

        btnReload.setOnClickListener { loadUsers() }
        btnUpdateRole.setOnClickListener { updateRoleForSelectedUser() }
        btnCreateSubject.setOnClickListener { createSubject() }
    }

    private fun setupRoleSpinner() {
        val roles = listOf("alumno", "profesor")
        spRoles.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, roles)
    }

    private fun loadUsers() {
        repository.getAllUsers { allUsers, error ->
            if (error != null) {
                showToast(error)
                return@getAllUsers
            }
            users = allUsers.sortedBy { it.correo }
            val labels = users.map { "${it.nombre} (${it.correo}) - ${it.rol}" }
            spUsers.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, labels)
        }
    }

    private fun updateRoleForSelectedUser() {
        val selectedIndex = spUsers.selectedItemPosition
        if (selectedIndex < 0 || users.isEmpty()) {
            showToast(getString(R.string.msg_user_required))
            return
        }

        val selectedUser = users[selectedIndex]
        val selectedRole = spRoles.selectedItem?.toString().orEmpty()
        val role = when (selectedRole) {
            "profesor" -> "profesor"
            else -> "alumno"
        }

        repository.updateUserRole(selectedUser.uid, role) { ok, error ->
            if (ok) {
                showToast(getString(R.string.msg_role_updated))
                loadUsers()
            } else {
                showToast(error ?: getString(R.string.msg_role_update_error))
            }
        }
    }

    private fun createSubject() {
        val nombre = etMateria.text.toString().trim()
        val horario = etHorario.text.toString().trim()
        val profesorEmail = etProfesorEmail.text.toString().trim()
        val alumnos = etAlumnosEmails.text.toString().split(",").map { it.trim() }.filter { it.isNotBlank() }

        if (nombre.isBlank() || horario.isBlank() || profesorEmail.isBlank() || alumnos.isEmpty()) {
            showToast(getString(R.string.msg_subject_required))
            return
        }

        repository.getUserByEmail(profesorEmail) { profesor, error ->
            if (error != null) {
                showToast(error)
                return@getUserByEmail
            }
            if (profesor == null) {
                showToast(getString(R.string.msg_professor_not_found))
                return@getUserByEmail
            }

            resolveStudentUids(alumnos) { uids ->
                if (uids.isEmpty()) {
                    showToast(getString(R.string.msg_students_not_found))
                    return@resolveStudentUids
                }

                repository.getSubjectByNameAndProfessor(nombre, profesor.uid) { existing, findError ->
                    if (findError != null) {
                        showToast(findError)
                        return@getSubjectByNameAndProfessor
                    }

                    if (existing == null) {
                        val subject = Subject(
                            nombre = nombre,
                            horario = horario,
                            profesorUid = profesor.uid,
                            alumnosUids = uids
                        )

                        repository.createSubject(subject) { ok, createError ->
                            if (ok) {
                                clearSubjectForm()
                                showToast(getString(R.string.msg_subject_created))
                            } else {
                                showToast(createError ?: getString(R.string.msg_subject_create_error))
                            }
                        }
                    } else {
                        repository.updateSubjectAssignments(existing.id, horario, uids) { ok, updateError ->
                            if (ok) {
                                clearSubjectForm()
                                showToast(getString(R.string.msg_subject_updated))
                            } else {
                                showToast(updateError ?: getString(R.string.msg_subject_update_error))
                            }
                        }
                    }
                }
            }
        }
    }

    private fun resolveStudentUids(emails: List<String>, onDone: (List<String>) -> Unit) {
        val resolved = mutableListOf<String>()

        fun resolve(index: Int) {
            if (index >= emails.size) {
                onDone(resolved)
                return
            }

            repository.getUserByEmail(emails[index]) { user, _ ->
                if (user != null) {
                    resolved.add(user.uid)
                }
                resolve(index + 1)
            }
        }

        resolve(0)
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
