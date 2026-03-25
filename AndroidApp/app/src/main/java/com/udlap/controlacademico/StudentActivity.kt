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

/**
 * Student panel for viewing enrolled subjects, schedule, grades, and generating attendance QR.
 *
 * UI <-> MVVM connection:
 * - Reads spinner selections and button intents.
 * - Delegates business logic to [StudentViewModel].
 * - Renders ViewModel state/events into TextViews, Spinner, and QR ImageView.
 */
class StudentActivity : AppCompatActivity() {
    /** Firebase auth instance used to read current student uid. */
    private lateinit var auth: FirebaseAuth

    /** ViewModel that loads subjects/grades and emits QR payload events. */
    private lateinit var viewModel: StudentViewModel

    /** Spinner listing student subjects for selection. */
    private lateinit var spSubjects: Spinner

    /** TextView showing schedule for selected subject. */
    private lateinit var tvSchedule: TextView

    /** TextView showing formatted grade summary text. */
    private lateinit var tvGrades: TextView

    /** ImageView where generated QR bitmap is rendered. */
    private lateinit var ivQr: ImageView

    /** Button to load or refresh student data. */
    private lateinit var btnLoad: Button

    /** Button to request QR payload generation for selected subject. */
    private lateinit var btnGenerateQr: Button

    /**
     * Binds widgets, subscribes observers, and kicks off initial subject load.
     */
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

    /**
     * Reads selected subject position and asks ViewModel to emit QR payload.
     */
    private fun generateQrForSelectedSubject() {
        viewModel.generateQrForSelectedSubject(auth.currentUser?.uid, spSubjects.selectedItemPosition)
    }

    /**
     * Writes subject labels to spinner adapter.
     */
    private fun setSubjectsAdapter(labels: List<String>) {
        spSubjects.adapter = buildSpinnerAdapter(labels)
    }

    /**
     * Creates a themed spinner adapter for subject labels.
     */
    private fun buildSpinnerAdapter(labels: List<String>): ArrayAdapter<String> {
        return ArrayAdapter(this, R.layout.spinner_item, labels).also {
            it.setDropDownViewResource(R.layout.spinner_dropdown_item)
        }
    }

    /**
     * Toggles control enabled states based on loading phase and subject availability.
     */
    private fun applyUiState(status: StudentStatus) {
        val busy = status == StudentStatus.LOADING_SUBJECTS || status == StudentStatus.LOADING_GRADES
        val hasSubjects = (spSubjects.adapter?.count ?: 0) > 0

        spSubjects.isEnabled = hasSubjects && !busy
        btnGenerateQr.isEnabled = hasSubjects && !busy
        btnLoad.isEnabled = !busy
    }

    /**
     * Observes state/events and writes rendered values into the UI.
     *
     * - State observer writes spinner, schedule text, grade text, and QR reset.
     * - Event observers show toast and render QR bitmap payload.
     */
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

    /**
     * Converts raw payload text into QR bitmap displayed to professor scanner.
     */
    private fun createQr(content: String): Bitmap {
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, 700, 700)
        return BarcodeEncoder().createBitmap(bitMatrix)
    }

    /**
     * Displays short user feedback messages.
     */
    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
