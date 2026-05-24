package eu.hxreborn.biometricapplock.hook

import android.os.SystemClock

internal class AuthState {
    @Volatile var authenticated: Boolean = false

    @Volatile var authInFlight: Boolean = false

    @Volatile var startedActivities: Int = 0

    @Volatile var backgroundedAt: Long = 0L

    fun onActivityStarted() {
        startedActivities++
    }

    fun onActivityStopped(): Boolean {
        startedActivities = (startedActivities - 1).coerceAtLeast(0)
        if (startedActivities == 0 && !authInFlight && authenticated) {
            backgroundedAt = SystemClock.elapsedRealtime()
        }
        return false
    }

    fun maybeRelockOnResume(
        delayMs: Long,
        now: Long,
    ): Boolean {
        if (!authenticated || backgroundedAt <= 0L) return false
        if ((now - backgroundedAt) < delayMs) return false
        authenticated = false
        return true
    }

    fun beginAuth() {
        authInFlight = true
    }

    fun endAuthSuccess() {
        authenticated = true
        authInFlight = false
        backgroundedAt = 0L
    }

    fun endAuthFailure() {
        authInFlight = false
    }
}
