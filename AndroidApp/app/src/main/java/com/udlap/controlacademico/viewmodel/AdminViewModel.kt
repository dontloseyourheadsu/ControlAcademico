package com.udlap.controlacademico.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.udlap.controlacademico.data.FirestoreRepository
import com.udlap.controlacademico.model.Subject
import com.udlap.controlacademico.model.UserProfile

enum class AdminStatus {
    INITIAL,
    LOADING_USERS,
    USERS_EMPTY,
    USERS_READY,
    SUBMITTING_ROLE,
    SUBMITTING_SUBJECT
}

data class AdminViewState(
    val status: AdminStatus = AdminStatus.INITIAL,
    val userLabels: List<String> = emptyList()
)

class AdminViewModel(
    private val repository: FirestoreRepository = FirestoreRepository()
) : ViewModel() {

    private val _state = MutableLiveData(AdminViewState())
    val state: LiveData<AdminViewState> = _state

    private val _toastEvent = MutableLiveData<Event<String>>()
    val toastEvent: LiveData<Event<String>> = _toastEvent

    private val _clearFormEvent = MutableLiveData<Event<Unit>>()
    val clearFormEvent: LiveData<Event<Unit>> = _clearFormEvent

    private var users: List<UserProfile> = emptyList()
    private var usersRequestToken: Int = 0

    fun loadUsers() {
        val requestToken = ++usersRequestToken
        users = emptyList()
        _state.value = AdminViewState(status = AdminStatus.LOADING_USERS, userLabels = emptyList())

        repository.getAllUsers { allUsers, error ->
            if (requestToken != usersRequestToken) return@getAllUsers

            if (!error.isNullOrBlank()) {
                _state.value = AdminViewState(status = AdminStatus.USERS_EMPTY, userLabels = emptyList())
                _toastEvent.value = Event(error)
                return@getAllUsers
            }

            users = allUsers.sortedBy { it.correo }
            if (users.isEmpty()) {
                _state.value = AdminViewState(status = AdminStatus.USERS_EMPTY, userLabels = emptyList())
                return@getAllUsers
            }

            _state.value = AdminViewState(
                status = AdminStatus.USERS_READY,
                userLabels = users.map { "${it.nombre} (${it.correo}) - ${it.rol}" }
            )
        }
    }

    fun updateRoleForSelectedUser(selectedIndex: Int, selectedRole: String) {
        if (selectedIndex !in users.indices) {
            _toastEvent.value = Event("Selecciona un usuario")
            return
        }

        val role = if (selectedRole == "profesor") "profesor" else "alumno"
        _state.value = _state.value?.copy(status = AdminStatus.SUBMITTING_ROLE)

        repository.updateUserRole(users[selectedIndex].uid, role) { ok, error ->
            if (ok) {
                _toastEvent.value = Event("Rol actualizado correctamente")
                loadUsers()
            } else {
                restoreStateAfterSubmit()
                _toastEvent.value = Event(error ?: "No se pudo actualizar el rol")
            }
        }
    }

    fun createOrUpdateSubject(
        nombre: String,
        horario: String,
        profesorEmail: String,
        alumnosEmails: List<String>
    ) {
        if (nombre.isBlank() || horario.isBlank() || profesorEmail.isBlank() || alumnosEmails.isEmpty()) {
            _toastEvent.value = Event("Completa todos los datos de la materia")
            return
        }

        _state.value = _state.value?.copy(status = AdminStatus.SUBMITTING_SUBJECT)

        repository.getUserByEmail(profesorEmail) { profesor, error ->
            if (!error.isNullOrBlank()) {
                restoreStateAfterSubmit()
                _toastEvent.value = Event(error)
                return@getUserByEmail
            }

            if (profesor == null) {
                restoreStateAfterSubmit()
                _toastEvent.value = Event("No se encontró profesor con ese correo")
                return@getUserByEmail
            }

            resolveStudentUids(alumnosEmails) { uids ->
                if (uids.isEmpty()) {
                    restoreStateAfterSubmit()
                    _toastEvent.value = Event("No se encontraron alumnos válidos")
                    return@resolveStudentUids
                }

                repository.getSubjectByNameAndProfessor(nombre, profesor.uid) { existing, findError ->
                    if (!findError.isNullOrBlank()) {
                        restoreStateAfterSubmit()
                        _toastEvent.value = Event(findError)
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
                            restoreStateAfterSubmit()
                            if (ok) {
                                _clearFormEvent.value = Event(Unit)
                                _toastEvent.value = Event("Materia creada correctamente")
                            } else {
                                _toastEvent.value = Event(createError ?: "No se pudo crear la materia")
                            }
                        }
                    } else {
                        repository.updateSubjectAssignments(existing.id, horario, uids) { ok, updateError ->
                            restoreStateAfterSubmit()
                            if (ok) {
                                _clearFormEvent.value = Event(Unit)
                                _toastEvent.value = Event("Materia actualizada (inscritos/horario)")
                            } else {
                                _toastEvent.value = Event(updateError ?: "No se pudo actualizar la materia")
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

    private fun restoreStateAfterSubmit() {
        _state.value = _state.value?.copy(
            status = if (users.isEmpty()) AdminStatus.USERS_EMPTY else AdminStatus.USERS_READY,
            userLabels = users.map { "${it.nombre} (${it.correo}) - ${it.rol}" }
        )
    }
}
