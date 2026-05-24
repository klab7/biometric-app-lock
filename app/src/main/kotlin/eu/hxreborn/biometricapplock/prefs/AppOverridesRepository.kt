package eu.hxreborn.biometricapplock.prefs

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

data class AppOverrides(
    val relockDelaySeconds: Int?,
    val flagSecureDisabled: Boolean?,
)

class AppOverridesRepository(
    private val local: SharedPreferences,
    private val remoteProvider: () -> SharedPreferences? = { null },
) {
    private fun relockKey(pkg: String) = "app_override:$pkg:relock_delay_seconds"

    private fun flagSecureKey(pkg: String) = "app_override:$pkg:flag_secure_disabled"

    private fun prefix(pkg: String) = "app_override:$pkg:"

    private fun currentOverrides(pkg: String): AppOverrides {
        val delay = if (local.contains(relockKey(pkg))) local.getInt(relockKey(pkg), 0) else null
        val flagSecure =
            if (local.contains(
                    flagSecureKey(pkg),
                )
            ) {
                local.getBoolean(flagSecureKey(pkg), false)
            } else {
                null
            }
        return AppOverrides(relockDelaySeconds = delay, flagSecureDisabled = flagSecure)
    }

    fun observe(pkg: String): Flow<AppOverrides> =
        callbackFlow {
            trySend(currentOverrides(pkg))
            val listener =
                SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                    if (key?.startsWith(prefix(pkg)) == true) trySend(currentOverrides(pkg))
                }
            local.registerOnSharedPreferenceChangeListener(listener)
            awaitClose { local.unregisterOnSharedPreferenceChangeListener(listener) }
        }

    fun setRelockDelaySeconds(
        pkg: String,
        seconds: Int?,
    ) {
        val key = relockKey(pkg)
        local.edit { if (seconds == null) remove(key) else putInt(key, seconds) }
        remoteProvider()?.edit(commit = true) {
            if (seconds ==
                null
            ) {
                remove(key)
            } else {
                putInt(key, seconds)
            }
        }
    }

    fun setFlagSecureDisabled(
        pkg: String,
        disabled: Boolean?,
    ) {
        val key = flagSecureKey(pkg)
        local.edit { if (disabled == null) remove(key) else putBoolean(key, disabled) }
        remoteProvider()?.edit(commit = true) {
            if (disabled ==
                null
            ) {
                remove(key)
            } else {
                putBoolean(key, disabled)
            }
        }
    }

    fun reset(pkg: String) {
        local.edit {
            remove(relockKey(pkg))
            remove(flagSecureKey(pkg))
        }
        remoteProvider()?.edit(commit = true) {
            remove(relockKey(pkg))
            remove(flagSecureKey(pkg))
        }
    }

    fun prune(installedPackages: Set<String>) {
        val keysToRemove =
            local.all.keys.filter { key ->
                if (!key.startsWith("app_override:")) return@filter false
                val pkg = key.removePrefix("app_override:").substringBefore(":")
                pkg !in installedPackages
            }
        if (keysToRemove.isEmpty()) return
        local.edit { keysToRemove.forEach { remove(it) } }
        remoteProvider()?.edit(commit = true) { keysToRemove.forEach { remove(it) } }
    }
}
