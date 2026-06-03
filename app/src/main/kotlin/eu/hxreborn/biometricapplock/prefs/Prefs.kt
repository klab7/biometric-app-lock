package eu.hxreborn.biometricapplock.prefs

object Prefs {
    const val GROUP = "biometric_app_lock_prefs"

    val DARK_THEME_CONFIG = StringPref("dark_theme_config", ThemeMode.FOLLOW_SYSTEM.prefValue)
    val USE_DYNAMIC_COLOR = BoolPref("use_dynamic_color", true)
    val LOCKED_PACKAGES = StringPref("locked_packages", "")
    val RELOCK_DELAY_SECONDS = IntPref("relock_delay_seconds", 0)
    val RELOCK_ON_SCREEN_OFF = BoolPref("relock_on_screen_off", true)
    val RELOCK_ON_TASK_REMOVED = BoolPref("relock_on_task_removed", true)
    val BLOCK_SCREENSHOTS = BoolPref("block_screenshots", false)
    val PREVENT_MODULE_UNINSTALL = BoolPref("prevent_module_uninstall", false)

    // hook reads this to gauge cache staleness
    val LAST_REMOTE_WRITE = LongPref("_last_remote_write", 0L)

    val FLOATING_NAV_BAR = BoolPref("floating_nav_bar", true)

    val AUTO_CHECK_UPDATE = BoolPref("auto_check_update", true)
    val LAST_UPDATE_CHECK_MS = LongPref("last_update_check_ms", 0L)
    val LAST_RELEASE_JSON = StringPref("last_release_json", "")
    val LAST_REMOTE_VERSION_CODE = IntPref("last_remote_version_code", 0)
    val LAST_CHANGELOG_JSON = StringPref("last_changelog_json", "")
    val LAST_DISMISSED_AVAILABLE_VERSION = StringPref("last_dismissed_available_version", "")
    val RELEASE_ETAG = StringPref("release_etag", "")
    val CHANGELOG_ETAG = StringPref("changelog_etag", "")

    val all: List<PrefSpec<*>> =
        listOf(
            DARK_THEME_CONFIG,
            USE_DYNAMIC_COLOR,
            LOCKED_PACKAGES,
            RELOCK_DELAY_SECONDS,
            RELOCK_ON_SCREEN_OFF,
            RELOCK_ON_TASK_REMOVED,
            BLOCK_SCREENSHOTS,
            PREVENT_MODULE_UNINSTALL,
            LAST_REMOTE_WRITE,
            FLOATING_NAV_BAR,
            AUTO_CHECK_UPDATE,
            LAST_UPDATE_CHECK_MS,
            LAST_RELEASE_JSON,
            LAST_REMOTE_VERSION_CODE,
            LAST_CHANGELOG_JSON,
            LAST_DISMISSED_AVAILABLE_VERSION,
            RELEASE_ETAG,
            CHANGELOG_ETAG,
        )
}
