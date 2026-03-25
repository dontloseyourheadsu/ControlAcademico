package com.udlap.controlacademico.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.udlap.controlacademico.data.FirestoreRepository
import com.udlap.controlacademico.model.Subject
import com.udlap.controlacademico.model.UserProfile

/**
 * UI status values consumed by [com.udlap.controlacademico.AdminActivity]
 * to enable/disable controls during admin operations.
 */
enum class AdminStatus {
    INITIAL,
    LOADING_USERS,
    USERS_EMPTY,
    USERS_READY,
    SUBMITTING_ROLE,
    SUBMITTING_SUBJECT
}

/**
 * Immutable screen state for Admin panel widgets.
 *
 * @property status Current interaction phase for loading and submissions.
 * @property userLabels Formatted labels shown in the users spinner.
 */
data class AdminViewState(
    val status: AdminStatus = AdminStatus.INITIAL,
    val userLabels: List<String> = emptyList()
)

/**
 * ViewModel for admin use cases: list users, assign roles, and create/update subjects.
 *
 * MVVM connection:
 * 1. Receives UI intents from `AdminActivity`.
 * 2. Calls [FirestoreRepository] for remote data changes.
 * 3. Publishes [state], [toastEvent], and [clearFormEvent] for UI rendering.
 */
class AdminViewModel(
    private val repository: FirestoreRepository = FirestoreRepository()
) : ViewModel() {

    /** Internal mutable screen state updated by repository callbacks. */
    private val _state = MutableLiveData(AdminViewState())

    /** Public immutable state observed by the Activity to render widgets. */
    val state: LiveData<AdminViewState> = _state

    /** One-shot text events for toast messages. */
    private val _toastEvent = MutableLiveData<Event<String>>()

    /** Stream consumed by UI for transient feedback. */
    val toastEvent: LiveData<Event<String>> = _toastEvent

    /** One-shot signal used by UI to clear subject input fields after success. */
    private val _clearFormEvent = MutableLiveData<Event<Unit>>()

    /** Public event channel to trigger form reset in the Activity. */
    val clearFormEvent: LiveData<Event<Unit>> = _clearFormEvent

    /** Cached users list aligned with spinner positions from the rendered state. */
    private var users: List<UserProfile> = emptyList()

    /** Monotonic token to ignore stale async responses from previous load requests. */
    private var usersRequestToken: Int = 0

    /**
     * Loads all users and emits spinner-ready labels for role management UI.
     */
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

    /**
     * Updates the role of the selected spinner user.
     *
     * @param selectedIndex Position selected in users spinner.
     * @param selectedRole Role selected in roles spinner.
     */
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

    /**
     * Creates a new subject or updates an existing one for the same professor/name pair.
     *
     * MVVM connection:
     * - Reads typed values from the Activity.
     * - Resolves users via repository queries.
     * - Emits UI state transitions and clear-form/toast events.
     */
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

    /**
     * Resolves student emails to Firestore user ids sequentially.
     *
     * Sequential calls keep logic simple and preserve input order.
     */
    private fun resolveStudentUids(emails: List<String>, onDone: (List<String>) -> Unit) {
        /**
         * Collector for resolved ids used to build a subject enrollment payload.
         */
        val resolved = mutableListOf<String>()

        /** Recursive worker that advances one email lookup per callback. */
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

    /**
     * Restores non-busy UI status after role/subject submission finishes.
     */
    private fun restoreStateAfterSubmit() {
        _state.value = _state.value?.copy(
            status = if (users.isEmpty()) AdminStatus.USERS_EMPTY else AdminStatus.USERS_READY,
            userLabels = users.map { "${it.nombre} (${it.correo}) - ${it.rol}" }
        )
    }
}
