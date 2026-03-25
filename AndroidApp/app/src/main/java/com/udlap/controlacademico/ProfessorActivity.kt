package com.udlap.controlacademico

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.zxing.integration.android.IntentIntegrator
import com.google.zxing.integration.android.IntentResult
import com.udlap.controlacademico.viewmodel.ProfessorStatus
import com.udlap.controlacademico.viewmodel.ProfessorViewModel

/**
 * Professor panel for viewing assigned subjects, grading students, and scanning attendance QR.
 *
 * UI <-> MVVM connection:
 * - Reads spinner/input values and forwards intents to [ProfessorViewModel].
 * - Observes state/events to render subject/student lists, schedule, and feedback.
 */
class ProfessorActivity : AppCompatActivity() {
    /** Current Firebase session provider used to obtain professor uid. */
    private lateinit var auth: FirebaseAuth

    /** ViewModel that owns professor business rules and repository interactions. */
    private lateinit var viewModel: ProfessorViewModel

    /** Spinner that lists subjects assigned to current professor. */
    private lateinit var spSubjects: Spinner

    /** Spinner that lists students for the selected subject. */
    private lateinit var spStudents: Spinner

    /** TextView that renders selected subject schedule. */
    private lateinit var tvSchedule: TextView

    /** TextView that renders last successful attendance registration message. */
    private lateinit var tvLastAttendance: TextView

    /** Input where professor types numeric grade value. */
    private lateinit var etGrade: EditText

    /** Button to reload subjects from backend. */
    private lateinit var btnLoad: Button

    /** Button to submit grade for selected student. */
    private lateinit var btnSaveGrade: Button

    /** Button to start QR scan flow for attendance registration. */
    private lateinit var btnScan: Button

    /** Cache of rendered subject labels to avoid unnecessary adapter resets. */
    private var renderedSubjectLabels: List<String> = emptyList()

    /** Cache of rendered student labels to avoid unnecessary adapter resets. */
    private var renderedStudentLabels: List<String> = emptyList()

    /**
     * Guard flag used to prevent spinner callback loops when selection is changed
     * programmatically during state rendering.
     */
    private var isProgrammaticSubjectSelection: Boolean = false

    /**
     * Permission launcher that bridges Android permission result to scan flow.
     */
    private val cameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                startQrScanner()
            } else {
                showToast(getString(R.string.msg_camera_permission_required))
            }
        }

    /**
     * Binds UI controls, subscribes observers, and triggers initial subject load.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_professor)

        auth = FirebaseAuth.getInstance()
        viewModel = ViewModelProvider(this)[ProfessorViewModel::class.java]

        spSubjects = findViewById(R.id.spSubjectsProfessor)
        spStudents = findViewById(R.id.spStudentsProfessor)
        tvSchedule = findViewById(R.id.tvProfessorSchedule)
        tvLastAttendance = findViewById(R.id.tvAttendanceResult)
        etGrade = findViewById(R.id.etGrade)

        btnLoad = findViewById(R.id.btnLoadSubjectsProfessor)
        btnSaveGrade = findViewById(R.id.btnSaveGrade)
        btnScan = findViewById(R.id.btnScanQr)
        val btnBack = findViewById<Button>(R.id.btnBackFromProfessor)  // FIX: was never wired up

        setSubjectsAdapter(emptyList())
        setStudentsAdapter(emptyList())
        applyUiState(ProfessorStatus.INITIAL)
        observeViewModel()

        btnBack.setOnClickListener { finish() }
        btnLoad.setOnClickListener { viewModel.loadSubjects(auth.currentUser?.uid) }
        btnSaveGrade.setOnClickListener { saveGrade() }
        btnScan.setOnClickListener { scanQr() }

        spSubjects.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (isProgrammaticSubjectSelection) return
                viewModel.refreshSelectedSubject(position)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        viewModel.loadSubjects(auth.currentUser?.uid)
    }

    /**
     * Reads selected subject/student and typed grade, then sends save intent to ViewModel.
     */
    private fun saveGrade() {
        val grade = etGrade.text.toString().toDoubleOrNull()
        viewModel.saveGrade(
            selectedSubjectPosition = spSubjects.selectedItemPosition,
            selectedStudentPosition = spStudents.selectedItemPosition,
            professorUid = auth.currentUser?.uid,
            grade = grade
        )
    }

    /**
     * Starts attendance scan flow after checking subject availability and camera permission.
     */
    private fun scanQr() {
        if ((spSubjects.adapter?.count ?: 0) == 0) {
            showToast(getString(R.string.msg_no_subject_selected))
            return
        }

        val isGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        if (isGranted) {
            startQrScanner()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    /**
     * Launches ZXing scanner UI configured for QR codes only.
     */
    private fun startQrScanner() {
        IntentIntegrator(this)
            .setDesiredBarcodeFormats(IntentIntegrator.QR_CODE)
            .setPrompt(getString(R.string.scan_prompt))
            .setBeepEnabled(true)
            .setOrientationLocked(false)
            .initiateScan()
    }

    /**
     * Receives scanner result and forwards QR content for domain validation.
     */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        val result: IntentResult? = IntentIntegrator.parseActivityResult(requestCode, resultCode, data)
        if (result != null) {
            if (result.contents == null) {
                showToast(getString(R.string.msg_scan_cancelled))
            } else {
                processAttendanceQr(result.contents)
            }
            return
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    /**
     * Sends scanned QR payload and current subject context to ViewModel.
     */
    private fun processAttendanceQr(content: String) {
        viewModel.registerAttendanceFromQr(
            qrContent = content,
            selectedSubjectPosition = spSubjects.selectedItemPosition,
            professorUid = auth.currentUser?.uid
        )
    }

    /**
     * Writes subject labels into the subject spinner adapter.
     */
    private fun setSubjectsAdapter(labels: List<String>) {
        spSubjects.adapter = buildSpinnerAdapter(labels)
    }

    /**
     * Writes student labels into the student spinner adapter.
     */
    private fun setStudentsAdapter(labels: List<String>) {
        spStudents.adapter = buildSpinnerAdapter(labels)
    }

    /**
     * Creates a themed spinner adapter shared across subject/student spinners.
     */
    private fun buildSpinnerAdapter(labels: List<String>): ArrayAdapter<String> {
        return ArrayAdapter(this, R.layout.spinner_item, labels).also {
            it.setDropDownViewResource(R.layout.spinner_dropdown_item)
        }
    }

    /**
     * Enables/disables controls based on loading/submission status and available data.
     */
    private fun applyUiState(status: ProfessorStatus) {
        val busy = status == ProfessorStatus.LOADING_SUBJECTS ||
            status == ProfessorStatus.LOADING_STUDENTS ||
            status == ProfessorStatus.SAVING_GRADE ||
            status == ProfessorStatus.REGISTERING_ATTENDANCE

        val hasSubjects = (spSubjects.adapter?.count ?: 0) > 0
        val hasStudents = (spStudents.adapter?.count ?: 0) > 0

        spSubjects.isEnabled = hasSubjects && !busy
        spStudents.isEnabled = hasStudents && !busy
        etGrade.isEnabled = hasSubjects && hasStudents && !busy
        btnLoad.isEnabled = !busy
        btnSaveGrade.isEnabled = hasSubjects && hasStudents && !busy
        btnScan.isEnabled = hasSubjects && !busy
    }

    /**
     * Observes ViewModel state/events and writes all visible UI outputs.
     *
     * Writes adapters, TextViews, input clearing, and enabled states.
     */
    private fun observeViewModel() {
        viewModel.state.observe(this) { state ->
            if (state.subjectLabels != renderedSubjectLabels) {
                val currentSelection = spSubjects.selectedItemPosition.coerceAtLeast(0)
                renderedSubjectLabels = state.subjectLabels
                isProgrammaticSubjectSelection = true
                setSubjectsAdapter(state.subjectLabels)
                if (state.subjectLabels.isNotEmpty()) {
                    val restoredSelection = currentSelection.coerceAtMost(state.subjectLabels.lastIndex)
                    spSubjects.setSelection(restoredSelection, false)
                }
                isProgrammaticSubjectSelection = false
            }

            if (state.studentLabels != renderedStudentLabels) {
                renderedStudentLabels = state.studentLabels
                setStudentsAdapter(state.studentLabels)
            }

            tvSchedule.text = if (state.schedule.isBlank()) {
                getString(R.string.msg_no_subjects)
            } else {
                getString(R.string.subject_schedule, state.schedule)
            }

            if (state.lastAttendanceMessage.isNotBlank()) {
                tvLastAttendance.text = state.lastAttendanceMessage
            }

            applyUiState(state.status)
            if (state.status == ProfessorStatus.STUDENTS_READY) {
                etGrade.text.clear()
            }
        }

        viewModel.toastEvent.observe(this) { event ->
            event.getContentIfNotHandled()?.let { showToast(it) }
        }
    }

    /**
     * Displays toast feedback for scan/grade/load outcomes.
     */
    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
