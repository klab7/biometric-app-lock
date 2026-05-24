package eu.hxreborn.biometricapplock.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import eu.hxreborn.biometricapplock.App
import io.github.libxposed.service.XposedService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val TAG = "BiometricAppLock"

data class FrameworkInfo(
    val name: String,
    val version: String,
)

class ScopeViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val _framework = MutableStateFlow<FrameworkInfo?>(null)
    val framework: StateFlow<FrameworkInfo?> = _framework.asStateFlow()

    private val _scope = MutableStateFlow<Set<String>>(emptySet())
    val scope: StateFlow<Set<String>> = _scope.asStateFlow()

    init {
        App.boundService?.let { onServiceBound(it) }
    }

    fun onServiceBound(service: XposedService) {
        _framework.value = FrameworkInfo(service.frameworkName, "v${service.frameworkVersion}")
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { service.scope.toSet() }
                .onSuccess { _scope.value = it }
                .onFailure { Log.w(TAG, "scope load failed: ${it.message}") }
        }
    }

    fun onServiceDied() {
        _framework.value = null
    }

    fun toggleScope(
        packageName: String,
        enable: Boolean,
    ) {
        val service = App.boundService ?: return
        if (enable) {
            service.requestScope(listOf(packageName), approvalListener)
        } else {
            _scope.value = _scope.value - packageName
            viewModelScope.launch(Dispatchers.IO) {
                runCatching { service.removeScope(listOf(packageName)) }
                    .onSuccess { Log.i(TAG, "scope removed: $packageName") }
                    .onFailure {
                        Log.w(TAG, "scope remove failed: ${it.message}")
                        _scope.value = _scope.value + packageName
                    }
            }
        }
    }

    fun clearScope(packages: Set<String> = _scope.value) {
        val service = App.boundService ?: return
        if (packages.isEmpty()) return
        _scope.value = _scope.value - packages
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { service.removeScope(packages.toList()) }
                .onFailure {
                    Log.w(TAG, "scope clear failed: ${it.message}")
                    _scope.value = _scope.value + packages
                }
        }
    }

    fun restoreScope(previous: Set<String>) {
        val service = App.boundService ?: return
        if (previous.isEmpty()) return
        service.requestScope(previous.toList(), approvalListener)
    }

    private val approvalListener =
        object : XposedService.OnScopeEventListener {
            override fun onScopeRequestApproved(approved: List<String>) {
                Log.i(TAG, "scope approved: $approved")
                _scope.value = _scope.value + approved
            }

            override fun onScopeRequestFailed(message: String) {
                Log.w(TAG, "scope request failed: $message")
            }
        }

    companion object {
        val Factory =
            viewModelFactory {
                initializer {
                    ScopeViewModel(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application)
                }
            }
    }
}
