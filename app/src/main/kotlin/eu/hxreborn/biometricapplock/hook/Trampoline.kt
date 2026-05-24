package eu.hxreborn.biometricapplock.hook

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.util.Log
import eu.hxreborn.biometricapplock.BiometricAuthActivity
import eu.hxreborn.biometricapplock.TrampolineReceiver
import eu.hxreborn.biometricapplock.util.Logger
import io.github.libxposed.api.XposedModule
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

private const val TIMEOUT_MS = 60_000L
private val WATCHDOG_TOKEN = Any()

internal fun XposedModule.launchTrampoline(
    activity: Activity,
    state: AuthState,
) {
    state.beginAuth()
    val mainHandler = Handler(Looper.getMainLooper())
    val replied = AtomicBoolean(false)
    val callbackAction =
        "${BiometricAuthActivity.MODULE_PACKAGE}.AUTH_CALLBACK_${Process.myPid()}"
    val nonce = UUID.randomUUID().toString()

    fun completeFailure() {
        state.endAuthFailure()
        runCatching { activity.finishAndRemoveTask() }
            .onFailure { Logger.debug { "finishAndRemoveTask failed: ${it.message}" } }
    }

    fun completeSuccess() {
        state.endAuthSuccess()
        releaseBlackout(activity)
    }

    val receiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                ctx: Context,
                intent: Intent,
            ) {
                if (intent.getStringExtra(BiometricAuthActivity.EXTRA_NONCE) != nonce) return
                val code =
                    intent.getIntExtra(
                        BiometricAuthActivity.EXTRA_RESULT_CODE,
                        BiometricAuthActivity.RESULT_CANCELLED,
                    )
                if (!replied.compareAndSet(false, true)) return
                mainHandler.removeCallbacksAndMessages(WATCHDOG_TOKEN)
                mainHandler.post {
                    if (code ==
                        BiometricAuthActivity.RESULT_OK
                    ) {
                        completeSuccess()
                    } else {
                        completeFailure()
                    }
                }
                runCatching { activity.unregisterReceiver(this) }
                    .onFailure { Logger.debug { "unregisterReceiver failed: ${it.message}" } }
            }
        }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        activity.registerReceiver(
            receiver,
            IntentFilter(callbackAction),
            Context.RECEIVER_EXPORTED,
        )
    } else {
        @Suppress("UnspecifiedRegisterReceiverFlag")
        activity.registerReceiver(receiver, IntentFilter(callbackAction))
    }

    val label =
        runCatching {
            activity.applicationInfo.loadLabel(activity.packageManager).toString()
        }.getOrDefault(activity.packageName)
    val title = "Unlock $label"

    val directIntent =
        Intent().apply {
            component =
                ComponentName(
                    BiometricAuthActivity.MODULE_PACKAGE,
                    BiometricAuthActivity.AUTH_ACTIVITY,
                )
            putExtra(BiometricAuthActivity.EXTRA_CALLBACK_ACTION, callbackAction)
            putExtra(BiometricAuthActivity.EXTRA_TITLE, title)
            putExtra(BiometricAuthActivity.EXTRA_NONCE, nonce)
            addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
        }

    val directOk = runCatching { activity.startActivity(directIntent) }.isSuccess

    if (!directOk) {
        Logger.log(Log.WARN, "direct blocked, using broadcast fallback")
        if (!sendTrampolineBroadcast(activity, callbackAction, title, nonce)) {
            replied.set(true)
            completeFailure()
            runCatching { activity.unregisterReceiver(receiver) }
                .onFailure { Logger.debug { "unregisterReceiver failed: ${it.message}" } }
        }
    }

    if (!replied.get()) {
        val watchdog =
            Runnable {
                if (replied.compareAndSet(false, true)) {
                    Logger.log(Log.ERROR, "trampoline timeout")
                    completeFailure()
                    runCatching { activity.unregisterReceiver(receiver) }
                        .onFailure { Logger.debug { "unregisterReceiver failed: ${it.message}" } }
                }
            }
        mainHandler.postAtTime(
            watchdog,
            WATCHDOG_TOKEN,
            TIMEOUT_MS + SystemClock.uptimeMillis(),
        )
    }
}

private fun XposedModule.sendTrampolineBroadcast(
    activity: Activity,
    callbackAction: String,
    title: String,
    nonce: String,
): Boolean {
    val broadcast =
        Intent(TrampolineReceiver.ACTION).apply {
            component =
                ComponentName(
                    BiometricAuthActivity.MODULE_PACKAGE,
                    "${BiometricAuthActivity.MODULE_PACKAGE}.TrampolineReceiver",
                )
            putExtra(BiometricAuthActivity.EXTRA_CALLBACK_ACTION, callbackAction)
            putExtra(BiometricAuthActivity.EXTRA_TITLE, title)
            putExtra(BiometricAuthActivity.EXTRA_NONCE, nonce)
        }
    return runCatching { activity.sendBroadcast(broadcast) }
        .onFailure { Logger.log(Log.ERROR, "broadcast failed: ${it.message}") }
        .isSuccess
}
