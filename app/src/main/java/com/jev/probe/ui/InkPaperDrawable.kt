package com.jev.probe.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.drawable.Drawable
import kotlin.math.max

/**
 * Lightweight procedural xuan-paper / ink-wash background.
 *
 * It deliberately uses only low-alpha geometric washes so it remains legible,
 * cheap to draw and independent of bundled bitmap assets.
 */
class InkPaperDrawable : Drawable() {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()

    override fun draw(canvas: Canvas) {
        val b: Rect = bounds
        if (b.width() <= 0 || b.height() <= 0) return

        val w = b.width().toFloat()
        val h = b.height().toFloat()

        canvas.drawColor(Guofeng.PAPER)

        // Warm paper wash.
        paint.shader = LinearGradient(
            0f, 0f, 0f, h,
            intArrayOf(
                Color.rgb(250, 247, 239),
                Guofeng.PAPER,
                Color.rgb(241, 236, 224)
            ),
            floatArrayOf(0f, 0.48f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = null

        // Pale sun / seal wash behind the header.
        paint.color = Color.argb(18, 167, 82, 63)
        canvas.drawCircle(w * 0.77f, h * 0.075f, max(w, h) * 0.045f, paint)

        // Far mountains.
        paint.color = Color.argb(20, 40, 83, 74)
        path.reset()
        path.moveTo(w * 0.34f, h * 0.19f)
        path.lineTo(w * 0.49f, h * 0.10f)
        path.lineTo(w * 0.58f, h * 0.16f)
        path.lineTo(w * 0.69f, h * 0.075f)
        path.lineTo(w * 0.82f, h * 0.17f)
        path.lineTo(w, h * 0.095f)
        path.lineTo(w, h * 0.24f)
        path.lineTo(w * 0.34f, h * 0.24f)
        path.close()
        canvas.drawPath(path, paint)

        // Near mountain wash.
        paint.color = Color.argb(28, 28, 74, 64)
        path.reset()
        path.moveTo(w * 0.46f, h * 0.23f)
        path.lineTo(w * 0.61f, h * 0.13f)
        path.lineTo(w * 0.70f, h * 0.20f)
        path.lineTo(w * 0.80f, h * 0.115f)
        path.lineTo(w * 0.91f, h * 0.205f)
        path.lineTo(w, h * 0.16f)
        path.lineTo(w, h * 0.27f)
        path.lineTo(w * 0.46f, h * 0.27f)
        path.close()
        canvas.drawPath(path, paint)

        // Mist bands.
        paint.strokeWidth = max(1f, w * 0.0018f)
        paint.style = Paint.Style.STROKE
        paint.color = Color.argb(20, 86, 111, 100)
        canvas.drawLine(w * 0.42f, h * 0.205f, w * 0.96f, h * 0.205f, paint)
        canvas.drawLine(w * 0.50f, h * 0.225f, w * 0.88f, h * 0.225f, paint)

        // Bamboo hint at the lower-left edge.
        paint.color = Color.argb(23, 37, 91, 68)
        paint.strokeWidth = max(1.5f, w * 0.004f)
        canvas.drawLine(w * 0.035f, h * 0.78f, w * 0.005f, h, paint)
        canvas.drawLine(w * 0.075f, h * 0.83f, w * 0.045f, h, paint)

        paint.style = Paint.Style.FILL
        fun leaf(cx: Float, cy: Float, sx: Float, sy: Float) {
            path.reset()
            path.moveTo(cx - sx, cy)
            path.quadTo(cx, cy - sy, cx + sx, cy)
            path.quadTo(cx, cy + sy, cx - sx, cy)
            path.close()
            canvas.drawPath(path, paint)
        }
        leaf(w * 0.035f, h * 0.83f, w * 0.025f, h * 0.006f)
        leaf(w * 0.055f, h * 0.87f, w * 0.027f, h * 0.007f)
        leaf(w * 0.025f, h * 0.91f, w * 0.024f, h * 0.006f)
        leaf(w * 0.082f, h * 0.89f, w * 0.024f, h * 0.006f)

        // Subtle deterministic paper flecks; no random state or bitmap texture.
        paint.color = Color.argb(9, 92, 77, 55)
        val cols = 8
        val rows = 18
        for (iy in 0 until rows) {
            for (ix in 0 until cols) {
                val x = (ix + 0.37f + (iy % 3) * 0.11f) / cols * w
                val y = (iy + 0.41f + (ix % 2) * 0.13f) / rows * h
                canvas.drawCircle(x, y, 0.75f + ((ix + iy) % 3) * 0.28f, paint)
            }
        }
    }

    override fun setAlpha(alpha: Int) {
        // Individual washes own their alpha; external alpha is intentionally ignored.
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
    }

    override fun getOpacity(): Int = PixelFormat.OPAQUE
}
