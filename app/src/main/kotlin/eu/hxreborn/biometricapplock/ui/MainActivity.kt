package eu.hxreborn.biometricapplock.ui

import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricManager.Authenticators
import android.hardware.biometrics.BiometricPrompt
import android.os.Bundle
import android.os.CancellationSignal
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.hxreborn.biometricapplock.App
import eu.hxreborn.biometricapplock.R
import eu.hxreborn.biometricapplock.prefs.AppPrefs
import eu.hxreborn.biometricapplock.prefs.Prefs
import eu.hxreborn.biometricapplock.prefs.ThemeMode
import eu.hxreborn.biometricapplock.ui.theme.BiometricAppLockTheme
import eu.hxreborn.biometricapplock.ui.viewmodel.ScopeViewModel
import eu.hxreborn.biometricapplock.ui.viewmodel.SelfLockViewModel
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper

class MainActivity :
    ComponentActivity(),
    XposedServiceHelper.OnServiceListener {
    private val viewModel: ScopeViewModel by viewModels { ScopeViewModel.Factory }
    private val selfLock: SelfLockViewModel by viewModels()

    private var promptInFlight = false
    private var cancellationSignal: CancellationSignal? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        App.from(this).addServiceListener(this)
        setContent {
            val app = App.from(this@MainActivity)
            val prefs by app.prefsRepository.state.collectAsStateWithLifecycle(initialValue = AppPrefs.Defaults)
            val unlocked by selfLock.unlocked.collectAsStateWithLifecycle()

            SideEffect {
                if (prefs.selfLock) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }

            val darkTheme =
                when (prefs.themeMode) {
                    ThemeMode.FOLLOW_SYSTEM -> isSystemInDarkTheme()
                    ThemeMode.LIGHT -> false
                    ThemeMode.DARK -> true
                }
            LaunchedEffect(darkTheme) {
                val style =
                    if (darkTheme) {
                        SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT,
                        )
                    }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }

            BiometricAppLockTheme(
                themeMode = prefs.themeMode,
                useDynamicColor = prefs.useDynamicColor,
            ) {
                if (prefs.selfLock && !unlocked) {
                    SelfLockScreen(onUnlock = ::promptUnlock)
                } else {
                    MainScaffold(viewModel = viewModel)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val pref = App.from(this).prefsRepository.read(Prefs.SELF_LOCK)
        if (!pref) {
            selfLock.unlock()
        } else if (!selfLock.unlocked.value && !promptInFlight) {
            promptUnlock()
        }
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations && !promptInFlight) {
            selfLock.lock()
        }
    }

    private fun promptUnlock() {
        if (promptInFlight) return
        val bm = getSystemService(BiometricManager::class.java)
        val authenticators = pickAuthenticators(bm)
        if (authenticators == -1) {
            selfLock.unlock()
            return
        }
        promptInFlight = true
        val builder =
            BiometricPrompt
                .Builder(this)
                .setTitle(getString(R.string.self_lock_prompt_title))
                .setConfirmationRequired(false)
                .setAllowedAuthenticators(authenticators)
        if (authenticators and Authenticators.DEVICE_CREDENTIAL == 0) {
            builder.setNegativeButton(getString(android.R.string.cancel), mainExecutor) { _, _ ->
                promptInFlight = false
            }
        }
        val signal = CancellationSignal()
        cancellationSignal = signal
        runCatching {
            builder.build().authenticate(
                signal,
                mainExecutor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        promptInFlight = false
                        selfLock.unlock()
                    }

                    override fun onAuthenticationError(
                        errorCode: Int,
                        errString: CharSequence,
                    ) {
                        promptInFlight = false
                    }

                    override fun onAuthenticationFailed() {}
                },
            )
        }.onFailure {
            promptInFlight = false
        }
    }

    private fun pickAuthenticators(bm: BiometricManager): Int {
        val strongAndCred = Authenticators.BIOMETRIC_STRONG or Authenticators.DEVICE_CREDENTIAL
        val weak = Authenticators.BIOMETRIC_WEAK
        val cred = Authenticators.DEVICE_CREDENTIAL
        return when (BiometricManager.BIOMETRIC_SUCCESS) {
            bm.canAuthenticate(strongAndCred) -> strongAndCred
            bm.canAuthenticate(weak) -> weak
            bm.canAuthenticate(cred) -> cred
            else -> -1
        }
    }

    override fun onServiceBind(service: XposedService) {
        viewModel.onServiceBound(service)
    }

    override fun onServiceDied(service: XposedService) {
        viewModel.onServiceDied()
    }

    override fun onDestroy() {
        cancellationSignal?.cancel()
        super.onDestroy()
        App.from(this).removeServiceListener(this)
    }
}
