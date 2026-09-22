package com.jev.probe.ui

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.WindowInsetsController
import android.content.Context

/**
 * Shared visual language for the fork's Chinese ink / paper UI.
 *
 * The palette intentionally avoids pure white / black and saturated blue so
 * every surface reads as one system: warm xuan-paper, dark jade, cinnabar seal,
 * aged-gold accents and muted ink text.
 */
object Guofeng {
    val PAPER = Color.rgb(246, 242, 232)
    val PAPER_DEEP = Color.rgb(238, 232, 218)
    val CARD = Color.rgb(251, 249, 243)
    val CARD_SOFT = Color.rgb(244, 240, 229)

    val INK = Color.rgb(34, 45, 42)
    val INK_SOFT = Color.rgb(92, 101, 96)
    val INK_FAINT = Color.rgb(137, 139, 130)

    val JADE = Color.rgb(39, 103, 89)
    val JADE_DEEP = Color.rgb(24, 73, 64)
    val JADE_SOFT = Color.rgb(222, 236, 227)
    val JADE_PALE = Color.rgb(233, 242, 235)

    val CINNABAR = Color.rgb(164, 55, 48)
    val CINNABAR_SOFT = Color.rgb(244, 226, 220)
    val GOLD = Color.rgb(151, 111, 62)
    val GOLD_SOFT = Color.rgb(239, 230, 213)

    val BORDER = Color.rgb(213, 204, 187)
    val BORDER_JADE = Color.rgb(177, 201, 188)

    val SUCCESS = Color.rgb(46, 120, 88)
    val WARNING = Color.rgb(170, 112, 46)
    val DANGER = Color.rgb(166, 55, 49)

    fun dp(context: Context, value: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        value.toFloat(),
        context.resources.displayMetrics
    ).toInt()

    fun round(
        context: Context,
        radiusDp: Int,
        fill: Int,
        stroke: Int? = null,
        strokeWidthDp: Int = 1
    ): GradientDrawable = GradientDrawable().apply {
        cornerRadius = dp(context, radiusDp).toFloat()
        setColor(fill)
        if (stroke != null) setStroke(dp(context, strokeWidthDp), stroke)
    }

    fun serif(bold: Boolean = false): Typeface =
        Typeface.create(Typeface.SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)

    fun sans(bold: Boolean = false): Typeface =
        Typeface.create(Typeface.SANS_SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)

    fun applyWindow(activity: Activity) {
        activity.window.decorView.background = InkPaperDrawable()
        activity.window.statusBarColor = PAPER
        activity.window.navigationBarColor = PAPER
        activity.window.insetsController?.setSystemBarsAppearance(
            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
        )
    }
}
