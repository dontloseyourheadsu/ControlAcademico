package com.udlap.controlacademico.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.udlap.controlacademico.data.FirestoreRepository
import com.udlap.controlacademico.model.UserProfile

/**
 * Navigation events emitted by [HomeViewModel] and consumed by HomeActivity.
 */
sealed interface HomeNavigation {
    /** Route to login when there is no active user session. */
    object ToLogin : HomeNavigation

    /** Route to profile setup when user has auth but no profile document. */
    object ToProfileSetup : HomeNavigation

    /** Route to role-specific panel according to normalized role. */
    data class ToRolePanel(val normalizedRole: String) : HomeNavigation
}

/**
 * Render model for Home screen.
 *
 * @property isLoading True while profile request is in flight.
 * @property profile Loaded profile used to render user summary text.
 * @property normalizedRole Canonical role used for button label and panel routing.
 */
data class HomeViewState(
    val isLoading: Boolean = false,
    val profile: UserProfile? = null,
    val normalizedRole: String = "alumno"
)

/**
 * ViewModel for Home screen session/profile orchestration.
 *
 * MVVM connection:
 * - Receives user actions from HomeActivity.
 * - Queries [FirestoreRepository] for profile data.
 * - Emits UI state, toast events, and navigation events.
 */
class HomeViewModel(
    private val repository: FirestoreRepository = FirestoreRepository()
) : ViewModel() {

    /** Internal mutable state holder for loading/profile data. */
    private val _state = MutableLiveData(HomeViewState())

    /** Immutable state observed by HomeActivity to render controls and text. */
    val state: LiveData<HomeViewState> = _state

    /** One-time user feedback channel for transient messages. */
    private val _toastEvent = MutableLiveData<Event<String>>()

    /** Public toast stream consumed by UI. */
    val toastEvent: LiveData<Event<String>> = _toastEvent

    /** One-time navigation channel that Activity maps to intents. */
    private val _navigationEvent = MutableLiveData<Event<HomeNavigation>>()

    /** Public navigation stream for role/session routing. */
    val navigationEvent: LiveData<Event<HomeNavigation>> = _navigationEvent

    /**
     * Loads current profile and decides where Home flow should continue.
     */
    fun loadProfile(uid: String?) {
        if (uid.isNullOrBlank()) {
            _navigationEvent.value = Event(HomeNavigation.ToLogin)
            return
        }

        _state.value = _state.value?.copy(isLoading = true)

        repository.getUserProfile(uid) { profile, error ->
            if (profile == null) {
                _state.value = HomeViewState(isLoading = false)
                if (!error.isNullOrBlank()) {
                    _toastEvent.value = Event(error)
                }
                _navigationEvent.value = Event(HomeNavigation.ToProfileSetup)
                return@getUserProfile
            }

            val normalizedRole = when (profile.rol.trim().lowercase()) {
                "admin", "administrador" -> "admin"
                "profesor", "professor" -> "profesor"
                else -> "alumno"
            }

            _state.value = HomeViewState(
                isLoading = false,
                profile = profile,
                normalizedRole = normalizedRole
            )
        }
    }

    /**
     * Handles "open panel" UI intent once profile data is ready.
     */
    fun openRolePanelRequested() {
        val current = _state.value
        if (current?.profile == null || current.isLoading) {
            _toastEvent.value = Event("Cargando perfil, espera un momento")
            return
        }
        _navigationEvent.value = Event(HomeNavigation.ToRolePanel(current.normalizedRole))
    }
}
