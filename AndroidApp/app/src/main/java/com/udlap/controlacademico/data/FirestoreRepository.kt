package com.udlap.controlacademico.data

import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.udlap.controlacademico.model.AttendanceRecord
import com.udlap.controlacademico.model.GradeRecord
import com.udlap.controlacademico.model.Subject
import com.udlap.controlacademico.model.UserProfile

class FirestoreRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
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

    fun saveUserProfile(profile: UserProfile, onDone: (Boolean, String?) -> Unit) {
        db.collection("users").document(profile.uid)
            .set(profile)
            .addOnSuccessListener { onDone(true, null) }
            .addOnFailureListener { onDone(false, mapError(it)) }
    }

    fun getUserProfile(uid: String, onDone: (UserProfile?, String?) -> Unit) {
        db.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                onDone(doc.toObject(UserProfile::class.java), null)
            }
            .addOnFailureListener { onDone(null, mapError(it)) }
    }

    fun getAllUsers(onDone: (List<UserProfile>, String?) -> Unit) {
        db.collection("users").get()
            .addOnSuccessListener { snapshot ->
                onDone(snapshot.documents.mapNotNull { it.toObject(UserProfile::class.java) }, null)
            }
            .addOnFailureListener { onDone(emptyList(), mapError(it)) }
    }

    fun getUsersByIds(uids: List<String>, onDone: (List<UserProfile>, String?) -> Unit) {
        val uniqueUids = uids.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        if (uniqueUids.isEmpty()) {
            onDone(emptyList(), null)
            return
        }

        val refs = uniqueUids.map { uid -> db.collection("users").document(uid) }
        val tasks = refs.map { it.get() }

        Tasks.whenAllSuccess<DocumentSnapshot>(tasks)
            .addOnSuccessListener { docs ->
                val users = docs
                    .mapNotNull { it.toObject(UserProfile::class.java) }
                    .filter { it.rol.trim().lowercase() in listOf("alumno", "student") }
                    .sortedBy { it.nombre }
                onDone(users, null)
            }
            .addOnFailureListener { onDone(emptyList(), mapError(it)) }
    }

    fun updateUserRole(uid: String, role: String, onDone: (Boolean, String?) -> Unit) {
        db.collection("users").document(uid)
            .update("rol", role)
            .addOnSuccessListener { onDone(true, null) }
            .addOnFailureListener { onDone(false, mapError(it)) }
    }

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

    fun createSubject(subject: Subject, onDone: (Boolean, String?) -> Unit) {
        val docRef = if (subject.id.isBlank()) db.collection("subjects").document() else db.collection("subjects").document(subject.id)
        val withId = subject.copy(id = docRef.id)
        docRef.set(withId)
            .addOnSuccessListener { onDone(true, null) }
            .addOnFailureListener { onDone(false, mapError(it)) }
    }

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

    fun getSubjectsByProfessor(uid: String, onDone: (List<Subject>, String?) -> Unit) {
        db.collection("subjects").whereEqualTo("profesorUid", uid).get()
            .addOnSuccessListener { snapshot ->
                onDone(snapshot.documents.mapNotNull { it.toObject(Subject::class.java) }, null)
            }
            .addOnFailureListener { onDone(emptyList(), mapError(it)) }
    }

    fun getSubjectsByStudent(uid: String, onDone: (List<Subject>, String?) -> Unit) {
        db.collection("subjects").whereArrayContains("alumnosUids", uid).get()
            .addOnSuccessListener { snapshot ->
                onDone(snapshot.documents.mapNotNull { it.toObject(Subject::class.java) }, null)
            }
            .addOnFailureListener { onDone(emptyList(), mapError(it)) }
    }

    fun saveGrade(record: GradeRecord, onDone: (Boolean, String?) -> Unit) {
        val id = "${record.subjectId}_${record.studentUid}"
        db.collection("grades").document(id)
            .set(record.copy(id = id))
            .addOnSuccessListener { onDone(true, null) }
            .addOnFailureListener { onDone(false, mapError(it)) }
    }

    fun getGradesByStudent(uid: String, onDone: (List<GradeRecord>, String?) -> Unit) {
        db.collection("grades").whereEqualTo("studentUid", uid).get()
            .addOnSuccessListener { snapshot ->
                onDone(snapshot.documents.mapNotNull { it.toObject(GradeRecord::class.java) }, null)
            }
            .addOnFailureListener { onDone(emptyList(), mapError(it)) }
    }

    fun saveAttendance(record: AttendanceRecord, onDone: (Boolean, String?) -> Unit) {
        db.collection("attendance").add(record)
            .addOnSuccessListener { onDone(true, null) }
            .addOnFailureListener { onDone(false, mapError(it)) }
    }

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
