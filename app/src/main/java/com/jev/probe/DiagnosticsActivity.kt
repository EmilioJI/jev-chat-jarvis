package com.jev.probe

import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.jev.probe.core.DiagnosticsStore
import com.jev.probe.ui.Guofeng
import com.jev.probe.ui.InkPaperDrawable
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class DiagnosticsActivity : AppCompatActivity() {

    private lateinit var root: LinearLayout
    private fun dp(v: Int) = Guofeng.dp(this, v)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Guofeng.applyWindow(this)

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            background = InkPaperDrawable()
        }
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(32))
        }
        root.padForSystemBars()
        scroll.addView(root)
        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        root.removeAllViews()
        root.addView(text("捕获诊断", 27f, Guofeng.JADE_DEEP, bold = true, serif = true))
        root.addView(text(
            "只显示本机元数据，不保存聊天标题、聊天正文、OCR 文本、API Key 或模型响应。",
            12f,
            Guofeng.GOLD
        ).apply { setPadding(0, dp(4), 0, dp(12)) })

        val d = DiagnosticsStore.read(this)
        val card = card()
        if (d.updatedAt <= 0L) {
            card.addView(text("还没有捕获记录", 16f, Guofeng.INK, bold = true, serif = true))
            card.addView(text(
                "开启无障碍后进入微信 / QQ / X / 飞书任一聊天，再回到这里查看。",
                12.5f,
                Guofeng.INK_SOFT
            ).apply { setPadding(0, dp(6), 0, 0) })
        } else {
            card.addView(row("最近更新", formatTime(d.updatedAt)))
            card.addView(row("当前应用", appLabel(d.packageName)))
            card.addView(row("包名", d.packageName.ifBlank { "—" }))
            card.addView(row("适配器", d.adapter.ifBlank { "—" }))
            card.addView(row("捕获来源", sourceLabel(d.source)))
            card.addView(row("标题节点", if (d.titlePresent) "已读到（未保存内容）" else "未读到"))
            card.addView(row("消息条数", d.messageCount.toString()))
            card.addView(row("最新方向", sideLabel(d.latestFrom)))
            card.addView(row("状态", statusLabel(d.status)))
        }
        root.addView(card)

        root.addView(cardButton("刷新") { render() })
        root.addView(cardButton("清空诊断元数据") {
            DiagnosticsStore.clear(this)
            render()
        })

        root.addView(info(
            "微信 A/B 验收",
            "打开同一个微信聊天后，如果“适配器=WeChat、捕获来源=控件树、消息条数>0”，说明节点树读取成功。合规 A/B 包与普通验收包分别测一次即可比较 isAccessibilityTool 标志是否影响微信。"
        ))
    }

    private fun appLabel(pkg: String): String = when (pkg) {
        "com.tencent.mm" -> "微信"
        "com.tencent.mobileqq" -> "QQ"
        "com.ss.android.lark" -> "飞书"
        "com.twitter.android" -> "X"
        "" -> "—"
        else -> pkg
    }

    private fun sourceLabel(source: String): String = when (source) {
        "tree" -> "控件树"
        "tree_empty" -> "控件树为空，准备 OCR"
        "ocr_local" -> "本地 ML Kit OCR"
        "ocr_vision" -> "远程 Vision OCR"
        "unknown_app" -> "未适配 App"
        "screenshot_failed" -> "截图失败"
        else -> source.ifBlank { "—" }
    }

    private fun sideLabel(side: String): String = when (side) {
        "me" -> "我"
        "other" -> "对方"
        else -> "未知 / 无消息"
    }

    private fun statusLabel(status: String): String = when {
        status.startsWith("screenshot_error:") -> "截图错误码 " + status.substringAfter(':')
        status.isBlank() -> "—"
        else -> status
    }

    private fun formatTime(ms: Long): String = runCatching {
        FORMATTER.format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))
    }.getOrDefault(ms.toString())

    private fun row(label: String, value: String) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.TOP
        setPadding(0, dp(6), 0, dp(6))
        addView(text(label, 12.5f, Guofeng.INK_SOFT, bold = true).apply {
            layoutParams = LinearLayout.LayoutParams(dp(88), ViewGroup.LayoutParams.WRAP_CONTENT)
        })
        addView(text(value, 12.5f, Guofeng.INK).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
    }

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = Guofeng.round(this@DiagnosticsActivity, 17, Guofeng.CARD, Guofeng.BORDER)
        elevation = dp(1).toFloat()
        setPadding(dp(15), dp(13), dp(15), dp(13))
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(8) }
    }

    private fun cardButton(label: String, onClick: () -> Unit) = TextView(this).apply {
        text = label
        textSize = 14f
        gravity = Gravity.CENTER
        typeface = Guofeng.sans(true)
        setTextColor(Guofeng.JADE_DEEP)
        background = Guofeng.round(this@DiagnosticsActivity, 12, Guofeng.CARD, Guofeng.BORDER_JADE)
        setPadding(dp(14), dp(11), dp(14), dp(11))
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(10) }
        setOnClickListener { onClick() }
    }

    private fun info(title: String, body: String) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = Guofeng.round(this@DiagnosticsActivity, 14, Guofeng.JADE_PALE, Guofeng.BORDER_JADE)
        setPadding(dp(14), dp(12), dp(14), dp(12))
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(14) }
        addView(text(title, 14f, Guofeng.JADE_DEEP, bold = true, serif = true))
        addView(text(body, 12f, Guofeng.INK_SOFT).apply { setPadding(0, dp(5), 0, 0) })
    }

    private fun text(
        value: String,
        size: Float,
        color: Int,
        bold: Boolean = false,
        serif: Boolean = false
    ) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        typeface = if (serif) Guofeng.serif(bold) else Guofeng.sans(bold)
    }

    companion object {
        private val FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    }
}
