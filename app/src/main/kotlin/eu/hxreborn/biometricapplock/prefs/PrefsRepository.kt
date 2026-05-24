package eu.hxreborn.biometricapplock.prefs

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

private const val TAG = "BiometricAppLock"

data class AppPrefs(
    val themeMode: ThemeMode,
    val useDynamicColor: Boolean,
    val launcherHidden: Boolean,
    val verbose: Boolean,
    val relockDelaySeconds: Int,
    val disableFlagSecure: Boolean,
) {
    companion object {
        val Defaults =
            AppPrefs(
                themeMode = ThemeMode.FOLLOW_SYSTEM,
                useDynamicColor = true,
                launcherHidden = false,
                verbose = false,
                relockDelaySeconds = 0,
                disableFlagSecure = false,
            )
    }
}

class PrefsRepository(
    private val local: SharedPreferences,
    private val remoteProvider: () -> SharedPreferences? = { null },
) {
    val state: Flow<AppPrefs> =
        callbackFlow {
            fun emit() {
                trySend(
                    AppPrefs(
                        themeMode = ThemeMode.fromPrefValue(Prefs.DARK_THEME_CONFIG.read(local)),
                        useDynamicColor = Prefs.USE_DYNAMIC_COLOR.read(local),
                        launcherHidden = Prefs.LAUNCHER_HIDDEN.read(local),
                        verbose = Prefs.VERBOSE.read(local),
                        relockDelaySeconds = Prefs.RELOCK_DELAY_SECONDS.read(local),
                        disableFlagSecure = Prefs.DISABLE_FLAG_SECURE.read(local),
                    ),
                )
            }

            emit()
            val listener =
                SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> emit() }
            local.registerOnSharedPreferenceChangeListener(listener)
            awaitClose { local.unregisterOnSharedPreferenceChangeListener(listener) }
        }

    fun <T : Any> save(
        spec: PrefSpec<T>,
        value: T,
    ) {
        local.edit { spec.write(this, value) }
        runCatching {
            remoteProvider()?.edit(commit = false) {
                spec.write(this, value)
                Prefs.LAST_REMOTE_WRITE.write(this, System.currentTimeMillis())
            }
        }
    }

    fun syncToRemote() {
        val remote = remoteProvider() ?: return
        runCatching {
            remote.edit(commit = false) {
                local.all.forEach { (key, value) ->
                    when (value) {
                        is Boolean -> {
                            putBoolean(key, value)
                        }

                        is Int -> {
                            putInt(key, value)
                        }

                        is Long -> {
                            putLong(key, value)
                        }

                        is Float -> {
                            putFloat(key, value)
                        }

                        is String -> {
                            putString(key, value)
                        }

                        // Writing emptySet<String>() produces kotlin.collections.EmptySet, which remote-prefs can't deserialize
                        else -> {
                            Log.w(
                                "BiometricAppLock",
                                "syncToRemote skipping $key type=${value?.javaClass?.simpleName}",
                            )
                        }
                    }
                }
                Prefs.LAST_REMOTE_WRITE.write(this, System.currentTimeMillis())
            }
        }
    }

    companion object {
        fun create(
            context: Context,
            remoteProvider: () -> SharedPreferences? = { null },
        ): PrefsRepository =
            PrefsRepository(
                context.getSharedPreferences(Prefs.GROUP, Context.MODE_PRIVATE),
                remoteProvider,
            )
    }
}
