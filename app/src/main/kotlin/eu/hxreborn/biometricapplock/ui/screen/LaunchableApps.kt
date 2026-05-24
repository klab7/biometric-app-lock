package eu.hxreborn.biometricapplock.ui.screen

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal fun launchableAppsIntent(): Intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)

@SuppressLint("QueryPermissionsNeeded")
internal suspend fun loadLaunchablePackageNames(
    pm: PackageManager,
    ownPackage: String,
): Set<String> =
    withContext(Dispatchers.IO) {
        pm
            .queryIntentActivities(launchableAppsIntent(), 0)
            .asSequence()
            .mapNotNull { it.activityInfo?.applicationInfo?.packageName }
            .filterNot { it == ownPackage }
            .toSet()
    }

@Composable
internal fun rememberLaunchablePackageNames(): Set<String> {
    val context = LocalContext.current
    val pm = context.packageManager
    val ownPackage = context.packageName
    val packageNames by produceState(initialValue = emptySet(), pm, ownPackage) {
        value = loadLaunchablePackageNames(pm, ownPackage)
    }
    return packageNames
}
