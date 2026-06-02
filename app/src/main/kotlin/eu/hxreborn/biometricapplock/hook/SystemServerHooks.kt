package eu.hxreborn.biometricapplock.hook

import android.app.TaskInfo
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Handler
import android.os.SystemClock
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

internal fun refreshSecureSurfaces() {
    val atms = atmsRef ?: return
    val r = reflection ?: return
    val rwc = r.rootWindowContainerField.get(atms) ?: return
    val handler = r.handlerField.get(atms) as? Handler ?: return
    handler.post {
        runCatching { r.refreshSecureSurfaceState.invoke(rwc) }
            .onFailure { Logger.warn("refreshSecureSurfaceState failed: ${it.message}", it) }
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
            .onFailure { Logger.error("reflection init failed: ${it.message}", it) }
            .getOrNull()

    hookLaunchIntercept(classLoader)
    hookActivityLaunched(classLoader)
    hookRecentsLaunch(classLoader)
    hookScreenAwake(classLoader)
    hookFlagSecure(classLoader)
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
        hook(method).intercept { chain ->
            if (atmsRef == null) {
                atmsRef = captureAtms(chain.thisObject)
                ensurePackageEventsRegistered()
            }

            val intent = chain.args[0] as? Intent
            val activityInfo = chain.args[2] as? ActivityInfo
            val packageName = activityInfo?.packageName

            if (isValidAuthToken(intent, packageName)) return@intercept chain.proceed()

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

// Populates taskCache so hookRecentsLaunch can map taskId to package
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
        Logger.info("hooked onActivityLaunched args=${method.parameterCount}")
    }.onFailure { Logger.error("hookActivityLaunched failed: ${it.message}", it) }
}

// Recents-card taps and gesture-nav switches bypass ActivityStarter
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
                runCatching { postAuthLaunch(chain.thisObject, entry) }
                    .onFailure { Logger.error("recents auth failed: ${it.message}", it) }
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

// Relocks unlocked packages on screen-on once their relock delay has elapsed
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
            if (awake == true && unlockedPackages.isNotEmpty()) {
                val now = SystemClock.elapsedRealtime()
                val toRelock =
                    unlockedPackages.filter { shouldRelockOnTransition(it, now) }.toSet()
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

// Force-blocks screenshots in unlocked locked apps when BLOCK_SCREENSHOTS is on
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
            if (pkg in lockedPackages &&
                isUnlocked(pkg) &&
                shouldBlockScreenshots(pkg)
            ) {
                Logger.debug { "flagsecure force-block pkg=$pkg" }
                return@intercept true
            }
            chain.proceed()
        }
        Logger.info("hooked isSecureLocked args=${method.parameterCount}")
    }.onFailure { Logger.warn("hookFlagSecure not available: ${it.message}") }
}
