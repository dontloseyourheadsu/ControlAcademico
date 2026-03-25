package com.udlap.controlacademico.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.udlap.controlacademico.data.FirestoreRepository
import com.udlap.controlacademico.model.AttendanceRecord
import com.udlap.controlacademico.model.GradeRecord
import com.udlap.controlacademico.model.Subject
import com.udlap.controlacademico.model.UserProfile

enum class ProfessorStatus {
    INITIAL,
    LOADING_SUBJECTS,
    SUBJECTS_EMPTY,
    SUBJECTS_READY,
    LOADING_STUDENTS,
    STUDENTS_READY,
    SAVING_GRADE,
    REGISTERING_ATTENDANCE
}

data class ProfessorViewState(
    val status: ProfessorStatus = ProfessorStatus.INITIAL,
    val subjectLabels: List<String> = emptyList(),
    val studentLabels: List<String> = emptyList(),
    val schedule: String = "",
    val lastAttendanceMessage: String = ""
)

class ProfessorViewModel(
    private val repository: FirestoreRepository = FirestoreRepository()
) : ViewModel() {

    private val _state = MutableLiveData(ProfessorViewState())
    val state: LiveData<ProfessorViewState> = _state

    private val _toastEvent = MutableLiveData<Event<String>>()
    val toastEvent: LiveData<Event<String>> = _toastEvent

    private var subjects: List<Subject> = emptyList()
    private var studentsForSubject: List<UserProfile> = emptyList()
    private var subjectsRequestToken: Int = 0
    private var studentsRequestToken: Int = 0

    fun loadSubjects(professorUid: String?) {
        if (professorUid.isNullOrBlank()) {
            _toastEvent.value = Event("No hay sesión activa")
            return
        }

        val requestToken = ++subjectsRequestToken
        subjects = emptyList()
        studentsForSubject = emptyList()
        _state.value = ProfessorViewState(status = ProfessorStatus.LOADING_SUBJECTS)

        repository.getSubjectsByProfessor(professorUid) { list, error ->
            if (requestToken != subjectsRequestToken) return@getSubjectsByProfessor

            if (!error.isNullOrBlank()) {
                _state.value = ProfessorViewState(status = ProfessorStatus.SUBJECTS_EMPTY)
                _toastEvent.value = Event(error)
                return@getSubjectsByProfessor
            }

            subjects = list
            if (subjects.isEmpty()) {
                _state.value = ProfessorViewState(status = ProfessorStatus.SUBJECTS_EMPTY)
                return@getSubjectsByProfessor
            }

            _state.value = ProfessorViewState(
                status = ProfessorStatus.SUBJECTS_READY,
                subjectLabels = subjects.map { it.nombre }
            )
            refreshSelectedSubject(0)
        }
    }

    fun refreshSelectedSubject(position: Int) {
        val selected = subjects.getOrNull(position) ?: return

        if (selected.alumnosUids.isEmpty()) {
            studentsForSubject = emptyList()
            _state.value = _state.value?.copy(
                status = ProfessorStatus.SUBJECTS_READY,
                studentLabels = emptyList(),
                schedule = selected.horario
            )
            return
        }

        val requestToken = ++studentsRequestToken
        studentsForSubject = emptyList()
        _state.value = _state.value?.copy(
            status = ProfessorStatus.LOADING_STUDENTS,
            studentLabels = emptyList(),
            schedule = selected.horario
        )

        repository.getUsersByIds(selected.alumnosUids) { users, error ->
            if (requestToken != studentsRequestToken) return@getUsersByIds

            if (!error.isNullOrBlank()) {
                studentsForSubject = emptyList()
                _state.value = _state.value?.copy(
                    status = ProfessorStatus.SUBJECTS_READY,
                    studentLabels = emptyList()
                )
                _toastEvent.value = Event(error)
                return@getUsersByIds
            }

            studentsForSubject = users
            _state.value = _state.value?.copy(
                status = if (users.isEmpty()) ProfessorStatus.SUBJECTS_READY else ProfessorStatus.STUDENTS_READY,
                studentLabels = users.map { "${it.nombre} • ${it.matricula} • ${it.correo}" }
            )
        }
    }

    fun saveGrade(
        selectedSubjectPosition: Int,
        selectedStudentPosition: Int,
        professorUid: String?,
        grade: Double?
    ) {
        if (professorUid.isNullOrBlank()) {
            _toastEvent.value = Event("No hay sesión activa")
            return
        }

        if (grade == null || grade < 0 || grade > 100) {
            _toastEvent.value = Event("La calificación debe ser numérica entre 0 y 100")
            return
        }

        val subject = subjects.getOrNull(selectedSubjectPosition)
        val student = studentsForSubject.getOrNull(selectedStudentPosition)

        if (subject == null) {
            _toastEvent.value = Event("No hay materia seleccionada. Primero crea/asigna una materia")
            return
        }

        if (student == null) {
            _toastEvent.value = Event("Selecciona un alumno")
            return
        }

        _state.value = _state.value?.copy(status = ProfessorStatus.SAVING_GRADE)

        val record = GradeRecord(
            subjectId = subject.id,
            studentUid = student.uid,
            professorUid = professorUid,
            calificacion = grade,
            updatedAt = System.currentTimeMillis()
        )

        repository.saveGrade(record) { ok, error ->
            restoreReadyStatus()
            if (ok) {
                _toastEvent.value = Event("Calificación guardada")
            } else {
                _toastEvent.value = Event(error ?: "No se pudo guardar la calificación")
            }
        }
    }

    fun registerAttendanceFromQr(
        qrContent: String,
        selectedSubjectPosition: Int,
        professorUid: String?
    ) {
        if (professorUid.isNullOrBlank()) {
            _toastEvent.value = Event("No hay sesión activa")
            return
        }

        val selected = subjects.getOrNull(selectedSubjectPosition)
        if (selected == null) {
            _toastEvent.value = Event("No hay materia seleccionada. Primero crea/asigna una materia")
            return
        }

        val parts = qrContent.trim().split('|', limit = 2)
        if (parts.size != 2) {
            _toastEvent.value = Event("QR inválido")
            return
        }

        val studentUid = parts[0].trim()
        val subjectId = parts[1].trim()
        if (studentUid.isBlank() || subjectId.isBlank()) {
            _toastEvent.value = Event("QR inválido")
            return
        }

        if (selected.profesorUid != professorUid) {
            _toastEvent.value = Event("No puedes tomar asistencia de una materia que no te pertenece")
            return
        }

        if (subjectId != selected.id) {
            _toastEvent.value = Event("QR no corresponde a la materia seleccionada")
            return
        }

        if (!selected.alumnosUids.contains(studentUid)) {
            _toastEvent.value = Event("El alumno no está inscrito en esta materia")
            return
        }

        _state.value = _state.value?.copy(status = ProfessorStatus.REGISTERING_ATTENDANCE)
        repository.registerAttendanceFromQr(
            subjectId = subjectId,
            studentUid = studentUid,
            professorUid = professorUid
        ) { ok, error ->
            restoreReadyStatus()
            if (ok) {
                val studentLabel = studentsForSubject.firstOrNull { it.uid == studentUid }?.nombre ?: studentUid
                _state.value = _state.value?.copy(lastAttendanceMessage = "Asistencia registrada para: $studentLabel")
                _toastEvent.value = Event("Asistencia registrada")
            } else {
                _toastEvent.value = Event(error ?: "No se pudo registrar asistencia")
            }
        }
    }

    private fun restoreReadyStatus() {
        _state.value = _state.value?.copy(
            status = if (studentsForSubject.isEmpty()) ProfessorStatus.SUBJECTS_READY else ProfessorStatus.STUDENTS_READY
        )
    }
}
