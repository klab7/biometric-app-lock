package eu.hxreborn.biometricapplock.ui.util

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import eu.hxreborn.biometricapplock.BuildConfig

object LauncherIconHelper {
    private const val ALIAS_NAME = "${BuildConfig.APPLICATION_ID}.LauncherAlias"

    fun isLauncherIconVisible(context: Context): Boolean {
        val state =
            context.packageManager.getComponentEnabledSetting(
                ComponentName(context, ALIAS_NAME),
            )
        return state != PackageManager.COMPONENT_ENABLED_STATE_DISABLED
    }

    fun setLauncherIconVisible(
        context: Context,
        visible: Boolean,
    ) {
        context.packageManager.setComponentEnabledSetting(
            ComponentName(context, ALIAS_NAME),
            if (visible) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            },
            PackageManager.DONT_KILL_APP,
        )
    }
}
