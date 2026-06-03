package eu.hxreborn.biometricapplock.ui.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface SelfLockState {
    data class Locked(
        val error: Boolean = false,
    ) : SelfLockState

    data object Authenticating : SelfLockState

    data object LockedOut : SelfLockState

    data object Unlocked : SelfLockState
}

class SelfLockViewModel : ViewModel() {
    private val _state = MutableStateFlow<SelfLockState>(SelfLockState.Locked())
    val state: StateFlow<SelfLockState> = _state.asStateFlow()

    fun setAuthenticating() {
        _state.value = SelfLockState.Authenticating
    }

    fun setUnlocked() {
        _state.value = SelfLockState.Unlocked
    }

    fun setLockedOut() {
        _state.value = SelfLockState.LockedOut
    }

    fun setLocked(error: Boolean = false) {
        _state.value = SelfLockState.Locked(error)
    }
}
