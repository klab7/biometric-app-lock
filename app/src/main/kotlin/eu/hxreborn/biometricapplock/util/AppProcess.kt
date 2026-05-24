package eu.hxreborn.biometricapplock.util

import android.content.Context
import android.content.Intent
import android.os.Process

// Kill and relaunch to rebind because the XposedServiceHelper binder doesn't survive Activity recreation
fun restartAppProcess(context: Context) {
    val launch =
        context.packageManager
            .getLaunchIntentForPackage(context.packageName)
            ?.apply { addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK) }
    launch?.let { context.startActivity(it) }
    Process.killProcess(Process.myPid())
}
