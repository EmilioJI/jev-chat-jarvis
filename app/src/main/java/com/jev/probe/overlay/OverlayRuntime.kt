package com.jev.probe.overlay

import android.content.Context

/**
 * One process-wide overlay controller.
 *
 * The floating UI belongs to the foreground assistant service, not to
 * AccessibilityService. Accessibility, notification capture and clipboard
 * import can all reuse the same visible surface without creating duplicate
 * windows.
 */
object OverlayRuntime {
    @Volatile private var controller: OverlayController? = null

    @Synchronized
    fun get(context: Context): OverlayController {
        controller?.let { return it }
        return OverlayController(context.applicationContext).also { controller = it }
    }

    fun peek(): OverlayController? = controller

    @Synchronized
    fun release() {
        controller?.hide()
        controller = null
    }
}
