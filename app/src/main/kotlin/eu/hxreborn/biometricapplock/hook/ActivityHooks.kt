package eu.hxreborn.biometricapplock.hook

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.WindowManager.LayoutParams.FLAG_SECURE
import eu.hxreborn.biometricapplock.util.Logger
import io.github.libxposed.api.XposedModule

internal fun XposedModule.registerActivityHooks(
    cl: ClassLoader,
    state: AuthState,
) {
    hookOnCreate(cl)
    hookOnResume(cl, state)
    hookOnStart(cl, state)
    hookOnStop(cl, state)
}

private fun XposedModule.hookOnCreate(cl: ClassLoader) {
    runCatching {
        val onCreate =
            cl
                .loadClass("android.app.Activity")
                .getDeclaredMethod("onCreate", Bundle::class.java)
        hook(onCreate).intercept { chain ->
            val a = chain.thisObject as? Activity
            if (a != null) {
                val pkg = a.packageName
                val captureAllowed = AppOverrides.flagSecureDisabled(pkg)
                if (!captureAllowed) {
                    runCatching { a.window?.setFlags(FLAG_SECURE, FLAG_SECURE) }
                        .onFailure {
                            Logger.debug {
                                "setFlags FLAG_SECURE failed on ${a.localClassName}: ${it.message}"
                            }
                        }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        runCatching { a.setRecentsScreenshotEnabled(false) }
                            .onFailure {
                                Logger.debug {
                                    "setRecentsScreenshotEnabled failed on ${a.localClassName}: ${it.message}"
                                }
                            }
                    }
                }
            }
            chain.proceed()
        }
    }.onFailure { Logger.log(Log.ERROR, "hookOnCreate failed: ${it.message}") }
}

private fun XposedModule.hookOnResume(
    cl: ClassLoader,
    state: AuthState,
) {
    runCatching {
        val onResume = cl.loadClass("android.app.Activity").getDeclaredMethod("onResume")
        hook(onResume).intercept { chain ->
            val a = chain.thisObject as? Activity
            if (a != null) {
                val delaySeconds = AppOverrides.relockDelaySeconds(a.packageName)
                if (delaySeconds >= 0) {
                    val relocked =
                        state.maybeRelockOnResume(
                            delaySeconds * 1000L,
                            SystemClock.elapsedRealtime(),
                        )
                    if (relocked) {
                        Logger.debug {
                            "relock by delay pkg=${a.packageName} delayMs=${delaySeconds * 1000L}"
                        }
                    }
                }
            }
            if (a != null && !state.authenticated) applyBlackout(a)
            val result = chain.proceed()
            if (a == null) return@intercept result
            Logger.debug {
                "onResume ${a.localClassName} authenticated=${state.authenticated} authInFlight=${state.authInFlight}"
            }
            if (!state.authenticated && !state.authInFlight) launchTrampoline(a, state)
            result
        }
    }.onFailure { Logger.log(Log.ERROR, "hookOnResume failed: ${it.message}") }
}

private fun XposedModule.hookOnStart(
    cl: ClassLoader,
    state: AuthState,
) {
    runCatching {
        val onStart = cl.loadClass("android.app.Activity").getDeclaredMethod("onStart")
        hook(onStart).intercept { chain ->
            val result = chain.proceed()
            state.onActivityStarted()
            result
        }
    }.onFailure { Logger.log(Log.ERROR, "hookOnStart failed: ${it.message}") }
}

private fun XposedModule.hookOnStop(
    cl: ClassLoader,
    state: AuthState,
) {
    runCatching {
        val onStop = cl.loadClass("android.app.Activity").getDeclaredMethod("onStop")
        hook(onStop).intercept { chain ->
            val result = chain.proceed()
            val a = chain.thisObject as? Activity
            val backgroundedBefore = state.backgroundedAt
            val reLocked = state.onActivityStopped()
            Logger.debug {
                "onStop ${a?.localClassName} started=${state.startedActivities} authenticated=${state.authenticated} authInFlight=${state.authInFlight}"
            }
            if (reLocked) {
                Logger.log(Log.INFO, "re-locked on background")
            } else if (state.backgroundedAt != backgroundedBefore) {
                Logger.debug {
                    "background recorded started=0 backgroundedAt=${state.backgroundedAt} re-lock deferred"
                }
            }
            result
        }
    }.onFailure { Logger.log(Log.ERROR, "hookOnStop failed: ${it.message}") }
}
