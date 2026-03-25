package com.udlap.controlacademico.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.udlap.controlacademico.data.FirestoreRepository
import com.udlap.controlacademico.model.GradeRecord
import com.udlap.controlacademico.model.Subject

enum class StudentStatus {
    INITIAL,
    LOADING_SUBJECTS,
    SUBJECTS_EMPTY,
    SUBJECTS_READY,
    LOADING_GRADES,
    READY
}

data class StudentViewState(
    val status: StudentStatus = StudentStatus.INITIAL,
    val subjectLabels: List<String> = emptyList(),
    val schedule: String = "",
    val gradesText: String = ""
)

class StudentViewModel(
    private val repository: FirestoreRepository = FirestoreRepository()
) : ViewModel() {

    private val _state = MutableLiveData(StudentViewState())
    val state: LiveData<StudentViewState> = _state

    private val _toastEvent = MutableLiveData<Event<String>>()
    val toastEvent: LiveData<Event<String>> = _toastEvent

    private val _qrPayloadEvent = MutableLiveData<Event<String>>()
    val qrPayloadEvent: LiveData<Event<String>> = _qrPayloadEvent

    private var subjects: List<Subject> = emptyList()
    private var subjectNameById: Map<String, String> = emptyMap()
    private var subjectsRequestToken: Int = 0
    private var gradesRequestToken: Int = 0

    fun loadSubjects(studentUid: String?) {
        if (studentUid.isNullOrBlank()) {
            _toastEvent.value = Event("No hay sesión activa")
            return
        }

        val requestToken = ++subjectsRequestToken
        subjects = emptyList()
        subjectNameById = emptyMap()
        _state.value = StudentViewState(
            status = StudentStatus.LOADING_SUBJECTS,
            gradesText = "No hay calificaciones disponibles"
        )

        repository.getSubjectsByStudent(studentUid) { list, error ->
            if (requestToken != subjectsRequestToken) return@getSubjectsByStudent

            if (!error.isNullOrBlank()) {
                _state.value = StudentViewState(status = StudentStatus.SUBJECTS_EMPTY)
                _toastEvent.value = Event(error)
                return@getSubjectsByStudent
            }

            subjects = list
            subjectNameById = subjects.associate { it.id to it.nombre }

            if (subjects.isEmpty()) {
                _state.value = StudentViewState(status = StudentStatus.SUBJECTS_EMPTY)
                return@getSubjectsByStudent
            }

            val first = subjects.first()
            _state.value = StudentViewState(
                status = StudentStatus.SUBJECTS_READY,
                subjectLabels = subjects.map { it.nombre },
                schedule = first.horario,
                gradesText = "No hay calificaciones disponibles"
            )
            loadGrades(studentUid)
        }
    }

    fun onSubjectSelected(position: Int) {
        val selected = subjects.getOrNull(position) ?: return
        _state.value = _state.value?.copy(schedule = selected.horario)
    }

    fun loadGrades(studentUid: String?) {
        if (studentUid.isNullOrBlank()) return

        val requestToken = ++gradesRequestToken
        _state.value = _state.value?.copy(status = StudentStatus.LOADING_GRADES)

        repository.getGradesByStudent(studentUid) { records, error ->
            if (requestToken != gradesRequestToken) return@getGradesByStudent

            if (!error.isNullOrBlank()) {
                _state.value = _state.value?.copy(status = StudentStatus.SUBJECTS_READY)
                _toastEvent.value = Event(error)
                return@getGradesByStudent
            }

            _state.value = _state.value?.copy(
                status = StudentStatus.READY,
                gradesText = buildGradesText(records, subjectNameById)
            )
        }
    }

    fun generateQrForSelectedSubject(studentUid: String?, selectedPosition: Int) {
        if (studentUid.isNullOrBlank()) {
            _toastEvent.value = Event("No hay sesión activa")
            return
        }

        val subject = subjects.getOrNull(selectedPosition)
        if (subject == null) {
            _toastEvent.value = Event("No hay materia seleccionada. Primero crea/asigna una materia")
            return
        }

        _qrPayloadEvent.value = Event("${studentUid}|${subject.id}")
    }

    private fun buildGradesText(records: List<GradeRecord>, namesById: Map<String, String>): String {
        if (records.isEmpty()) {
            return "No hay calificaciones disponibles"
        }

        return records.joinToString(separator = "\n") {
            val readableName = namesById[it.subjectId] ?: it.subjectId
            "Materia: $readableName • Calificación: ${it.calificacion}"
        }
    }
}
