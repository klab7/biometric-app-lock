package eu.hxreborn.biometricapplock.prefs

enum class ThemeMode {
    FOLLOW_SYSTEM,
    LIGHT,
    DARK,
    ;

    val prefValue: String get() = name.lowercase()

    companion object {
        fun fromPrefValue(value: String): ThemeMode =
            entries.firstOrNull { it.prefValue == value.lowercase() } ?: FOLLOW_SYSTEM
    }
}
