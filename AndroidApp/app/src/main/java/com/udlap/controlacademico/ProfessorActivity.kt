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
import com.google.firebase.auth.FirebaseAuth
import com.google.zxing.integration.android.IntentIntegrator
import com.google.zxing.integration.android.IntentResult
import com.udlap.controlacademico.data.FirestoreRepository
import com.udlap.controlacademico.model.GradeRecord
import com.udlap.controlacademico.model.Subject
import com.udlap.controlacademico.model.UserProfile

class ProfessorActivity : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth
    private val repository = FirestoreRepository()

    private lateinit var spSubjects: Spinner
    private lateinit var spStudents: Spinner
    private lateinit var tvSchedule: TextView
    private lateinit var tvLastAttendance: TextView
    private lateinit var etGrade: EditText

    private var subjects: List<Subject> = emptyList()
    private var studentsForSubject: List<UserProfile> = emptyList()

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

        spSubjects = findViewById(R.id.spSubjectsProfessor)
        spStudents = findViewById(R.id.spStudentsProfessor)
        tvSchedule = findViewById(R.id.tvProfessorSchedule)
        tvLastAttendance = findViewById(R.id.tvAttendanceResult)
        etGrade = findViewById(R.id.etGrade)

        val btnLoad = findViewById<Button>(R.id.btnLoadSubjectsProfessor)
        val btnSaveGrade = findViewById<Button>(R.id.btnSaveGrade)
        val btnScan = findViewById<Button>(R.id.btnScanQr)
        val btnBack = findViewById<Button>(R.id.btnBackFromProfessor)  // FIX: was never wired up

        btnBack.setOnClickListener { finish() }
        btnLoad.setOnClickListener { loadSubjects() }
        btnSaveGrade.setOnClickListener { saveGrade() }
        btnScan.setOnClickListener { scanQr() }

        spSubjects.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                refreshSelectedSubject()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        loadSubjects()
    }

    private fun loadSubjects() {
        val user = auth.currentUser ?: return
        repository.getSubjectsByProfessor(user.uid) { list, error ->
            if (error != null) {
                showToast(error)
                return@getSubjectsByProfessor
            }

            subjects = list
            if (subjects.isEmpty()) {
                tvSchedule.text = getString(R.string.msg_no_subjects)
                spSubjects.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, emptyList<String>())
                spStudents.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, emptyList<String>())
                return@getSubjectsByProfessor
            }

            val labels = subjects.map { it.nombre }
            spSubjects.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, labels)
            refreshSelectedSubject()
        }
    }

    private fun refreshSelectedSubject() {
        if (subjects.isEmpty()) return
        val selected = subjects[spSubjects.selectedItemPosition.coerceAtLeast(0)]
        tvSchedule.text = getString(R.string.subject_schedule, selected.horario)

        if (selected.alumnosUids.isEmpty()) {
            studentsForSubject = emptyList()
            spStudents.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, emptyList<String>())
            return
        }

        repository.getUsersByIds(selected.alumnosUids) { users, error ->
            if (error != null) {
                showToast(error)
                studentsForSubject = emptyList()
                spStudents.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, emptyList<String>())
                return@getUsersByIds
            }

            studentsForSubject = users
            val labels = users.map { "${it.nombre} • ${it.matricula} • ${it.correo}" }
            spStudents.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, labels)
        }
    }

    private fun saveGrade() {
        val user = auth.currentUser ?: return
        if (subjects.isEmpty()) {
            showToast(getString(R.string.msg_no_subject_selected))
            return
        }

        val grade = etGrade.text.toString().toDoubleOrNull()
        if (grade == null || grade < 0 || grade > 100) {
            showToast(getString(R.string.msg_grade_invalid))
            return
        }

        val subject = subjects[spSubjects.selectedItemPosition.coerceAtLeast(0)]
        val student = studentsForSubject.getOrNull(spStudents.selectedItemPosition)
        val studentUid = student?.uid.orEmpty()
        if (studentUid.isBlank()) {
            showToast(getString(R.string.msg_student_required))
            return
        }

        val record = GradeRecord(
            subjectId = subject.id,
            studentUid = studentUid,
            professorUid = user.uid,
            calificacion = grade,
            updatedAt = System.currentTimeMillis()
        )

        repository.saveGrade(record) { ok, error ->
            if (ok) {
                etGrade.text.clear()  // FIX: clear field after successful save to prevent accidental re-submit
                showToast(getString(R.string.msg_grade_saved))
            } else {
                showToast(error ?: getString(R.string.msg_grade_error))
            }
        }
    }

    private fun scanQr() {
        if (subjects.isEmpty()) {
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
        runCatching {
            val user = auth.currentUser ?: throw IllegalStateException(getString(R.string.msg_session_missing))

            val parts = content.trim().split('|', limit = 2)
            if (parts.size != 2) {
                throw IllegalStateException(getString(R.string.msg_qr_invalid))
            }

            val studentUid = parts[0].trim()
            val subjectId = parts[1].trim()
            if (studentUid.isBlank() || subjectId.isBlank()) {
                throw IllegalStateException(getString(R.string.msg_qr_invalid))
            }

            val selectedPosition = spSubjects.selectedItemPosition
            val selected = subjects.getOrNull(selectedPosition)
                ?: throw IllegalStateException(getString(R.string.msg_no_subject_selected))

            if (selected.profesorUid != user.uid) {
                throw IllegalStateException(getString(R.string.msg_subject_not_owned_by_professor))
            }

            if (subjectId != selected.id) {
                throw IllegalStateException(getString(R.string.msg_qr_subject_mismatch))
            }

            if (!selected.alumnosUids.contains(studentUid)) {
                throw IllegalStateException(getString(R.string.msg_student_not_enrolled))
            }

            repository.registerAttendanceFromQr(
                subjectId = subjectId,
                studentUid = studentUid,
                professorUid = user.uid
            ) { ok, error ->
                if (ok) {
                    val studentLabel = studentsForSubject.firstOrNull { it.uid == studentUid }?.nombre ?: studentUid
                    tvLastAttendance.text = getString(R.string.msg_attendance_saved, studentLabel)
                    showToast(getString(R.string.msg_attendance_saved_short))
                } else {
                    showToast(error ?: getString(R.string.msg_attendance_error))
                }
            }
        }.onFailure {
            showToast(it.message ?: getString(R.string.msg_attendance_error))
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
