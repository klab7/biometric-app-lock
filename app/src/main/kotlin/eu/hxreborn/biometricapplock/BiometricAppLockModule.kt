package eu.hxreborn.biometricapplock

import android.os.Process
import android.util.Log
import eu.hxreborn.biometricapplock.hook.AuthState
import eu.hxreborn.biometricapplock.hook.registerActivityHooks
import eu.hxreborn.biometricapplock.util.Logger
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam

@PublishedApi
internal lateinit var module: BiometricAppLockModule
    private set

class BiometricAppLockModule : XposedModule() {
    private val state = AuthState()

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        module = this
        Logger.log(Log.INFO, "loaded in ${param.processName}")
    }

    override fun onPackageReady(param: PackageReadyParam) {
        if (!param.isFirstPackage()) return
        Logger.log(
            Log.INFO,
            "loaded for ${param.packageName} pid=${Process.myPid()}",
        )
        runCatching { registerActivityHooks(param.classLoader, state) }
            .onFailure { Logger.log(Log.ERROR, "registerActivityHooks failed: ${it.message}", it) }
    }
}
