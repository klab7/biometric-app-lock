package eu.hxreborn.biometricapplock

import android.app.ActivityOptions
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import eu.hxreborn.biometricapplock.R

private const val TAG = "BiometricAppLock"

class TrampolineReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val callbackAction =
            intent.getStringExtra(BiometricAuthActivity.EXTRA_CALLBACK_ACTION)
        val title =
            intent.getStringExtra(BiometricAuthActivity.EXTRA_TITLE)
                ?: context.getString(R.string.biometric_prompt_default_title)
        val nonce = intent.getStringExtra(BiometricAuthActivity.EXTRA_NONCE)

        val launch =
            Intent().apply {
                component =
                    ComponentName(
                        context.packageName,
                        BiometricAuthActivity.AUTH_ACTIVITY,
                    )
                putExtra(BiometricAuthActivity.EXTRA_CALLBACK_ACTION, callbackAction)
                putExtra(BiometricAuthActivity.EXTRA_TITLE, title)
                // Pass nonce through because the hooked-process receiver validates it and drops mismatches
                putExtra(BiometricAuthActivity.EXTRA_NONCE, nonce)
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS,
                )
            }

        val opts = ActivityOptions.makeCustomAnimation(context, 0, 0)
        runCatching { context.startActivity(launch, opts.toBundle()) }
            .onFailure {
                Log.e(TAG, "startActivity failed: ${it.message}")
                if (callbackAction != null) {
                    context.sendBroadcast(
                        Intent(callbackAction)
                            .putExtra(
                                BiometricAuthActivity.EXTRA_RESULT_CODE,
                                BiometricAuthActivity.RESULT_ERROR,
                            ).putExtra(BiometricAuthActivity.EXTRA_NONCE, nonce),
                    )
                }
            }
    }

    companion object {
        const val ACTION = "eu.hxreborn.biometricapplock.TRAMPOLINE"
    }
}
