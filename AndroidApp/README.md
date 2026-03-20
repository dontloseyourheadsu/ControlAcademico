# Proyecto: Aplicación de Control Académico

## 1. Introducción

El objetivo de este proyecto es desarrollar una aplicación móvil para la gestión académica de materias, profesores y alumnos, utilizando Firebase como base de datos en la nube. Los usuarios tendrán distintos roles dentro de la aplicación: Administrador, Profesor y Alumno. Cada uno contará con permisos específicos para gestionar información relevante al control de asistencia y calificaciones.

## 2. Equipo de Trabajo

Cada equipo estará conformado por 2, 3 o 4 personas. Es importante dividir responsabilidades para optimizar el desarrollo.

---

## 3. Funcionalidades de la Aplicación

### 3.1 Administrador

* Cambiar el tipo de usuario de otros usuarios (asignar como profesor o alumno).
* Crear nuevas materias y asignarlas a profesores y alumnos.

### 3.2 Profesor

* Tomar lista de asistencia escaneando un código QR generado en la app del alumno.
* Asignar calificaciones a los alumnos inscritos en su materia.
* Revisar su horario de clases.

### 3.3 Alumno

* Generar un código QR para el pase de lista en la materia actual.
* Ver sus calificaciones.
* Revisar su horario de clases.

---

## 4. Requisitos Técnicos

* **Lenguaje:** Kotlin.
* **Base de Datos:** Firebase Firestore o Firebase Realtime Database.
* **Autenticación:** Firebase Authentication (correo y contraseña).
* **Almacenamiento Local:** SharedPreferences para recordar sesión del usuario.
* **Generación y escaneo de QR:** Biblioteca ZXing.
* **Compatibilidad:** Android (mínimo API 24).

---

## 5. Criterios de Evaluación

| Criterio | Puntos |
| --- | --- |
| Funcionalidad completa | 30 pts |
| Integración con Firebase (Autenticación y Base de Datos) | 25 pts |
| Uso de QR para pase de lista | 15 pts |
| Almacenamiento con SharedPreferences | 10 pts |
| Interfaz de usuario (UI/UX) | 10 pts |
| Código limpio y estructurado | 10 pts |

---

## 6. Entregables del Proyecto

Cada equipo debe entregar:

* Código fuente en un repositorio de GitHub.
* APK de la aplicación lista para instalación en dispositivos Android.
* Informe técnico en PDF con integrantes, roles, descripción de funciones y capturas de pantalla.

## 7. Organización del Equipo

* **Backend Firebase:** Configuración de la base de datos, autenticación y Firestore.
* **Frontend Android:** Desarrollo de la interfaz y funcionalidad en Kotlin.
* **Gestión de QR y Almacenamiento:** Implementación de generación y escaneo de QR + SharedPreferences.
