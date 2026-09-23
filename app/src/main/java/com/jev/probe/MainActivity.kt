package com.jev.probe

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import com.jev.probe.core.Prefs
import com.jev.probe.capture.KeepAliveService
import com.jev.probe.ui.Guofeng
import com.jev.probe.ui.InkPaperDrawable

/**
 * Home / setup screen.
 *
 * The fork keeps the original behavior but presents it as one coherent
 * Chinese-ink product surface: paper background, jade actions, cinnabar seal
 * accent and restrained status cards.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var container: LinearLayout

    private val a11yComponent: String
        get() = "$packageName/com.jev.probe.capture.ChatCaptureService"

    private fun dp(v: Int) = Guofeng.dp(this, v)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        Guofeng.applyWindow(this)

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            background = InkPaperDrawable()
        }
        container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(32))
        }
        container.padForSystemBars()
        scroll.addView(container)
        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        if (prefs.enabled && Settings.canDrawOverlays(this)) {
            KeepAliveService.start(this)
        }
        build()
    }

    private fun build() {
        container.removeAllViews()

        container.addView(hero())

        val a11y = isA11yEnabled()
        val overlay = Settings.canDrawOverlays(this)
        val notifications = isNotificationAccessEnabled()
        val ime = isZhiyanImeEnabled()
        val key = prefs.hasKey()
        val ready = overlay && key

        container.addView(readinessCard(ready, notifications, overlay, key))

        container.addView(sectionTitle("启用准备", "微信基础模式只需悬浮窗 + 模型；通知权限可把操作降到一键"))
        container.addView(permissionCard(
            mark = "浮",
            title = "悬浮窗权限",
            desc = "微信 A / B 共用的常驻入口",
            granted = overlay
        ) {
            startActivity(Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            ))
        })
        container.addView(permissionCard(
            mark = "讯",
            title = "微信通知访问（推荐）",
            desc = "新消息到达后自动准备上下文；点一次悬浮球即可分析",
            granted = notifications
        ) {
            showNotificationDisclosure()
        })
        container.addView(permissionCard(
            mark = "读",
            title = "无障碍权限（可选）",
            desc = "仅用于 QQ / X / 飞书适配和可选本地截屏，不是微信必需",
            granted = a11y
        ) {
            showAccessibilityDisclosure()
        })
        container.addView(permissionCard(
            mark = "入",
            title = "小书童·快捷填入（可选）",
            desc = "点候选后通过 Android 标准输入法接口写入，随后切回原键盘",
            granted = ime
        ) {
            showImeDisclosure()
        })
        container.addView(permissionCard(
            mark = "稳",
            title = "自启动与省电设置",
            desc = "国产 ROM 建议设为允许自启动、后台不限",
            granted = null
        ) {
            runCatching {
                startActivity(Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:$packageName")
                ))
            }
        })

        container.addView(sectionTitle("常用入口", "把上下文与模型配置分开管理"))
        container.addView(shortcutRow())

        if (!prefs.contextEnabled) {
            container.addView(infoStrip(
                "关联上下文当前关闭",
                "不会写入聊天历史；需要长期记忆时再在设置中开启。"
            ))
        }

        container.addView(masterButton(prefs.enabled).apply {
            setOnClickListener {
                prefs.enabled = !prefs.enabled
                if (prefs.enabled && Settings.canDrawOverlays(this@MainActivity)) {
                    KeepAliveService.start(this@MainActivity)
                } else if (!prefs.enabled) {
                    KeepAliveService.stop(this@MainActivity)
                }
                build()
            }
        })

        container.addView(text(
            "微信 · QQ · X · 飞书  |  默认复制；可选快捷输入法直接填入，发送仍由你完成",
            11.5f,
            Guofeng.INK_FAINT
        ).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, 0)
        })
    }

    private fun hero(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(2), dp(8), dp(2), dp(8))
        }

        val avatar = ImageView(this).apply {
            setImageResource(R.drawable.zhiyan_mascot_avatar)
            scaleType = ImageView.ScaleType.CENTER_CROP
            contentDescription = "小书童·知言"
            background = Guofeng.round(
                this@MainActivity,
                30,
                Guofeng.CARD,
                Guofeng.BORDER_JADE
            )
            setPadding(dp(2), dp(2), dp(2), dp(2))
            layoutParams = LinearLayout.LayoutParams(dp(58), dp(58)).apply {
                rightMargin = dp(12)
            }
        }

        val left = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
        }
        left.addView(text("小书童·知言", 26f, Guofeng.JADE_DEEP, bold = true, serif = true))
        left.addView(text("装在手机上的「对话副驾」", 14f, Guofeng.INK_SOFT).apply {
            setPadding(0, dp(4), 0, 0)
        })
        left.addView(text("知言 · 慎答 · 由你发送", 12f, Guofeng.GOLD).apply {
            setPadding(0, dp(6), 0, 0)
        })

        val seal = TextView(this).apply {
            text = "知\n言"
            gravity = Gravity.CENTER
            textSize = 12f
            setTextColor(Guofeng.CARD)
            typeface = Guofeng.serif(true)
            background = Guofeng.round(this@MainActivity, 9, Guofeng.CINNABAR)
            setPadding(dp(9), dp(7), dp(9), dp(7))
            layoutParams = LinearLayout.LayoutParams(dp(44), dp(54)).apply {
                leftMargin = dp(8)
            }
        }

        row.addView(avatar)
        row.addView(left)
        row.addView(seal)
        return row
    }

    private fun readinessCard(
        ready: Boolean,
        notifications: Boolean,
        overlay: Boolean,
        key: Boolean
    ): View {
        val c = card(strong = true)

        val head = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val left = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
        }
        left.addView(text(
            if (ready) "今日已就绪" else "还差一点准备",
            17f,
            Guofeng.INK,
            bold = true,
            serif = true
        ))
        left.addView(text(
            if (ready) "进入聊天后，悬浮球会在旁边待命。"
            else "把下面缺失的权限或密钥补齐即可。",
            12f,
            Guofeng.INK_SOFT
        ).apply { setPadding(0, dp(3), 0, 0) })

        val badge = TextView(this).apply {
            text = if (ready) "可用" else "待配置"
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(if (ready) Guofeng.JADE_DEEP else Guofeng.CINNABAR)
            typeface = Guofeng.sans(true)
            background = Guofeng.round(
                this@MainActivity,
                18,
                if (ready) Guofeng.JADE_SOFT else Guofeng.CINNABAR_SOFT
            )
            setPadding(dp(13), dp(6), dp(13), dp(6))
        }

        head.addView(left)
        head.addView(badge)
        c.addView(head)

        val statuses = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(14), 0, 0)
        }
        statuses.addView(statusToken("悬浮窗", overlay))
        statuses.addView(statusToken("微信通知", notifications))
        statuses.addView(statusToken("密钥", key))
        c.addView(statuses)

        return c
    }

    private fun statusToken(label: String, ok: Boolean): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setPadding(dp(4), dp(4), dp(4), dp(2))
        }
        box.addView(text(if (ok) "●" else "○", 13f, if (ok) Guofeng.SUCCESS else Guofeng.INK_FAINT).apply {
            gravity = Gravity.CENTER
        })
        box.addView(text(label, 12f, Guofeng.INK_SOFT, bold = ok).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(2), 0, 0)
        })
        return box
    }

    private fun permissionCard(
        mark: String,
        title: String,
        desc: String,
        granted: Boolean?,
        onClick: () -> Unit
    ): View {
        val c = card()
        c.setOnClickListener { onClick() }

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val medallion = TextView(this).apply {
            text = mark
            textSize = 13f
            gravity = Gravity.CENTER
            typeface = Guofeng.serif(true)
            setTextColor(Guofeng.JADE_DEEP)
            background = Guofeng.round(this@MainActivity, 22, Guofeng.JADE_PALE, Guofeng.BORDER_JADE)
            layoutParams = LinearLayout.LayoutParams(dp(42), dp(42)).apply {
                rightMargin = dp(12)
            }
        }

        val copy = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        copy.addView(text(title, 15f, Guofeng.INK, bold = true, serif = true))
        copy.addView(text(desc, 12f, Guofeng.INK_SOFT).apply {
            setPadding(0, dp(3), 0, 0)
        })

        val state = TextView(this).apply {
            text = when (granted) {
                true -> "已开启"
                false -> "去开启"
                null -> "去设置"
            }
            textSize = 12f
            gravity = Gravity.CENTER
            typeface = Guofeng.sans(true)
            setTextColor(if (granted == true) Guofeng.JADE_DEEP else Guofeng.GOLD)
            background = Guofeng.round(
                this@MainActivity,
                16,
                if (granted == true) Guofeng.JADE_SOFT else Guofeng.GOLD_SOFT
            )
            setPadding(dp(11), dp(6), dp(11), dp(6))
        }

        row.addView(medallion)
        row.addView(copy)
        row.addView(state)
        c.addView(row)
        return c
    }

    private fun shortcutRow(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        row.addView(shortcutCard(
            mark = "册",
            title = "知识库与联系人",
            desc = "关系 · 备注 · 历史",
            primary = true
        ) {
            startActivity(Intent(this, KnowledgeActivity::class.java))
        })

        row.addView(shortcutCard(
            mark = "调",
            title = "接口与分析",
            desc = "模型 · 密钥 · OCR",
            primary = false
        ) {
            startActivity(Intent(this, SettingsActivity::class.java))
        })

        return row
    }

    private fun shortcutCard(
        mark: String,
        title: String,
        desc: String,
        primary: Boolean,
        onClick: () -> Unit
    ): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = Guofeng.round(
                this@MainActivity,
                18,
                if (primary) Guofeng.JADE_PALE else Guofeng.CARD,
                if (primary) Guofeng.BORDER_JADE else Guofeng.BORDER
            )
            elevation = dp(1).toFloat()
            setPadding(dp(14), dp(13), dp(12), dp(13))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                if (primary) rightMargin = dp(5) else leftMargin = dp(5)
            }
            setOnClickListener { onClick() }

            addView(text(mark, 19f, if (primary) Guofeng.JADE_DEEP else Guofeng.CINNABAR, bold = true, serif = true))
            addView(text(title, 14f, Guofeng.INK, bold = true, serif = true).apply {
                setPadding(0, dp(6), 0, 0)
            })
            addView(text(desc, 11.5f, Guofeng.INK_SOFT).apply {
                setPadding(0, dp(3), 0, 0)
            })
        }
    }

    private fun infoStrip(title: String, desc: String): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = Guofeng.round(this@MainActivity, 14, Guofeng.GOLD_SOFT, Guofeng.BORDER)
            setPadding(dp(13), dp(10), dp(13), dp(10))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(12) }

            addView(text(title, 13f, Guofeng.GOLD, bold = true))
            addView(text(desc, 11.5f, Guofeng.INK_SOFT).apply {
                setPadding(0, dp(2), 0, 0)
            })
        }
    }

    private fun masterButton(on: Boolean): View {
        return TextView(this).apply {
            text = if (on) "辅助已开启 · 点击暂停" else "开始辅助"
            textSize = 16f
            gravity = Gravity.CENTER
            typeface = Guofeng.serif(true)
            setTextColor(if (on) Guofeng.CARD else Guofeng.JADE_DEEP)
            background = Guofeng.round(
                this@MainActivity,
                24,
                if (on) Guofeng.JADE_DEEP else Guofeng.JADE_SOFT,
                Guofeng.BORDER_JADE
            )
            setPadding(dp(18), dp(15), dp(18), dp(15))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(20) }
        }
    }

    private fun sectionTitle(title: String, subtitle: String): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(2), dp(20), dp(2), dp(3))
            addView(text(title, 17f, Guofeng.JADE_DEEP, bold = true, serif = true))
            addView(text(subtitle, 11.5f, Guofeng.INK_FAINT).apply {
                setPadding(0, dp(2), 0, 0)
            })
        }
    }

    private fun card(strong: Boolean = false): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = Guofeng.round(
            this@MainActivity,
            18,
            if (strong) Guofeng.CARD else Guofeng.CARD_SOFT,
            if (strong) Guofeng.BORDER_JADE else Guofeng.BORDER
        )
        elevation = dp(if (strong) 2 else 1).toFloat()
        setPadding(dp(15), dp(14), dp(15), dp(14))
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(10) }
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

    private fun isNotificationAccessEnabled(): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)

    private fun isZhiyanImeEnabled(): Boolean {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        return imm.enabledInputMethodList.any {
            it.serviceInfo.packageName == packageName &&
                it.serviceInfo.name.endsWith(".ZhiyanInputMethodService")
        }
    }

    private fun showImeDisclosure() {
        AlertDialog.Builder(this)
            .setTitle("小书童·快捷填入")
            .setMessage(
                "这是可选的一次性辅助输入法，不替代你的日常键盘。Android 会把已启用输入法视为可用于文本编辑器的高敏感能力；" +
                    "只有你主动选择“小书童·快捷填入”时它才成为当前输入法。\n\n" +
                    "本版本只通过标准 InputConnection.commitText 写入你选中的候选，不读取光标前后文字、不读取选中文本、不记录你手工键入的内容，" +
                    "不会查找微信控件、不会模拟点击、不会触发“发送”；写入后会尝试自动切回上一个输入法。"
            )
            .setNegativeButton("暂不开启", null)
            .setPositiveButton("前往输入法设置") { _, _ ->
                startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
            }
            .show()
    }

    private fun showNotificationDisclosure() {
        AlertDialog.Builder(this)
            .setTitle("微信通知访问")
            .setMessage(
                "这是当前推荐的低风险便捷模式。Android 的“通知访问”属于系统级权限，授权后系统会把设备通知事件提供给本服务；" +
                    "小书童代码在入口处只处理微信（com.tencent.mm）通知，其他 App 通知会立即忽略，不写入本地历史，也不发送给模型。\n\n" +
                    "对微信，小书童只使用系统通知里已经显示的会话标题、消息摘要和通知时间；不会打开微信、不会读取微信内部控件、不会模拟点击。\n\n" +
                    "默认行为是“新消息到达→悬浮球提示→你点一下才分析”；你也可以在设置中显式开启“通知到达即分析”。"
            )
            .setNegativeButton("暂不开启", null)
            .setPositiveButton("前往系统设置") { _, _ ->
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
            .show()
    }

    /**
     * Prominent AccessibilityService disclosure shown immediately before the
     * system permission screen. It is intentionally separate from every other
     * permission/privacy disclosure and requires an affirmative action.
     */
    private fun showAccessibilityDisclosure() {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(8))

            addView(text(
                "小书童·知言使用标准 Android 无障碍能力，主要用于你主动发起的当前页面分析和可选的本地截屏识别。",
                14f,
                Guofeng.INK,
                bold = true
            ))
            addView(text(
                "开启后，服务具备访问当前活动窗口中可见文字、控件结构和会话标题的能力，也具备系统截屏能力。\n\n" +
                    "微信默认采用主动模式：不会因为收到消息就在后台持续读取聊天树；只有你点击悬浮球发起分析时，才会尝试读取当前可见内容。你也可以主动选择本地 ML Kit 截屏识别，本地 OCR 不需要图像 API Key，也不会上传截图。只有你以后明确选择“视觉 API”时，才会把裁剪后的聊天区域发送到你配置的视觉服务商。\n\n" +
                    "用途：你主动提交的内容用于生成意图判断和候选回复；只有你另外开启关联上下文时，相关历史才会写入本机。分析文本会发送给你在设置中选择的判断/回复模型服务商。\n\n" +
                    "小书童·知言不会读取微信数据库，不使用 Hook、Root 或私有协议，不会自动点击发送。微信候选回复默认只复制到剪贴板，由你自行粘贴并发送。你可以随时在系统无障碍设置中关闭此权限。",
                13f,
                Guofeng.INK_SOFT
            ).apply { setPadding(0, dp(12), 0, 0) })
        }

        val scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = true
            addView(content)
        }

        AlertDialog.Builder(this)
            .setTitle("无障碍访问披露")
            .setView(scroll)
            .setNegativeButton("不同意") { dialog, _ -> dialog.dismiss() }
            .setPositiveButton("同意并前往系统设置") { _, _ ->
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            .setCancelable(false)
            .show()
    }

    private fun isA11yEnabled(): Boolean {
        val enabled = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.contains(a11yComponent)
    }
}
