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

// keep the exact launch we gated so auth resumes it instead of a rebuilt one
private val pendingLaunches = ConcurrentHashMap<String, Intent>()

private fun mintToken(): String {
    sweepExpiredTokens()
    val token = UUID.randomUUID().toString()
    pendingTokens[token] = SystemClock.elapsedRealtime()
    return token
}

private fun sweepExpiredTokens() {
    val cutoff = SystemClock.elapsedRealtime() - TOKEN_TTL_MS
    pendingTokens.entries.removeIf { it.value < cutoff }
    pendingLaunches.keys.retainAll(pendingTokens.keys)
}

private fun discardToken(token: String) {
    pendingTokens.remove(token)
    pendingLaunches.remove(token)
}

private fun consumeToken(token: String): Boolean {
    val issuedAt = pendingTokens.remove(token) ?: return false
    return SystemClock.elapsedRealtime() - issuedAt <= TOKEN_TTL_MS
}

internal fun takePendingLaunch(token: String): Intent? = pendingLaunches.remove(token)

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
            discardToken(token)
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
    val originalIntent =
        runCatching {
            reflection.intentField.get(
                interceptor,
            ) as? Intent
        }.getOrNull()
    val activityTaskSupervisor = reflection.supervisorField.get(interceptor)
    val realPid = reflection.realCallingPidField.getInt(interceptor)
    val realUid = reflection.realCallingUidField.getInt(interceptor)
    val userId = reflection.userIdField.getInt(interceptor)
    val startFlags = reflection.startFlagsField.getInt(interceptor)

    val authIntent = buildAuthIntent(targetPackageName, targetClassName, token)

    originalIntent?.let { pendingLaunches[token] = Intent(it) }
    val resolveArgs =
        if (reflection.resolveIntent.parameterCount >= 6) {
            // A14+ (API 34+) takes a trailing callingPid arg
            arrayOf(authIntent, null, userId, 0, realUid, realPid)
        } else {
            // A13 (API 33) has no callingPid arg
            arrayOf(authIntent, null, userId, 0, realUid)
        }
    val resolvedInfo = reflection.resolveIntent.invoke(activityTaskSupervisor, *resolveArgs)
    val activityInfo =
        reflection.resolveActivity.invoke(
            activityTaskSupervisor,
            authIntent,
            resolvedInfo,
            startFlags,
            null,
        ) as? ActivityInfo
            ?: error("BiometricAuthActivity not resolvable: PackageManager not ready")

    reflection.intentField.set(interceptor, authIntent)
    reflection.resolvedInfoField.set(interceptor, resolvedInfo)
    reflection.activityInfoField.set(interceptor, activityInfo)
    reflection.callingPidField.setInt(interceptor, realPid)
    reflection.callingUidField.setInt(interceptor, realUid)
    reflection.resolvedTypeField.set(interceptor, null)
}

// system_server can start non-exported targets and launch without the app-process restrictions
internal fun resumeOriginalLaunch(original: Intent) {
    val reflection = reflection ?: return
    val atms = atmsRef ?: return
    val handler = reflection.handlerField.get(atms) as? Handler ?: return
    val context = reflection.contextField.get(atms) as? Context ?: return
    val launch = Intent(original).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
    handler.post {
        runCatching { context.startActivity(launch) }
            .onFailure { Logger.error("resume launch failed: ${it.message}", it) }
    }
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
    // cached top often isn't exported, launcher intent always is
    val intent = buildAuthIntent(entry.packageName, null, token)

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
    targetClassName: String?,
    token: String,
) = Intent().apply {
    component =
        ComponentName(BiometricAuthActivity.MODULE_PACKAGE, BiometricAuthActivity.AUTH_ACTIVITY)
    putExtra(BiometricAuthActivity.EXTRA_TARGET_PKG, targetPackageName)
    if (targetClassName != null) putExtra(BiometricAuthActivity.EXTRA_TARGET_CLS, targetClassName)
    putExtra(BiometricAuthActivity.EXTRA_AUTH_TOKEN, token)
    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
