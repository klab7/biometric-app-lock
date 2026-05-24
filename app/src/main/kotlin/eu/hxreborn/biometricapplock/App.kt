package eu.hxreborn.biometricapplock

import android.app.Application
import android.content.Context
import android.util.Log
import eu.hxreborn.biometricapplock.prefs.AppOverridesRepository
import eu.hxreborn.biometricapplock.prefs.Prefs
import eu.hxreborn.biometricapplock.prefs.PrefsRepository
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import java.util.concurrent.CopyOnWriteArrayList

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        val localPrefs = getSharedPreferences(Prefs.GROUP, Context.MODE_PRIVATE)
        prefsRepository =
            PrefsRepository(localPrefs) {
                runCatching { boundService?.getRemotePreferences(Prefs.GROUP) }.getOrNull()
            }
        appOverridesRepository =
            AppOverridesRepository(localPrefs) {
                runCatching { boundService?.getRemotePreferences(Prefs.GROUP) }.getOrNull()
            }
        XposedServiceHelper.registerListener(
            object : XposedServiceHelper.OnServiceListener {
                override fun onServiceBind(service: XposedService) {
                    Log.i(
                        TAG,
                        "service bound: ${service.frameworkName} v${service.frameworkVersion}",
                    )
                    boundService = service
                    prefsRepository.syncToRemote()
                    listeners.forEach { it.onServiceBind(service) }
                }

                override fun onServiceDied(service: XposedService) {
                    Log.w(TAG, "service died")
                    boundService = null
                    listeners.forEach { it.onServiceDied(service) }
                }
            },
        )
    }

    companion object {
        private const val TAG = "BiometricAppLock"

        @Volatile
        var boundService: XposedService? = null
            private set

        lateinit var prefsRepository: PrefsRepository
            private set

        lateinit var appOverridesRepository: AppOverridesRepository
            private set

        private val listeners = CopyOnWriteArrayList<XposedServiceHelper.OnServiceListener>()

        fun addServiceListener(listener: XposedServiceHelper.OnServiceListener) {
            listeners.add(listener)
            boundService?.let { listener.onServiceBind(it) }
        }

        fun removeServiceListener(listener: XposedServiceHelper.OnServiceListener) {
            listeners.remove(listener)
        }
    }
}
