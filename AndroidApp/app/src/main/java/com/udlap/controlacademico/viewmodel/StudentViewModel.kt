package com.udlap.controlacademico.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.udlap.controlacademico.data.FirestoreRepository
import com.udlap.controlacademico.model.GradeRecord
import com.udlap.controlacademico.model.Subject

/**
 * UI statuses for student panel data loading and display stages.
 */
enum class StudentStatus {
    INITIAL,
    LOADING_SUBJECTS,
    SUBJECTS_EMPTY,
    SUBJECTS_READY,
    LOADING_GRADES,
    READY
}

/**
 * Immutable render state consumed by StudentActivity.
 *
 * @property status Current loading/ready phase.
 * @property subjectLabels Subject names displayed in spinner.
 * @property schedule Schedule text for selected subject.
 * @property gradesText Multi-line summary of grades to render in TextView.
 */
data class StudentViewState(
    val status: StudentStatus = StudentStatus.INITIAL,
    val subjectLabels: List<String> = emptyList(),
    val schedule: String = "",
    val gradesText: String = ""
)

/**
 * ViewModel for student experience: subjects, grades, and attendance QR payload.
 *
 * MVVM connection:
 * - Activity sends user intents (load/select/generate QR).
 * - ViewModel requests data from [FirestoreRepository].
 * - ViewModel emits UI state, toast events, and QR payload events.
 */
class StudentViewModel(
    private val repository: FirestoreRepository = FirestoreRepository()
) : ViewModel() {

    /** Mutable state backing field for screen rendering. */
    private val _state = MutableLiveData(StudentViewState())

    /** Public immutable state stream observed by StudentActivity. */
    val state: LiveData<StudentViewState> = _state

    /** One-time channel for transient user feedback messages. */
    private val _toastEvent = MutableLiveData<Event<String>>()

    /** Public toast event stream consumed by Activity. */
    val toastEvent: LiveData<Event<String>> = _toastEvent

    /** One-time channel carrying QR text payload to be rendered as bitmap in UI. */
    private val _qrPayloadEvent = MutableLiveData<Event<String>>()

    /** Public QR payload event stream observed by Activity. */
    val qrPayloadEvent: LiveData<Event<String>> = _qrPayloadEvent

    /** Cached subject models aligned with spinner indexes. */
    private var subjects: List<Subject> = emptyList()

    /** Lookup map used to display readable subject names in grade lines. */
    private var subjectNameById: Map<String, String> = emptyMap()

    /** Token that ignores stale subject-loading responses. */
    private var subjectsRequestToken: Int = 0

    /** Token that ignores stale grade-loading responses. */
    private var gradesRequestToken: Int = 0

    /**
     * Loads subjects where the student is enrolled, then triggers grade loading.
     */
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

    /**
     * Handles subject spinner selection by updating schedule text in state.
     */
    fun onSubjectSelected(position: Int) {
        val selected = subjects.getOrNull(position) ?: return
        _state.value = _state.value?.copy(schedule = selected.horario)
    }

    /**
     * Loads all grade records for current student and formats them for UI.
     */
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

    /**
     * Emits QR payload combining student id and selected subject id.
     */
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

    /**
     * Produces human-readable grade rows for the student TextView.
     */
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
