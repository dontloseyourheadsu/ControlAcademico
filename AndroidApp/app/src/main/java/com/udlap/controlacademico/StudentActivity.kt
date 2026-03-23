package com.udlap.controlacademico

import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.journeyapps.barcodescanner.BarcodeEncoder
import com.udlap.controlacademico.data.FirestoreRepository
import com.udlap.controlacademico.model.GradeRecord
import com.udlap.controlacademico.model.Subject

class StudentActivity : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth
    private val repository = FirestoreRepository()

    private lateinit var spSubjects: Spinner
    private lateinit var tvSchedule: TextView
    private lateinit var tvGrades: TextView
    private lateinit var ivQr: ImageView

    private var subjects: List<Subject> = emptyList()
    private var subjectNameById: Map<String, String> = emptyMap()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_student)

        auth = FirebaseAuth.getInstance()

        spSubjects = findViewById(R.id.spSubjectsStudent)
        tvSchedule = findViewById(R.id.tvStudentSchedule)
        tvGrades = findViewById(R.id.tvStudentGrades)
        ivQr = findViewById(R.id.ivStudentQr)

        val btnLoad = findViewById<Button>(R.id.btnLoadStudentData)
        val btnGenerateQr = findViewById<Button>(R.id.btnGenerateQr)
        val btnBack = findViewById<Button>(R.id.btnBackFromStudent)

        // FIX: removed direct loadGrades() call here — loadSubjects() already calls it
        // in its callback once subjectNameById is populated, so calling it here would
        // fire before subjects are ready and show raw Firestore IDs instead of names.
        btnLoad.setOnClickListener { loadSubjects() }
        btnGenerateQr.setOnClickListener { generateQrForSelectedSubject() }
        btnBack.setOnClickListener { finish() }

        spSubjects.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                refreshSelectedSubjectInfo()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        loadSubjects()
    }

    private fun loadSubjects() {
        val user = auth.currentUser ?: return
        repository.getSubjectsByStudent(user.uid) { list, error ->
            if (error != null) {
                showToast(error)
                return@getSubjectsByStudent
            }

            subjects = list
            subjectNameById = subjects.associate { it.id to it.nombre }
            val labels = subjects.map { it.nombre }
            spSubjects.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, labels)
            refreshSelectedSubjectInfo()
            // Grades are loaded here, after subjectNameById is ready, so names resolve correctly
            loadGrades()
        }
    }

    private fun refreshSelectedSubjectInfo() {
        if (subjects.isEmpty()) {
            tvSchedule.text = getString(R.string.msg_no_subjects)
            ivQr.setImageBitmap(null)
            return
        }

        val selected = subjects[spSubjects.selectedItemPosition.coerceAtLeast(0)]
        tvSchedule.text = getString(R.string.subject_schedule, selected.horario)
    }

    private fun loadGrades() {
        val user = auth.currentUser ?: return
        repository.getGradesByStudent(user.uid) { records, error ->
            if (error != null) {
                showToast(error)
                return@getGradesByStudent
            }
            tvGrades.text = buildGradesText(records, subjectNameById)
        }
    }

    private fun buildGradesText(records: List<GradeRecord>, namesById: Map<String, String>): String {
        if (records.isEmpty()) {
            return getString(R.string.msg_no_grades)
        }

        return records.joinToString(separator = "\n") {
            val readableName = namesById[it.subjectId] ?: it.subjectId
            "Materia: $readableName • Calificación: ${it.calificacion}"
        }
    }

    private fun generateQrForSelectedSubject() {
        val user = auth.currentUser ?: return
        if (subjects.isEmpty()) {
            showToast(getString(R.string.msg_no_subject_selected))
            return
        }

        val subject = subjects[spSubjects.selectedItemPosition.coerceAtLeast(0)]
        val payload = "${user.uid}|${subject.id}"
        ivQr.setImageBitmap(createQr(payload))
    }

    private fun createQr(content: String): Bitmap {
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, 700, 700)
        return BarcodeEncoder().createBitmap(bitMatrix)
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
