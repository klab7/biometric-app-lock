package eu.hxreborn.biometricapplock.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.hxreborn.biometricapplock.App
import eu.hxreborn.biometricapplock.prefs.AppPrefs
import eu.hxreborn.biometricapplock.ui.theme.BiometricAppLockTheme
import eu.hxreborn.biometricapplock.ui.viewmodel.ScopeViewModel
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper

class MainActivity :
    ComponentActivity(),
    XposedServiceHelper.OnServiceListener {
    private val viewModel: ScopeViewModel by viewModels { ScopeViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        App.addServiceListener(this)
        setContent {
            val prefs by App.prefsRepository.state.collectAsStateWithLifecycle(initialValue = AppPrefs.Defaults)
            BiometricAppLockTheme(
                themeMode = prefs.themeMode,
                useDynamicColor = prefs.useDynamicColor,
            ) {
                MainScaffold(viewModel = viewModel)
            }
        }
    }

    override fun onServiceBind(service: XposedService) {
        viewModel.onServiceBound(service)
    }

    override fun onServiceDied(service: XposedService) {
        viewModel.onServiceDied()
    }

    override fun onDestroy() {
        super.onDestroy()
        App.removeServiceListener(this)
    }
}
