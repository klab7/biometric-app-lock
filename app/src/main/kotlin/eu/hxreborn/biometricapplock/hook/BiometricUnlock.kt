package eu.hxreborn.biometricapplock.hook

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Handler
import android.os.SystemClock
import eu.hxreborn.biometricapplock.BiometricAuthActivity
import eu.hxreborn.biometricapplock.util.Logger
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

private const val TOKEN_TTL_MS = 2 * 60 * 1000L

private val pendingTokens = ConcurrentHashMap<String, Long>()

private fun mintToken(): String {
    sweepExpiredTokens()
    val token = UUID.randomUUID().toString()
    pendingTokens[token] = SystemClock.elapsedRealtime()
    return token
}

private fun sweepExpiredTokens() {
    val cutoff = SystemClock.elapsedRealtime() - TOKEN_TTL_MS
    val iter = pendingTokens.entries.iterator()
    while (iter.hasNext()) {
        if (iter.next().value < cutoff) iter.remove()
    }
}

private fun consumeToken(token: String): Boolean {
    val issuedAt = pendingTokens.remove(token) ?: return false
    return SystemClock.elapsedRealtime() - issuedAt <= TOKEN_TTL_MS
}

internal fun isValidAuthToken(
    intent: Intent?,
    packageName: String?,
): Boolean {
    val token = intent?.getStringExtra(BiometricAuthActivity.EXTRA_AUTH_TOKEN) ?: return false
    val pkg = packageName ?: return false
    if (pkg !in lockedPackages || !consumeToken(token)) return false
    intent.removeExtra(BiometricAuthActivity.EXTRA_AUTH_TOKEN)
    addUnlocked(pkg)
    Logger.info("unlocked pkg=$pkg")
    return true
}

internal fun tryRedirect(
    interceptor: Any,
    packageName: String,
    className: String,
): Boolean {
    val reflection = reflection ?: return false
    val token = mintToken()

    runCatching { applyRedirect(reflection, interceptor, packageName, className, token) }
        .onFailure {
            pendingTokens.remove(token)
            Logger.error("redirect failed: ${it.message}", it)
            return false
        }

    Logger.info("redirected pkg=$packageName comp=$className")
    return true
}

private fun applyRedirect(
    reflection: SystemServerReflection,
    interceptor: Any,
    targetPackageName: String,
    targetClassName: String,
    token: String,
) {
    val activityTaskSupervisor = reflection.supervisorField.get(interceptor)
    val realPid = reflection.realCallingPidField.getInt(interceptor)
    val realUid = reflection.realCallingUidField.getInt(interceptor)
    val userId = reflection.userIdField.getInt(interceptor)
    val startFlags = reflection.startFlagsField.getInt(interceptor)

    val authIntent = buildAuthIntent(targetPackageName, targetClassName, token)
    val resolvedInfo =
        reflection.resolveIntent.invoke(
            activityTaskSupervisor,
            authIntent,
            null,
            userId,
            0,
            realUid,
            realPid,
        )
    val activityInfo =
        reflection.resolveActivity.invoke(
            activityTaskSupervisor,
            authIntent,
            resolvedInfo,
            startFlags,
            null,
        ) as? ActivityInfo
            ?: error("BiometricAuthActivity not resolvable — PackageManager not ready")

    reflection.intentField.set(interceptor, authIntent)
    reflection.resolvedInfoField.set(interceptor, resolvedInfo)
    reflection.activityInfoField.set(interceptor, activityInfo)
    reflection.callingPidField.setInt(interceptor, realPid)
    reflection.callingUidField.setInt(interceptor, realUid)
    reflection.resolvedTypeField.set(interceptor, null)
}

internal fun postAuthLaunch(
    activityTaskSupervisor: Any,
    entry: TaskEntry,
) {
    val reflection = reflection ?: return
    val activityTaskManagerService =
        reflection.activityTaskManagerServiceField.get(activityTaskSupervisor)
    val handler = reflection.handlerField.get(activityTaskManagerService) as Handler
    val context = reflection.contextField.get(activityTaskManagerService) as Context

    val token = mintToken()
    val intent = buildAuthIntent(entry.packageName, entry.topActivityClassName, token)

    handler.post {
        runCatching { context.startActivity(intent) }
            .onFailure {
                pendingTokens.remove(token)
                Logger.error("posted auth launch failed: ${it.message}", it)
            }
    }
}

private fun buildAuthIntent(
    targetPackageName: String,
    targetClassName: String,
    token: String,
) = Intent().apply {
    component =
        ComponentName(BiometricAuthActivity.MODULE_PACKAGE, BiometricAuthActivity.AUTH_ACTIVITY)
    putExtra(BiometricAuthActivity.EXTRA_TARGET_PKG, targetPackageName)
    putExtra(BiometricAuthActivity.EXTRA_TARGET_CLS, targetClassName)
    putExtra(BiometricAuthActivity.EXTRA_AUTH_TOKEN, token)
    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
