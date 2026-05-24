package eu.hxreborn.biometricapplock

import android.app.Activity
import android.content.Intent
import android.hardware.biometrics.BiometricPrompt
import android.os.Bundle
import android.os.CancellationSignal
import android.util.Log
import eu.hxreborn.biometricapplock.R
import java.util.concurrent.Executor

private const val TAG = "BiometricAppLock"

class BiometricAuthActivity : Activity() {
    private var callbackAction: String? = null
    private var nonce: String? = null
    private var replied = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val action = intent.getStringExtra(EXTRA_CALLBACK_ACTION)
        val incomingNonce = intent.getStringExtra(EXTRA_NONCE)
        val title =
            intent.getStringExtra(EXTRA_TITLE) ?: getString(R.string.biometric_prompt_default_title)

        if (action.isNullOrEmpty() ||
            !action.startsWith(CALLBACK_ACTION_PREFIX) ||
            incomingNonce.isNullOrEmpty()
        ) {
            Log.w(
                TAG,
                "rejecting launch: action=$action nonce=${incomingNonce?.length ?: 0}b caller=$callingPackage",
            )
            finish()
            return
        }
        callbackAction = action
        nonce = incomingNonce
        showPrompt(title)
    }

    override fun onDestroy() {
        if (!replied) reply(RESULT_CANCELLED)
        super.onDestroy()
    }

    private fun showPrompt(title: String) {
        val executor = Executor { r -> runOnUiThread(r) }
        val cancellation = CancellationSignal()

        val prompt =
            BiometricPrompt
                .Builder(this)
                .setTitle(title)
                .setNegativeButton(getString(android.R.string.cancel), executor) { _, _ ->
                    cancellation.cancel()
                    reply(RESULT_CANCELLED)
                }.build()

        prompt.authenticate(
            cancellation,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(
                    result: BiometricPrompt.AuthenticationResult,
                ) {
                    reply(RESULT_OK)
                }

                override fun onAuthenticationFailed() {
                    Log.w(TAG, "attempt failed, prompt stays open")
                }

                override fun onAuthenticationError(
                    errorCode: Int,
                    errString: CharSequence,
                ) {
                    reply(RESULT_ERROR)
                }
            },
        )
    }

    private fun reply(code: Int) {
        if (replied) return
        replied = true
        callbackAction?.let {
            runCatching {
                sendBroadcast(
                    Intent(it)
                        .putExtra(EXTRA_RESULT_CODE, code)
                        .putExtra(EXTRA_NONCE, nonce),
                )
            }
        }
        finish()
    }

    companion object {
        const val EXTRA_CALLBACK_ACTION = "eu.hxreborn.biometricapplock.CALLBACK_ACTION"
        const val EXTRA_TITLE = "eu.hxreborn.biometricapplock.TITLE"
        const val EXTRA_RESULT_CODE = "eu.hxreborn.biometricapplock.RESULT_CODE"
        const val EXTRA_NONCE = "eu.hxreborn.biometricapplock.NONCE"

        const val RESULT_OK = 1
        const val RESULT_CANCELLED = 2
        const val RESULT_ERROR = 3

        const val MODULE_PACKAGE = "eu.hxreborn.biometricapplock"
        const val AUTH_ACTIVITY = "$MODULE_PACKAGE.BiometricAuthActivity"
        const val CALLBACK_ACTION_PREFIX = "$MODULE_PACKAGE.AUTH_CALLBACK_"
    }
}
