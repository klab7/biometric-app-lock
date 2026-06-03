package eu.hxreborn.biometricapplock

import android.app.Activity
import android.content.Intent
import android.hardware.biometrics.BiometricPrompt
import android.os.Bundle
import android.os.CancellationSignal
import android.util.Log

private const val TAG = "BiometricAppLock"

class BiometricAuthActivity : Activity() {
    private var targetPkg: String? = null
    private var authToken: String? = null
    private var replied = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        targetPkg = intent.getStringExtra(EXTRA_TARGET_PKG)
        authToken = intent.getStringExtra(EXTRA_AUTH_TOKEN)

        if (targetPkg.isNullOrEmpty() || authToken.isNullOrEmpty()) {
            Log.w(TAG, "missing extras, finishing")
            finish()
            return
        }
        Log.i(TAG, "gating $targetPkg")
        val pkg = targetPkg ?: return
        val label =
            runCatching {
                packageManager.getApplicationInfo(pkg, 0).loadLabel(packageManager).toString()
            }.getOrDefault(pkg)
        showPrompt("Unlock $label")
    }

    override fun onDestroy() {
        if (!replied) onResult(AUTH_CANCELLED)
        super.onDestroy()
    }

    private fun showPrompt(title: String) {
        val cancellation = CancellationSignal()
        val executor = mainExecutor

        BiometricPrompt
            .Builder(this)
            .setTitle(title)
            .setNegativeButton(getString(android.R.string.cancel), executor) { _, _ ->
                cancellation.cancel()
                onResult(AUTH_CANCELLED)
            }.build()
            .authenticate(
                cancellation,
                executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(
                        result: BiometricPrompt.AuthenticationResult,
                    ) = onResult(AUTH_OK)

                    override fun onAuthenticationFailed() {
                        Log.w(TAG, "attempt failed, prompt stays open")
                    }

                    override fun onAuthenticationError(
                        errorCode: Int,
                        errString: CharSequence,
                    ) = onResult(AUTH_ERROR)
                },
            )
    }

    override fun onStop() {
        super.onStop()
        if (replied) finish()
    }

    private fun onResult(code: Int) {
        if (replied) return
        replied = true
        Log.d(TAG, "onResult code=$code pkg=$targetPkg")
        if (code == AUTH_OK && launchTarget()) return
        goHome()
    }

    private fun goHome() {
        startActivity(
            Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
    }

    private fun launchTarget(): Boolean {
        val pkg = targetPkg ?: return false
        val token = authToken ?: return false
        // just carries the token back so the hook can resume the real launch
        val signal = packageManager.getLaunchIntentForPackage(pkg) ?: return false
        signal.putExtra(EXTRA_AUTH_TOKEN, token)
        signal.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
        Log.i(TAG, "auth ok, signaling resume pkg=$pkg")
        return runCatching { startActivity(signal) }
            .onFailure { Log.w(TAG, "signal failed pkg=$pkg: ${it.message}") }
            .isSuccess
    }

    companion object {
        const val MODULE_PACKAGE = "eu.hxreborn.biometricapplock"
        const val AUTH_ACTIVITY = "$MODULE_PACKAGE.BiometricAuthActivity"

        const val EXTRA_TARGET_PKG = "$MODULE_PACKAGE.TARGET_PKG"
        const val EXTRA_TARGET_CLS = "$MODULE_PACKAGE.TARGET_CLS"
        const val EXTRA_AUTH_TOKEN = "$MODULE_PACKAGE.AUTH_TOKEN"

        private const val AUTH_OK = 1
        private const val AUTH_CANCELLED = 2
        private const val AUTH_ERROR = 3
    }
}
