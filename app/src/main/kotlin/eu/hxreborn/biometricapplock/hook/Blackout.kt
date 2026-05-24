package eu.hxreborn.biometricapplock.hook

import android.app.Activity
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import eu.hxreborn.biometricapplock.util.Logger

internal const val OVERLAY_ID = 0x7F0BA101

internal fun applyBlackout(activity: Activity) {
    if (activity.window?.decorView?.findViewById<View>(OVERLAY_ID) != null) return
    runCatching {
        activity.addContentView(
            FrameLayout(activity).apply {
                id = OVERLAY_ID
                setBackgroundColor(Color.BLACK)
                isClickable = true
                isFocusable = true
            },
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
    }.onFailure {
        Logger.debug { "applyBlackout failed on ${activity.localClassName}: ${it.message}" }
    }
}

internal fun releaseBlackout(activity: Activity) {
    runCatching {
        val overlay = activity.window?.decorView?.findViewById<View>(OVERLAY_ID) ?: return
        (overlay.parent as? ViewGroup)?.removeView(overlay)
    }.onFailure {
        Logger.debug { "releaseBlackout failed on ${activity.localClassName}: ${it.message}" }
    }
}
