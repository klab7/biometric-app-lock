package eu.hxreborn.biometricapplock.hook

import android.content.SharedPreferences
import eu.hxreborn.biometricapplock.module
import eu.hxreborn.biometricapplock.prefs.Prefs

internal object AppOverrides {
    private const val PREFIX = "app_override:"

    fun relockDelaySeconds(pkg: String): Int {
        val prefs = prefs() ?: return 0
        val key = "${PREFIX}$pkg:relock_delay_seconds"
        return if (prefs.contains(key)) {
            prefs.getInt(key, 0)
        } else {
            prefs.getInt(Prefs.RELOCK_DELAY_SECONDS.key, 0)
        }
    }

    fun flagSecureDisabled(pkg: String): Boolean {
        val prefs = prefs() ?: return false
        val key = "${PREFIX}$pkg:flag_secure_disabled"
        return if (prefs.contains(key)) {
            prefs.getBoolean(key, false)
        } else {
            prefs.getBoolean(Prefs.DISABLE_FLAG_SECURE.key, false)
        }
    }

    private fun prefs(): SharedPreferences? =
        runCatching { module.getRemotePreferences(Prefs.GROUP) }.getOrNull()
}
