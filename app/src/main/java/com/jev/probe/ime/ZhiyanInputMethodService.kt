package com.jev.probe.ime

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.jev.probe.ui.Guofeng
import kotlin.math.roundToInt

/**
 * Optional one-shot IME for inserting an already-generated candidate through
 * Android's standard InputConnection.
 *
 * It does not inspect the target app package, view ids or UI hierarchy. The user
 * must explicitly enable/select this IME through Android settings.
 */
class ZhiyanInputMethodService : InputMethodService() {

    private var candidateBox: LinearLayout? = null

    private fun dp(v: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        v.toFloat(),
        resources.displayMetrics
    ).roundToInt()

    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onCreateInputView(): View {
        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = GradientDrawable().apply {
                setColor(Guofeng.CARD)
                setStroke(dp(1), Guofeng.BORDER_JADE)
            }
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(TextView(this).apply {
            text = "小书童·快捷填入"
            textSize = 15f
            setTextColor(Guofeng.JADE_DEEP)
            typeface = Guofeng.serif(true)
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
        })
        header.addView(action("返回原键盘") {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                if (!switchToPreviousInputMethod()) switchToNextInputMethod(false)
            } else {
                requestHideSelf(0)
            }
        })
        outer.addView(header)

        outer.addView(TextView(this).apply {
            text = "点候选即写入当前文本框；发送仍由你完成"
            textSize = 11.5f
            setTextColor(Guofeng.INK_SOFT)
            setPadding(0, dp(4), 0, dp(8))
        })

        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        candidateBox = box
        outer.addView(box)
        renderCandidates()
        return outer
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        val armed = ReplyHandoff.consumeArmed()
        if (!armed.isNullOrBlank()) {
            commitCandidate(armed)
            return
        }
        renderCandidates()
    }

    private fun renderCandidates() {
        val box = candidateBox ?: return
        box.removeAllViews()
        val replies = ReplyHandoff.current()
        if (replies.isEmpty()) {
            box.addView(TextView(this).apply {
                text = "暂无候选回复。先在小书童悬浮窗完成一次分析。"
                textSize = 13f
                setTextColor(Guofeng.INK_SOFT)
                setPadding(dp(8), dp(12), dp(8), dp(12))
            })
            return
        }

        replies.forEachIndexed { index, reply ->
            box.addView(TextView(this).apply {
                text = (index + 1).toString() + ". " + reply.text
                textSize = 14f
                setTextColor(Guofeng.INK)
                typeface = Typeface.DEFAULT
                setPadding(dp(10), dp(10), dp(10), dp(10))
                background = GradientDrawable().apply {
                    cornerRadius = dp(12).toFloat()
                    setColor(if (index == 0) Guofeng.JADE_PALE else Guofeng.CARD_SOFT)
                    setStroke(dp(1), Guofeng.BORDER)
                }
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(5) }
                setOnClickListener { commitCandidate(reply.text) }
            })
        }
    }

    private fun commitCandidate(text: String) {
        val ic = currentInputConnection
        if (ic == null) {
            Toast.makeText(this, "当前没有可写入的文本框", Toast.LENGTH_SHORT).show()
            return
        }

        val ok = runCatching {
            ic.finishComposingText()
            ic.commitText(text, 1)
        }.isSuccess
        if (!ok) {
            Toast.makeText(this, "写入失败，请改用复制", Toast.LENGTH_SHORT).show()
            return
        }

        ReplyHandoff.clear()
        Toast.makeText(this, "已填入，发送仍由你完成", Toast.LENGTH_SHORT).show()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            if (!switchToPreviousInputMethod()) requestHideSelf(0)
        } else {
            requestHideSelf(0)
        }
    }

    private fun action(label: String, onClick: () -> Unit): View = TextView(this).apply {
        text = label
        textSize = 12f
        gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
        setPadding(dp(10), dp(6), dp(10), dp(6))
        background = GradientDrawable().apply {
            cornerRadius = dp(14).toFloat()
            setColor(Guofeng.JADE_DEEP)
        }
        setOnClickListener { onClick() }
    }
}
