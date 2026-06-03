package eu.hxreborn.biometricapplock.hook

import android.app.TaskInfo
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Handler
import android.os.SystemClock
import android.widget.Toast
import eu.hxreborn.biometricapplock.BiometricAuthActivity
import eu.hxreborn.biometricapplock.R
import eu.hxreborn.biometricapplock.receiver.registerPackageEvents
import eu.hxreborn.biometricapplock.util.Logger
import io.github.libxposed.api.XposedModule
import java.util.concurrent.atomic.AtomicBoolean

@Volatile
internal var atmsRef: Any? = null

private val packageEventsRegistered = AtomicBoolean(false)

private fun captureAtms(interceptor: Any): Any? =
    runCatching {
        val r = reflection ?: return null
        val sup = r.supervisorField.get(interceptor) ?: return null
        r.activityTaskManagerServiceField.get(sup)
    }.getOrNull()

private fun ensurePackageEventsRegistered() {
    val atms = atmsRef ?: return
    if (!packageEventsRegistered.compareAndSet(false, true)) return
    val r = reflection ?: return
    val ctx = r.contextField.get(atms) as? Context ?: return
    val handler = r.handlerField.get(atms) as? Handler ?: return
    registerPackageEvents(ctx, handler)
}

// show the toast on the ATMS handler thread because deletePackageX holds the PMS lock
private fun postUninstallBlockedToast() {
    val atms = atmsRef ?: return
    val r = reflection ?: return
    val handler = r.handlerField.get(atms) as? Handler ?: return
    val ctx = r.contextField.get(atms) as? Context ?: return
    handler.post {
        runCatching {
            val pkgCtx = ctx.createPackageContext(BiometricAuthActivity.MODULE_PACKAGE, 0)
            val message = pkgCtx.getString(R.string.uninstall_blocked_toast)
            Toast.makeText(pkgCtx, message, Toast.LENGTH_LONG).show()
        }.onFailure { Logger.warn("uninstall toast failed: ${it.message}") }
    }
}

internal fun refreshSecureSurfaces() {
    val atms = atmsRef ?: return
    val r = reflection ?: return
    val rwc = r.rootWindowContainerField.get(atms) ?: return
    val handler = r.handlerField.get(atms) as? Handler ?: return
    handler.post {
        runCatching { r.refreshSecureSurfaceState.invoke(rwc) }.onFailure {
            Logger.warn(
                "refreshSecureSurfaceState failed: ${it.message}",
                it,
            )
        }
    }
}

internal fun XposedModule.registerSystemServerHooks(
    classLoader: ClassLoader,
    locked: Set<String>,
) {
    lockedPackages = locked
    Logger.info("registering system_server hooks sdk=${Build.VERSION.SDK_INT}")
    reflection =
        runCatching { SystemServerReflection(classLoader) }
            .onFailure {
                Logger.error(
                    "reflection init failed: ${it.message}",
                    it,
                )
            }.getOrNull()

    hookLaunchIntercept(classLoader)
    hookActivityLaunched(classLoader)
    hookRecentsLaunch(classLoader)
    hookTaskRemoved(classLoader)
    hookScreenAwake(classLoader)
    hookFlagSecure(classLoader)
    hookUninstall(classLoader)
}

// every user-initiated uninstall (launcher, Settings, Play Store, adb) funnels through deletePackageX
private fun XposedModule.hookUninstall(classLoader: ClassLoader) {
    runCatching {
        val method =
            classLoader.findMethod(
                "com.android.server.pm.DeletePackageHelper",
                "deletePackageX",
                5,
            )
        hook(method).intercept { chain ->
            // fail open so arg drift lets the delete run instead of throwing in system_server
            val shouldBlock =
                runCatching {
                    val packageName = chain.args.getOrNull(0) as? String
                    val removedBySystem = chain.args.getOrNull(4) as? Boolean
                    packageName == BiometricAuthActivity.MODULE_PACKAGE &&
                        removedBySystem == false &&
                        shouldPreventModuleUninstall()
                }.getOrDefault(false)

            if (shouldBlock) {
                Logger.info("blocked uninstall pkg=${BiometricAuthActivity.MODULE_PACKAGE}")
                postUninstallBlockedToast()
                // DELETE_FAILED_INTERNAL_ERROR aborts the deletion without running it
                return@intercept -1
            }
            chain.proceed()
        }
        Logger.info("hooked deletePackageX args=${method.parameterCount}")
    }.onFailure { Logger.warn("hookUninstall not available: ${it.message}") }
}

// ActivityStarter routes all launches through here
private fun XposedModule.hookLaunchIntercept(classLoader: ClassLoader) {
    runCatching {
        val method =
            classLoader.findMethod(
                "com.android.server.wm.ActivityStartInterceptor",
                "intercept",
                11,
            )
        // resolve indices once at install time so a future arg shift can't desync the hot path
        val intentIdx = method.firstArgIndexOfType("Intent").let { if (it >= 0) it else 0 }
        val actInfoIdx = method.firstArgIndexOfType("ActivityInfo").let { if (it >= 0) it else 2 }
        Logger.info(
            "intercept indices intent=$intentIdx aInfo=$actInfoIdx args=${method.parameterCount}",
        )
        hook(method).intercept { chain ->
            if (atmsRef == null) {
                atmsRef = captureAtms(chain.thisObject)
                ensurePackageEventsRegistered()
            }

            val intent = chain.args[intentIdx] as? Intent
            val activityInfo = chain.args[actInfoIdx] as? ActivityInfo
            val packageName = activityInfo?.packageName

            val auth = resolveAuthToken(intent, packageName)
            if (auth != null) {
                val original = auth.launch
                if (original != null) {
                    Logger.debug { "resume original pkg=$packageName" }
                    resumeOriginalLaunch(original)
                    return@intercept true
                }
                return@intercept chain.proceed()
            }

            relockOtherPackages(packageName)

            val result = chain.proceed()
            if (result == true) return@intercept true
            if (packageName == null || packageName !in lockedPackages) return@intercept false
            if (intent?.hasCategory(Intent.CATEGORY_HOME) == true) return@intercept false
            if (isUnlocked(packageName)) {
                refreshUnlock(packageName)
                Logger.debug { "intercept pass pkg=$packageName comp=${activityInfo.name}" }
                return@intercept false
            }

            Logger.debug {
                "intercept gating pkg=$packageName comp=${activityInfo.name} action=${intent?.action}"
            }
            tryRedirect(chain.thisObject, packageName, activityInfo.name)
        }
        Logger.info("hooked intercept args=${method.parameterCount}")
    }.onFailure { Logger.error("hookLaunchIntercept failed: ${it.message}", it) }
}

// populates taskCache so the recents and task-removed hooks can map taskId to package
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
                taskCache[taskInfo.taskId] = TaskEntry(packageName)
                Logger.debug {
                    "launched pkg=$packageName taskId=${taskInfo.taskId} top=${topActivity.shortClassName}"
                }
            }
            chain.proceed()
        }
        Logger.info("hooked onActivityLaunched args=${method.parameterCount}")
    }.onFailure { Logger.error("hookActivityLaunched failed: ${it.message}", it) }
}

// intercepts recents-card taps and gesture switches, which bypass ActivityStarter
private fun XposedModule.hookRecentsLaunch(classLoader: ClassLoader) {
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

            if (entry != null && !isUnlocked(entry.packageName)) {
                Logger.debug { "recents gate pkg=${entry.packageName} taskId=$taskId" }
                val result = chain.proceed()
                runCatching {
                    postAuthLaunch(
                        chain.thisObject,
                        entry,
                    )
                }.onFailure { Logger.error("recents auth failed: ${it.message}", it) }
                return@intercept result
            }
            if (entry != null) {
                refreshUnlock(entry.packageName)
                Logger.debug { "recents pass pkg=${entry.packageName} taskId=$taskId" }
            }
            chain.proceed()
        }
        Logger.info("hooked startActivityFromRecents args=${method.parameterCount}")
    }.onFailure { Logger.error("hookRecentsLaunch failed: ${it.message}", it) }
}

// relock a locked task when it is swiped off recents through the cleanUpRemovedTask hook
private fun XposedModule.hookTaskRemoved(classLoader: ClassLoader) {
    runCatching {
        val supervisorClass =
            classLoader.anyClassFromNames(
                "com.android.server.wm.ActivityTaskSupervisor",
                "com.android.server.wm.ActivityStackSupervisor",
            )
        // both removal-method names are tried so the hook survives the A13 to A14 rename
        val method =
            supervisorClass.declaredMethods.firstOrNull {
                it.name == "cleanUpRemovedTask" || it.name == "cleanUpRemovedTaskLocked"
            } ?: error("cleanUpRemovedTask not found")
        val taskIdField =
            classLoader
                .loadClass("com.android.server.wm.Task")
                .getDeclaredField("mTaskId")
                .apply { isAccessible = true }
        hook(method).intercept { chain ->
            val result = chain.proceed()
            // runs after proceed and lock-free to stay safe under mGlobalLock
            runCatching {
                if (!shouldRelockOnTaskRemoved()) return@runCatching
                val taskId = chain.args.getOrNull(0)?.let { taskIdField.getInt(it) }
                val entry = taskId?.let { taskCache.remove(it) } ?: return@runCatching
                removeFromUnlocked(setOf(entry.packageName))
                Logger.debug { "task removed relock pkg=${entry.packageName} taskId=$taskId" }
            }
            result
        }
        Logger.info("hooked ${method.name} args=${method.parameterCount}")
    }.onFailure {
        Logger.warn("hookTaskRemoved unavailable (cleanUpRemovedTask/mTaskId): ${it.message}")
    }
}

// clears unlock state on screen off, relocks elapsed packages on wake
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
                // screen off drops all unlock state so locked apps re-auth on next launch
                if (shouldRelockOnScreenOff() && unlockedPackages.isNotEmpty()) {
                    val cleared = unlockedPackages.size
                    clearUnlocked()
                    Logger.debug { "screen off relock cleared=$cleared" }
                }
                return@intercept chain.proceed()
            }
            if (awake == true && unlockedPackages.isNotEmpty()) {
                // screen on relocks only packages whose delay has elapsed
                val now = SystemClock.elapsedRealtime()
                val toRelock = unlockedPackages.filter { shouldRelockOnTransition(it, now) }.toSet()
                if (toRelock.isNotEmpty()) removeFromUnlocked(toRelock)
                Logger.debug {
                    val topPkg =
                        runCatching {
                            reflection?.findTopResumedPackageName(chain.thisObject)
                        }.getOrNull()
                    "screen on relocked=${toRelock.size} topPkg=$topPkg"
                }
            }
            chain.proceed()
        }
        Logger.info("hooked onScreenAwakeChanged args=${method.parameterCount}")
    }.onFailure { Logger.error("hookScreenAwake failed: ${it.message}", it) }
}

// force-blocks screenshots in unlocked locked apps when BLOCK_SCREENSHOTS is on
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
            if (pkg in lockedPackages && isUnlocked(pkg) && shouldBlockScreenshots(pkg)) {
                Logger.debug { "flagsecure force-block pkg=$pkg" }
                return@intercept true
            }
            chain.proceed()
        }
        Logger.info("hooked isSecureLocked args=${method.parameterCount}")
    }.onFailure { Logger.warn("hookFlagSecure not available: ${it.message}") }
}
