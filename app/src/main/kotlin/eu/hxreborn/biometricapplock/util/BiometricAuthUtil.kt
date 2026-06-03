package eu.hxreborn.biometricapplock.util

import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricManager.Authenticators

fun pickAuthenticators(bm: BiometricManager): Int? {
    val strongAndCred = Authenticators.BIOMETRIC_STRONG or Authenticators.DEVICE_CREDENTIAL
    val weak = Authenticators.BIOMETRIC_WEAK
    val cred = Authenticators.DEVICE_CREDENTIAL
    return when (BiometricManager.BIOMETRIC_SUCCESS) {
        bm.canAuthenticate(strongAndCred) -> strongAndCred
        bm.canAuthenticate(weak) -> weak
        bm.canAuthenticate(cred) -> cred
        else -> null
    }
}
