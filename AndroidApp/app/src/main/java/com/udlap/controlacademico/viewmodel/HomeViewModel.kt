package com.udlap.controlacademico.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.udlap.controlacademico.data.FirestoreRepository
import com.udlap.controlacademico.model.UserProfile

sealed interface HomeNavigation {
    object ToLogin : HomeNavigation
    object ToProfileSetup : HomeNavigation
    data class ToRolePanel(val normalizedRole: String) : HomeNavigation
}

data class HomeViewState(
    val isLoading: Boolean = false,
    val profile: UserProfile? = null,
    val normalizedRole: String = "alumno"
)

class HomeViewModel(
    private val repository: FirestoreRepository = FirestoreRepository()
) : ViewModel() {

    private val _state = MutableLiveData(HomeViewState())
    val state: LiveData<HomeViewState> = _state

    private val _toastEvent = MutableLiveData<Event<String>>()
    val toastEvent: LiveData<Event<String>> = _toastEvent

    private val _navigationEvent = MutableLiveData<Event<HomeNavigation>>()
    val navigationEvent: LiveData<Event<HomeNavigation>> = _navigationEvent

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

    fun openRolePanelRequested() {
        val current = _state.value
        if (current?.profile == null || current.isLoading) {
            _toastEvent.value = Event("Cargando perfil, espera un momento")
            return
        }
        _navigationEvent.value = Event(HomeNavigation.ToRolePanel(current.normalizedRole))
    }
}
