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
import androidx.lifecycle.ViewModelProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.journeyapps.barcodescanner.BarcodeEncoder
import com.udlap.controlacademico.viewmodel.StudentStatus
import com.udlap.controlacademico.viewmodel.StudentViewModel

class StudentActivity : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var viewModel: StudentViewModel

    private lateinit var spSubjects: Spinner
    private lateinit var tvSchedule: TextView
    private lateinit var tvGrades: TextView
    private lateinit var ivQr: ImageView
    private lateinit var btnLoad: Button
    private lateinit var btnGenerateQr: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_student)

        auth = FirebaseAuth.getInstance()
        viewModel = ViewModelProvider(this)[StudentViewModel::class.java]

        spSubjects = findViewById(R.id.spSubjectsStudent)
        tvSchedule = findViewById(R.id.tvStudentSchedule)
        tvGrades = findViewById(R.id.tvStudentGrades)
        ivQr = findViewById(R.id.ivStudentQr)

        btnLoad = findViewById(R.id.btnLoadStudentData)
        btnGenerateQr = findViewById(R.id.btnGenerateQr)
        val btnBack = findViewById<Button>(R.id.btnBackFromStudent)

        setSubjectsAdapter(emptyList())
        tvGrades.text = getString(R.string.msg_no_grades)
        applyUiState(StudentStatus.INITIAL)
        observeViewModel()

        btnLoad.setOnClickListener { viewModel.loadSubjects(auth.currentUser?.uid) }
        btnGenerateQr.setOnClickListener { generateQrForSelectedSubject() }
        btnBack.setOnClickListener { finish() }

        spSubjects.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                viewModel.onSubjectSelected(position)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        viewModel.loadSubjects(auth.currentUser?.uid)
    }

    private fun generateQrForSelectedSubject() {
        viewModel.generateQrForSelectedSubject(auth.currentUser?.uid, spSubjects.selectedItemPosition)
    }

    private fun setSubjectsAdapter(labels: List<String>) {
        spSubjects.adapter = buildSpinnerAdapter(labels)
    }

    private fun buildSpinnerAdapter(labels: List<String>): ArrayAdapter<String> {
        return ArrayAdapter(this, R.layout.spinner_item, labels).also {
            it.setDropDownViewResource(R.layout.spinner_dropdown_item)
        }
    }

    private fun applyUiState(status: StudentStatus) {
        val busy = status == StudentStatus.LOADING_SUBJECTS || status == StudentStatus.LOADING_GRADES
        val hasSubjects = (spSubjects.adapter?.count ?: 0) > 0

        spSubjects.isEnabled = hasSubjects && !busy
        btnGenerateQr.isEnabled = hasSubjects && !busy
        btnLoad.isEnabled = !busy
    }

    private fun observeViewModel() {
        viewModel.state.observe(this) { state ->
            setSubjectsAdapter(state.subjectLabels)

            tvSchedule.text = if (state.schedule.isBlank()) {
                getString(R.string.msg_no_subjects)
            } else {
                getString(R.string.subject_schedule, state.schedule)
            }

            tvGrades.text = if (state.gradesText.isBlank()) getString(R.string.msg_no_grades) else state.gradesText
            if (state.subjectLabels.isEmpty()) {
                ivQr.setImageBitmap(null)
            }
            applyUiState(state.status)
        }

        viewModel.toastEvent.observe(this) { event ->
            event.getContentIfNotHandled()?.let { showToast(it) }
        }

        viewModel.qrPayloadEvent.observe(this) { event ->
            event.getContentIfNotHandled()?.let { payload ->
                ivQr.setImageBitmap(createQr(payload))
            }
        }
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
