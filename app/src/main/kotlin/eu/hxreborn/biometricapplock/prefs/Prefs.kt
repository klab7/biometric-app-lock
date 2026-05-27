package eu.hxreborn.biometricapplock.prefs

object Prefs {
    const val GROUP = "biometric_app_lock_prefs"

    val DARK_THEME_CONFIG = StringPref("dark_theme_config", ThemeMode.FOLLOW_SYSTEM.prefValue)
    val USE_DYNAMIC_COLOR = BoolPref("use_dynamic_color", true)
    val LOCKED_PACKAGES = StringPref("locked_packages", "")
    val RELOCK_DELAY_SECONDS = IntPref("relock_delay_seconds", 0)
    val DISABLE_FLAG_SECURE = BoolPref("disable_flag_secure", false)

    // hook reads this to gauge cache staleness
    val LAST_REMOTE_WRITE = LongPref("_last_remote_write", 0L)

    val FLOATING_NAV_BAR = BoolPref("floating_nav_bar", true)

    val AUTO_CHECK_UPDATE = BoolPref("auto_check_update", true)
    val LAST_UPDATE_CHECK_MS = LongPref("last_update_check_ms", 0L)
    val LAST_RELEASE_JSON = StringPref("last_release_json", "")
    val LAST_CHANGELOG_JSON = StringPref("last_changelog_json", "")
    val LAST_DISMISSED_AVAILABLE_VERSION = StringPref("last_dismissed_available_version", "")
    val RELEASE_ETAG = StringPref("release_etag", "")
    val CHANGELOG_ETAG = StringPref("changelog_etag", "")
}
