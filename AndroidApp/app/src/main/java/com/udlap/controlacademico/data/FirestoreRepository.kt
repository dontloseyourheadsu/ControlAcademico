package com.udlap.controlacademico.data

import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.udlap.controlacademico.model.AttendanceRecord
import com.udlap.controlacademico.model.GradeRecord
import com.udlap.controlacademico.model.Subject
import com.udlap.controlacademico.model.UserProfile

/**
 * Central data source for Firestore operations used by the MVVM layer.
 *
 * This repository isolates persistence concerns from Activities and ViewModels,
 * exposing callback-based functions that return either mapped domain models
 * or user-facing error messages.
 *
 * @property db Shared Firestore client used to access all app collections.
 */
class FirestoreRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    /**
     * Converts low-level exceptions into messages the UI can show safely.
     *
     * @param throwable Original failure emitted by Firestore or transaction logic.
     * @return Human-readable error message in Spanish.
     */
    private fun mapError(throwable: Throwable?): String {
        if (throwable is IllegalStateException) {
            return throwable.message ?: "Validación de datos falló"
        }
        val firestoreCode = (throwable as? FirebaseFirestoreException)?.code
        if (firestoreCode == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
            return "Permiso denegado por reglas de Firestore. Verifica tus Security Rules."
        }
        return throwable?.localizedMessage ?: "Error inesperado de Firestore"
    }

    /**
     * Persists or replaces a user profile document at `users/{uid}`.
     *
     * @param profile Profile payload to store.
     * @param onDone Callback that receives success flag and optional error text.
     */
    fun saveUserProfile(profile: UserProfile, onDone: (Boolean, String?) -> Unit) {
        db.collection("users").document(profile.uid)
            .set(profile)
            .addOnSuccessListener { onDone(true, null) }
            .addOnFailureListener { onDone(false, mapError(it)) }
    }

    /**
     * Loads one user profile by Firebase Auth UID.
     *
     * @param uid User identifier used as Firestore document id.
     * @param onDone Callback with profile when found, or null plus optional error.
     */
    fun getUserProfile(uid: String, onDone: (UserProfile?, String?) -> Unit) {
        db.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                onDone(doc.toObject(UserProfile::class.java), null)
            }
            .addOnFailureListener { onDone(null, mapError(it)) }
    }

    /**
     * Fetches all user profiles for admin management screens.
     *
     * @param onDone Callback with list of users and optional error.
     */
    fun getAllUsers(onDone: (List<UserProfile>, String?) -> Unit) {
        db.collection("users").get()
            .addOnSuccessListener { snapshot ->
                onDone(snapshot.documents.mapNotNull { it.toObject(UserProfile::class.java) }, null)
            }
            .addOnFailureListener { onDone(emptyList(), mapError(it)) }
    }

    /**
     * Retrieves student profiles by id list, filtering unsupported roles.
     *
     * @param uids Candidate user ids, potentially with duplicates/blanks.
     * @param onDone Callback with resolved and sorted student list.
     */
    fun getUsersByIds(uids: List<String>, onDone: (List<UserProfile>, String?) -> Unit) {
        // Normalize ids so the repository does not execute redundant reads.
        val uniqueUids = uids.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        if (uniqueUids.isEmpty()) {
            onDone(emptyList(), null)
            return
        }

        val refs = uniqueUids.map { uid -> db.collection("users").document(uid) }
        val tasks = refs.map { it.get() }

        Tasks.whenAllSuccess<DocumentSnapshot>(tasks)
            .addOnSuccessListener { docs ->
                // Professor screen only needs enrolled students for grade/attendance actions.
                val users = docs
                    .mapNotNull { it.toObject(UserProfile::class.java) }
                    .filter { it.rol.trim().lowercase() in listOf("alumno", "student") }
                    .sortedBy { it.nombre }
                onDone(users, null)
            }
            .addOnFailureListener { onDone(emptyList(), mapError(it)) }
    }

    /**
     * Updates the role field for one user profile document.
     */
    fun updateUserRole(uid: String, role: String, onDone: (Boolean, String?) -> Unit) {
        db.collection("users").document(uid)
            .update("rol", role)
            .addOnSuccessListener { onDone(true, null) }
            .addOnFailureListener { onDone(false, mapError(it)) }
    }

    /**
     * Searches one user by email to resolve professor/student references.
     */
    fun getUserByEmail(email: String, onDone: (UserProfile?, String?) -> Unit) {
        db.collection("users")
            .whereEqualTo("correo", email)
            .limit(1)
            .get()
            .addOnSuccessListener { snapshot ->
                onDone(snapshot.documents.firstOrNull()?.toObject(UserProfile::class.java), null)
            }
            .addOnFailureListener { onDone(null, mapError(it)) }
    }

    /**
     * Creates a subject if it has no id, or overwrites an existing id.
     *
     * @param subject Subject payload from admin flow.
     * @param onDone Callback with operation result.
     */
    fun createSubject(subject: Subject, onDone: (Boolean, String?) -> Unit) {
        // Keep a stable id in-memory and in Firestore, regardless of caller input.
        val docRef = if (subject.id.isBlank()) db.collection("subjects").document() else db.collection("subjects").document(subject.id)
        val withId = subject.copy(id = docRef.id)
        docRef.set(withId)
            .addOnSuccessListener { onDone(true, null) }
            .addOnFailureListener { onDone(false, mapError(it)) }
    }

    /**
     * Finds a subject by unique tuple: name + professor id.
     */
    fun getSubjectByNameAndProfessor(nombre: String, profesorUid: String, onDone: (Subject?, String?) -> Unit) {
        db.collection("subjects")
            .whereEqualTo("nombre", nombre)
            .whereEqualTo("profesorUid", profesorUid)
            .limit(1)
            .get()
            .addOnSuccessListener { snapshot ->
                onDone(snapshot.documents.firstOrNull()?.toObject(Subject::class.java), null)
            }
            .addOnFailureListener { onDone(null, mapError(it)) }
    }

    /**
     * Updates mutable fields of an existing subject assignment.
     */
    fun updateSubjectAssignments(
        subjectId: String,
        horario: String,
        alumnosUids: List<String>,
        onDone: (Boolean, String?) -> Unit
    ) {
        db.collection("subjects").document(subjectId)
            .update(
                mapOf(
                    "horario" to horario,
                    "alumnosUids" to alumnosUids
                )
            )
            .addOnSuccessListener { onDone(true, null) }
            .addOnFailureListener { onDone(false, mapError(it)) }
    }

    /**
     * Fetches all subjects taught by a professor.
     */
    fun getSubjectsByProfessor(uid: String, onDone: (List<Subject>, String?) -> Unit) {
        db.collection("subjects").whereEqualTo("profesorUid", uid).get()
            .addOnSuccessListener { snapshot ->
                onDone(snapshot.documents.mapNotNull { it.toObject(Subject::class.java) }, null)
            }
            .addOnFailureListener { onDone(emptyList(), mapError(it)) }
    }

    /**
     * Fetches all subjects where a student is enrolled.
     */
    fun getSubjectsByStudent(uid: String, onDone: (List<Subject>, String?) -> Unit) {
        db.collection("subjects").whereArrayContains("alumnosUids", uid).get()
            .addOnSuccessListener { snapshot ->
                onDone(snapshot.documents.mapNotNull { it.toObject(Subject::class.java) }, null)
            }
            .addOnFailureListener { onDone(emptyList(), mapError(it)) }
    }

    /**
     * Writes one grade using composite id `subject_student` to keep one active record.
     */
    fun saveGrade(record: GradeRecord, onDone: (Boolean, String?) -> Unit) {
        val id = "${record.subjectId}_${record.studentUid}"
        db.collection("grades").document(id)
            .set(record.copy(id = id))
            .addOnSuccessListener { onDone(true, null) }
            .addOnFailureListener { onDone(false, mapError(it)) }
    }

    /**
     * Loads all grade records for a specific student.
     */
    fun getGradesByStudent(uid: String, onDone: (List<GradeRecord>, String?) -> Unit) {
        db.collection("grades").whereEqualTo("studentUid", uid).get()
            .addOnSuccessListener { snapshot ->
                onDone(snapshot.documents.mapNotNull { it.toObject(GradeRecord::class.java) }, null)
            }
            .addOnFailureListener { onDone(emptyList(), mapError(it)) }
    }

    /**
     * Stores one attendance record without additional validation.
     */
    fun saveAttendance(record: AttendanceRecord, onDone: (Boolean, String?) -> Unit) {
        db.collection("attendance").add(record)
            .addOnSuccessListener { onDone(true, null) }
            .addOnFailureListener { onDone(false, mapError(it)) }
    }

    /**
     * Registers attendance from QR payload with transactional integrity.
     *
     * The transaction validates subject existence, professor ownership, and
     * student enrollment before creating the attendance document.
     */
    fun registerAttendanceFromQr(
        subjectId: String,
        studentUid: String,
        professorUid: String,
        onDone: (Boolean, String?) -> Unit
    ) {
        if (subjectId.isBlank() || studentUid.isBlank() || professorUid.isBlank()) {
            onDone(false, "El QR está incompleto o el usuario no es válido")
            return
        }

        // These refs are created once so the transaction operates on stable document targets.
        val subjectRef = db.collection("subjects").document(subjectId)
        val attendanceRef = db.collection("attendance").document()

        db.runTransaction { transaction ->
            val subjectSnapshot = transaction.get(subjectRef)
            if (!subjectSnapshot.exists()) {
                throw IllegalStateException("La materia del QR no existe")
            }

            val subject = subjectSnapshot.toObject(Subject::class.java)
                ?: throw IllegalStateException("No se pudo leer la materia del QR")

            if (subject.profesorUid != professorUid) {
                throw IllegalStateException("La materia no está asignada a este profesor")
            }

            if (!subject.alumnosUids.contains(studentUid)) {
                throw IllegalStateException("El alumno no está inscrito en la materia")
            }

            // Timestamp is generated server-side in app time to support chronological UI messages.
            val attendance = AttendanceRecord(
                id = attendanceRef.id,
                subjectId = subjectId,
                studentUid = studentUid,
                professorUid = professorUid,
                timestamp = System.currentTimeMillis()
            )

            transaction.set(attendanceRef, attendance)
        }
            .addOnSuccessListener { onDone(true, null) }
            .addOnFailureListener { onDone(false, mapError(it)) }
    }
}
