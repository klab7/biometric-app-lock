package eu.hxreborn.biometricapplock.hook

import android.app.TaskInfo
import android.content.Intent
import android.content.pm.ActivityInfo
import android.util.Log
import eu.hxreborn.biometricapplock.util.Logger
import io.github.libxposed.api.XposedModule
import java.util.concurrent.ConcurrentHashMap

private val WATCH_PKGS =
    setOf(
        "com.google.android.calculator",
        "com.alibaba.aliexpresshd",
    )

private val watchedTaskIds = ConcurrentHashMap<Int, String>()

internal fun XposedModule.registerSystemServerHooks(cl: ClassLoader) {
    hookActivityStartIntercept(cl)
    hookOnActivityLaunched(cl)
    hookStartActivityFromRecents(cl)
    hookMoveTaskToFrontLocked(cl)
}

private fun XposedModule.hookActivityStartIntercept(cl: ClassLoader) {
    runCatching {
        val klass = cl.loadClass("com.android.server.wm.ActivityStartInterceptor")
        val method =
            klass.declaredMethods.firstOrNull { it.name == "intercept" && it.parameterCount == 11 }
                ?: error("ActivityStartInterceptor.intercept(11-args) not found")
        hook(method).intercept { chain ->
            val args = chain.args
            val info = args.getOrNull(2) as? ActivityInfo
            val pkg = info?.packageName
            if (pkg in WATCH_PKGS) {
                val intent = args.getOrNull(0) as? Intent
                val flagsHex = intent?.flags?.toString(16) ?: "?"
                Logger.log(
                    Log.INFO,
                    "intercept enter pkg=$pkg comp=${info?.name} " +
                        "action=${intent?.action} flags=0x$flagsHex",
                )
            }
            val result = chain.proceed()
            if (pkg in WATCH_PKGS) {
                Logger.log(Log.INFO, "intercept exit pkg=$pkg replaced=$result")
            }
            result
        }
        Logger.log(Log.INFO, "hooked ActivityStartInterceptor.intercept watching=$WATCH_PKGS")
    }.onFailure {
        Logger.log(Log.ERROR, "hookActivityStartIntercept failed: ${it.message}", it)
    }
}

private fun XposedModule.hookOnActivityLaunched(cl: ClassLoader) {
    runCatching {
        val klass = cl.loadClass("com.android.server.wm.ActivityStartInterceptor")
        val method =
            klass.declaredMethods.firstOrNull {
                it.name == "onActivityLaunched" && it.parameterCount == 2
            } ?: error("ActivityStartInterceptor.onActivityLaunched(2-args) not found")
        hook(method).intercept { chain ->
            val taskInfo = chain.args.getOrNull(0) as? TaskInfo
            val pkg = taskInfo?.topActivity?.packageName
            if (pkg in WATCH_PKGS) {
                val tid = taskInfo?.taskId ?: -1
                if (tid >= 0) watchedTaskIds[tid] = pkg!!
                Logger.log(
                    Log.INFO,
                    "onActivityLaunched pkg=$pkg taskId=$tid " +
                        "top=${taskInfo?.topActivity?.shortClassName}",
                )
            }
            chain.proceed()
        }
        Logger.log(Log.INFO, "hooked ActivityStartInterceptor.onActivityLaunched")
    }.onFailure {
        Logger.log(Log.ERROR, "hookOnActivityLaunched failed: ${it.message}", it)
    }
}

private fun XposedModule.hookStartActivityFromRecents(cl: ClassLoader) {
    runCatching {
        val klass = cl.loadClass("com.android.server.wm.ActivityTaskSupervisor")
        val method =
            klass.declaredMethods.firstOrNull {
                it.name == "startActivityFromRecents" && it.parameterCount == 4
            } ?: error("ActivityTaskSupervisor.startActivityFromRecents(4-args) not found")
        hook(method).intercept { chain ->
            val pid = chain.args.getOrNull(0)
            val uid = chain.args.getOrNull(1)
            val taskId = chain.args.getOrNull(2) as? Int
            val pkg = taskId?.let { watchedTaskIds[it] }
            if (pkg != null) {
                Logger.log(
                    Log.INFO,
                    "startActivityFromRecents pkg=$pkg taskId=$taskId pid=$pid uid=$uid",
                )
            }
            chain.proceed()
        }
        Logger.log(Log.INFO, "hooked ActivityTaskSupervisor.startActivityFromRecents")
    }.onFailure {
        Logger.log(Log.ERROR, "hookStartActivityFromRecents failed: ${it.message}", it)
    }
}

private fun XposedModule.hookMoveTaskToFrontLocked(cl: ClassLoader) {
    runCatching {
        val klass = cl.loadClass("com.android.server.wm.ActivityTaskManagerService")
        val method =
            klass.declaredMethods.firstOrNull {
                it.name == "moveTaskToFrontLocked" && it.parameterCount == 5
            } ?: error("ActivityTaskManagerService.moveTaskToFrontLocked(5-args) not found")
        hook(method).intercept { chain ->
            val callingPkg = chain.args.getOrNull(1) as? String
            val taskId = chain.args.getOrNull(2) as? Int
            val targetPkg = taskId?.let { watchedTaskIds[it] }
            if (targetPkg != null) {
                Logger.log(
                    Log.INFO,
                    "moveTaskToFrontLocked targetPkg=$targetPkg taskId=$taskId callingPkg=$callingPkg",
                )
            }
            chain.proceed()
        }
        Logger.log(Log.INFO, "hooked ActivityTaskManagerService.moveTaskToFrontLocked")
    }.onFailure {
        Logger.log(Log.ERROR, "hookMoveTaskToFrontLocked failed: ${it.message}", it)
    }
}
