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

class ProfessorActivity : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var viewModel: ProfessorViewModel

    private lateinit var spSubjects: Spinner
    private lateinit var spStudents: Spinner
    private lateinit var tvSchedule: TextView
    private lateinit var tvLastAttendance: TextView
    private lateinit var etGrade: EditText
    private lateinit var btnLoad: Button
    private lateinit var btnSaveGrade: Button
    private lateinit var btnScan: Button

    private val cameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                startQrScanner()
            } else {
                showToast(getString(R.string.msg_camera_permission_required))
            }
        }

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
                viewModel.refreshSelectedSubject(position)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        viewModel.loadSubjects(auth.currentUser?.uid)
    }

    private fun saveGrade() {
        val grade = etGrade.text.toString().toDoubleOrNull()
        viewModel.saveGrade(
            selectedSubjectPosition = spSubjects.selectedItemPosition,
            selectedStudentPosition = spStudents.selectedItemPosition,
            professorUid = auth.currentUser?.uid,
            grade = grade
        )
    }

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

    private fun startQrScanner() {
        IntentIntegrator(this)
            .setDesiredBarcodeFormats(IntentIntegrator.QR_CODE)
            .setPrompt(getString(R.string.scan_prompt))
            .setBeepEnabled(true)
            .setOrientationLocked(false)
            .initiateScan()
    }

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

    private fun processAttendanceQr(content: String) {
        viewModel.registerAttendanceFromQr(
            qrContent = content,
            selectedSubjectPosition = spSubjects.selectedItemPosition,
            professorUid = auth.currentUser?.uid
        )
    }

    private fun setSubjectsAdapter(labels: List<String>) {
        spSubjects.adapter = buildSpinnerAdapter(labels)
    }

    private fun setStudentsAdapter(labels: List<String>) {
        spStudents.adapter = buildSpinnerAdapter(labels)
    }

    private fun buildSpinnerAdapter(labels: List<String>): ArrayAdapter<String> {
        return ArrayAdapter(this, R.layout.spinner_item, labels).also {
            it.setDropDownViewResource(R.layout.spinner_dropdown_item)
        }
    }

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

    private fun observeViewModel() {
        viewModel.state.observe(this) { state ->
            setSubjectsAdapter(state.subjectLabels)
            setStudentsAdapter(state.studentLabels)

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

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
