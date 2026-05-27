package eu.hxreborn.biometricapplock.hook

import android.app.TaskInfo
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.SystemClock
import eu.hxreborn.biometricapplock.util.Logger
import io.github.libxposed.api.XposedModule

internal fun XposedModule.registerSystemServerHooks(
    classLoader: ClassLoader,
    locked: Set<String>,
) {
    lockedPackages = locked
    reflection =
        runCatching { SystemServerReflection(classLoader) }
            .onFailure { Logger.error("reflection init failed: ${it.message}", it) }
            .getOrNull()

    hookLaunchIntercept(classLoader)
    hookActivityLaunched(classLoader)
    hookRecents(classLoader)
    hookScreenAwake(classLoader)
    hookSnapshotProtection(classLoader)
    hookFlagSecure(classLoader)
}

private fun XposedModule.hookLaunchIntercept(classLoader: ClassLoader) {
    runCatching {
        val method =
            classLoader.findMethod(
                "com.android.server.wm.ActivityStartInterceptor",
                "intercept",
                11,
            )
        hook(method).intercept { chain ->
            val intent = chain.args[0] as? Intent
            val activityInfo = chain.args[2] as? ActivityInfo
            val packageName = activityInfo?.packageName

            if (isValidAuthToken(intent, packageName)) return@intercept chain.proceed()

            relockOtherPackages(packageName)

            val result = chain.proceed()
            if (result == true) return@intercept true
            if (packageName == null || packageName !in lockedPackages) return@intercept false
            if (intent?.hasCategory(Intent.CATEGORY_HOME) == true) return@intercept false
            if (packageName in unlockedPackages) {
                Logger.debug { "intercept pass pkg=$packageName comp=${activityInfo.name}" }
                return@intercept false
            }

            Logger.debug {
                "intercept gating pkg=$packageName comp=${activityInfo.name} action=${intent?.action}"
            }
            tryRedirect(chain.thisObject, packageName, activityInfo.name)
        }
        Logger.info("hooked intercept")
    }.onFailure { Logger.error("hookLaunchIntercept failed: ${it.message}", it) }
}

private fun XposedModule.hookActivityLaunched(classLoader: ClassLoader) {
    runCatching {
        val method =
            classLoader.findMethod(
                "com.android.server.wm.ActivityStartInterceptor",
                "onActivityLaunched",
                2,
            )
        hook(method).intercept { chain ->
            val taskInfo = chain.args[0] as? TaskInfo ?: return@intercept chain.proceed()
            val topActivity = taskInfo.topActivity ?: return@intercept chain.proceed()
            val packageName = topActivity.packageName
            if (packageName in lockedPackages) {
                taskCache[taskInfo.taskId] = TaskEntry(packageName, topActivity.className)
                Logger.debug {
                    "launched pkg=$packageName taskId=${taskInfo.taskId} top=${topActivity.shortClassName}"
                }
            }
            chain.proceed()
        }
        Logger.info("hooked onActivityLaunched")
    }.onFailure { Logger.error("hookActivityLaunched failed: ${it.message}", it) }
}

private fun XposedModule.hookRecents(classLoader: ClassLoader) {
    runCatching {
        val method =
            classLoader.findMethod(
                "com.android.server.wm.ActivityTaskSupervisor",
                "startActivityFromRecents",
                4,
            )
        hook(method).intercept { chain ->
            val taskId = chain.args[2] as? Int
            val entry = taskId?.let { taskCache[it] }

            relockOtherPackages(entry?.packageName)

            if (entry != null && entry.packageName !in unlockedPackages) {
                Logger.debug { "recents gate pkg=${entry.packageName} taskId=$taskId" }
                val result = chain.proceed()
                runCatching { postAuthLaunch(chain.thisObject, entry) }
                    .onFailure { Logger.error("recents auth failed: ${it.message}", it) }
                return@intercept result
            }
            if (entry != null) {
                Logger.debug { "recents pass pkg=${entry.packageName} taskId=$taskId" }
            }
            chain.proceed()
        }
        Logger.info("hooked startActivityFromRecents")
    }.onFailure { Logger.error("hookRecents failed: ${it.message}", it) }
}

private fun XposedModule.hookScreenAwake(classLoader: ClassLoader) {
    runCatching {
        val method =
            classLoader.findMethod(
                "com.android.server.wm.ActivityTaskManagerService",
                "onScreenAwakeChanged",
                1,
            )
        hook(method).intercept { chain ->
            val awake = chain.args[0] as? Boolean
            if (awake == false) {
                screenOffElapsed = SystemClock.elapsedRealtime()
                return@intercept chain.proceed()
            }
            if (awake == true && unlockedPackages.isNotEmpty()) {
                val offMs =
                    if (screenOffElapsed == Long.MIN_VALUE) {
                        Long.MAX_VALUE
                    } else {
                        SystemClock.elapsedRealtime() - screenOffElapsed
                    }
                val toRelock =
                    unlockedPackages
                        .filter { pkg ->
                            val delay = getEffectiveRelockDelay(pkg)
                            delay != RELOCK_DELAY_NEVER && offMs >= delay * 1000L
                        }.toSet()
                if (toRelock.isNotEmpty()) removeFromUnlocked(toRelock)
                Logger.debug {
                    val topPkg =
                        runCatching {
                            reflection?.findTopResumedPackageName(chain.thisObject)
                        }.getOrNull()
                    "screen on offMs=$offMs relocked=${toRelock.size} topPkg=$topPkg"
                }
            }
            chain.proceed()
        }
        Logger.info("hooked onScreenAwakeChanged")
    }.onFailure { Logger.error("hookScreenAwake failed: ${it.message}", it) }
}

private fun XposedModule.hookSnapshotProtection(classLoader: ClassLoader) {
    runCatching {
        val method =
            classLoader.findMethod(
                "com.android.server.wm.ActivityRecord",
                "shouldUseAppThemeSnapshot",
                0,
            )
        val packageNameField =
            reflection?.activityRecordPackageNameField ?: error("reflection not ready")

        hook(method).intercept { chain ->
            val pkg = packageNameField.get(chain.thisObject) as? String
            if (pkg != null && pkg in lockedPackages) {
                if (isRecentsPreviewEnabled(pkg) && pkg in unlockedPackages) {
                    return@intercept chain.proceed()
                }
                return@intercept true
            }
            chain.proceed()
        }
        Logger.info("hooked shouldUseAppThemeSnapshot")
    }.onFailure {
        Logger.error("hookSnapshotProtection failed: ${it.message}", it)
    }
}

private fun XposedModule.hookFlagSecure(classLoader: ClassLoader) {
    runCatching {
        val method =
            classLoader.findMethod(
                "com.android.server.wm.WindowState",
                "isSecureLocked",
                0,
            )
        val windowStateClass = classLoader.loadClass("com.android.server.wm.WindowState")
        val activityRecordField =
            windowStateClass.getDeclaredField("mActivityRecord").apply { isAccessible = true }
        val packageNameField =
            reflection?.activityRecordPackageNameField ?: error("reflection not ready")
        hook(method).intercept { chain ->
            val ar = activityRecordField.get(chain.thisObject) ?: return@intercept chain.proceed()
            val pkg = packageNameField.get(ar) as? String ?: return@intercept chain.proceed()
            if (pkg in unlockedPackages && isFlagSecureDisabled(pkg)) return@intercept false
            chain.proceed()
        }
        Logger.info("hooked isSecureLocked")
    }.onFailure { Logger.warn("hookFlagSecure not available: ${it.message}") }
}
