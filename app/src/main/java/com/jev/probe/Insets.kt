package com.jev.probe

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * targetSdk 36 keeps Android 15+ edge-to-edge and Android 16 removes the
 * target-36 opt-out entirely. A plain code-built screen therefore starts under
 * the status/navigation bars. Pad [this] by the system-bar insets *on top of*
 * the padding it already carries.
 *
 * The base padding is captured before the listener is installed and every pass
 * recomputes from that baseline, so repeated inset dispatches (rotation, IME,
 * cutout changes) cannot pile padding up. Horizontal padding is left alone —
 * only top and bottom are at issue here.
 *
 * Applied to the content container of [MainActivity], [SettingsActivity] and
 * [KnowledgeActivity]; nothing in the theme is touched.
 */
fun View.padForSystemBars() {
    val baseTop = paddingTop
    val baseBottom = paddingBottom
    ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
        val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        v.setPadding(v.paddingLeft, baseTop + bars.top, v.paddingRight, baseBottom + bars.bottom)
        insets
    }
    ViewCompat.requestApplyInsets(this)
}
