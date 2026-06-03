package eu.hxreborn.biometricapplock.prefs

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

data class AppOverrides(
    val relockDelaySeconds: Int?,
    val blockScreenshots: Boolean?,
)

private fun SharedPreferences.getIntOrNull(key: String): Int? =
    if (contains(key)) getInt(key, 0) else null

private fun SharedPreferences.getBooleanOrNull(key: String): Boolean? =
    if (contains(key)) getBoolean(key, false) else null

class AppOverridesRepository(
    private val local: SharedPreferences,
    private val remoteProvider: () -> SharedPreferences? = { null },
) {
    private fun relockKey(pkg: String) = "app_override:$pkg:relock_delay_seconds"

    private fun blockScreenshotsKey(pkg: String) = "app_override:$pkg:block_screenshots"

    private fun prefix(pkg: String) = "app_override:$pkg:"

    private fun currentOverrides(pkg: String) =
        AppOverrides(
            relockDelaySeconds = local.getIntOrNull(relockKey(pkg)),
            blockScreenshots = local.getBooleanOrNull(blockScreenshotsKey(pkg)),
        )

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

    private fun editLocalAndRemote(action: SharedPreferences.Editor.() -> Unit) {
        local.edit(action = action)
        runCatching {
            remoteProvider()?.edit(commit = false) {
                action()
                Prefs.LAST_REMOTE_WRITE.write(this, System.currentTimeMillis())
            }
        }
    }

    fun setRelockDelaySeconds(
        pkg: String,
        seconds: Int?,
    ) {
        val key = relockKey(pkg)
        editLocalAndRemote { if (seconds == null) remove(key) else putInt(key, seconds) }
    }

    fun setBlockScreenshots(
        pkg: String,
        blocked: Boolean?,
    ) {
        val key = blockScreenshotsKey(pkg)
        editLocalAndRemote { if (blocked == null) remove(key) else putBoolean(key, blocked) }
    }

    fun reset(pkg: String) =
        editLocalAndRemote {
            remove(relockKey(pkg))
            remove(blockScreenshotsKey(pkg))
        }

    fun prune(installedPackages: Set<String>) {
        val keysToRemove =
            local.all.keys.filter { key ->
                if (!key.startsWith("app_override:")) return@filter false
                val pkg = key.removePrefix("app_override:").substringBefore(":")
                pkg !in installedPackages
            }
        if (keysToRemove.isEmpty()) return
        editLocalAndRemote { keysToRemove.forEach { remove(it) } }
    }
}
