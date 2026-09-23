package com.jev.probe.debug

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Rect
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import java.util.ArrayDeque

/**
 * Debug-only structural Accessibility probe.
 *
 * It intentionally stores counts/metadata only. It never stores node text,
 * content descriptions, conversation names, message bodies, or view IDs.
 */
abstract class AccessibilityProbeBaseService : AccessibilityService() {

    protected abstract val profileName: String

    private var eventCount = 0L
    private var lastEventPackage = ""
    private var receiverRegistered = false

    private val probeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_PROBE) probeNow()
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        if (!receiverRegistered) {
            val filter = IntentFilter(ACTION_PROBE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(probeReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                registerReceiver(probeReceiver, filter)
            }
            receiverRegistered = true
        }
        writeStatus("connected", null, ProbeStats())
        Log.i(TAG, "connected profile=" + profileName)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        eventCount++
        lastEventPackage = event.packageName?.toString().orEmpty()
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (receiverRegistered) {
            runCatching { unregisterReceiver(probeReceiver) }
            receiverRegistered = false
        }
        super.onDestroy()
    }

    private fun probeNow() {
        val ws = runCatching { windows.toList() }.getOrDefault(emptyList())
        val directRoot = runCatching { rootInActiveWindow }.getOrNull()

        var source = if (directRoot != null) "rootInActiveWindow" else "none"
        var selectedRoot = directRoot

        if (selectedRoot == null) {
            selectedRoot = ws.asSequence()
                .filter { it.isActive || it.isFocused }
                .mapNotNull { safeRoot(it) }
                .firstOrNull { it.packageName?.toString() == WECHAT_PKG }
            if (selectedRoot != null) source = "activeWechatWindow"
        }
        if (selectedRoot == null) {
            selectedRoot = ws.asSequence()
                .mapNotNull { safeRoot(it) }
                .firstOrNull { it.packageName?.toString() == WECHAT_PKG }
            if (selectedRoot != null) source = "anyWechatWindow"
        }
        if (selectedRoot == null) {
            selectedRoot = ws.asSequence()
                .filter { it.isActive || it.isFocused }
                .mapNotNull { safeRoot(it) }
                .firstOrNull()
            if (selectedRoot != null) source = "activeWindowFallback"
        }

        val stats = selectedRoot?.let(::measureTree) ?: ProbeStats()
        val windowCount = ws.size
        val activeWindows = ws.count { it.isActive }
        val focusedWindows = ws.count { it.isFocused }
        val wechatWindows = ws.count { w ->
            safeRoot(w)?.packageName?.toString() == WECHAT_PKG
        }

        writeStatus(
            source,
            selectedRoot?.packageName?.toString(),
            stats.copy(
                windowCount = windowCount,
                activeWindowCount = activeWindows,
                focusedWindowCount = focusedWindows,
                wechatWindowCount = wechatWindows
            )
        )
        Log.i(
            TAG,
            "profile=" + profileName +
                " source=" + source +
                " root=" + (selectedRoot?.packageName?.toString() ?: "") +
                " nodes=" + stats.totalNodes +
                " text=" + stats.textNodes +
                " ids=" + stats.idNodes +
                " bkl=" + stats.legacyBubbleNodes +
                " windows=" + windowCount +
                " wechatWindows=" + wechatWindows
        )
    }

    private fun safeRoot(window: AccessibilityWindowInfo): AccessibilityNodeInfo? =
        runCatching { window.root }.getOrNull()

    private fun measureTree(root: AccessibilityNodeInfo): ProbeStats {
        var total = 0
        var text = 0
        var desc = 0
        var editable = 0
        var ids = 0
        var clickable = 0
        var legacyBubbles = 0
        var maxDepth = 0

        val stack = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
        stack.addLast(root to 0)

        while (stack.isNotEmpty() && total < MAX_NODES) {
            val (node, depth) = stack.removeLast()
            total++
            if (!node.text.isNullOrEmpty()) text++
            if (!node.contentDescription.isNullOrEmpty()) desc++
            if (node.isEditable) editable++
            if (node.isClickable) clickable++
            val id = node.viewIdResourceName
            if (!id.isNullOrBlank()) ids++
            if (id == LEGACY_WECHAT_BUBBLE_ID) legacyBubbles++
            if (depth > maxDepth) maxDepth = depth

            for (i in node.childCount - 1 downTo 0) {
                node.getChild(i)?.let { stack.addLast(it to (depth + 1)) }
            }
        }

        val bounds = Rect()
        root.getBoundsInScreen(bounds)
        return ProbeStats(
            totalNodes = total,
            textNodes = text,
            descNodes = desc,
            editableNodes = editable,
            idNodes = ids,
            clickableNodes = clickable,
            legacyBubbleNodes = legacyBubbles,
            maxDepth = maxDepth,
            rootChildCount = root.childCount,
            rootWidth = bounds.width(),
            rootHeight = bounds.height()
        )
    }

    private fun writeStatus(source: String, rootPackage: String?, stats: ProbeStats) {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
            .clear()
            .putLong("timestamp", System.currentTimeMillis())
            .putString("profile", profileName)
            .putString("source", source)
            .putString("root_package", rootPackage.orEmpty())
            .putLong("event_count", eventCount)
            .putString("last_event_package", lastEventPackage)
            .putInt("total_nodes", stats.totalNodes)
            .putInt("text_nodes", stats.textNodes)
            .putInt("desc_nodes", stats.descNodes)
            .putInt("editable_nodes", stats.editableNodes)
            .putInt("id_nodes", stats.idNodes)
            .putInt("clickable_nodes", stats.clickableNodes)
            .putInt("legacy_bkl_nodes", stats.legacyBubbleNodes)
            .putInt("max_depth", stats.maxDepth)
            .putInt("root_child_count", stats.rootChildCount)
            .putInt("root_width", stats.rootWidth)
            .putInt("root_height", stats.rootHeight)
            .putInt("window_count", stats.windowCount)
            .putInt("wechat_window_count", stats.wechatWindowCount)
            .putInt("active_window_count", stats.activeWindowCount)
            .putInt("focused_window_count", stats.focusedWindowCount)
            .apply()
    }

    private data class ProbeStats(
        val totalNodes: Int = 0,
        val textNodes: Int = 0,
        val descNodes: Int = 0,
        val editableNodes: Int = 0,
        val idNodes: Int = 0,
        val clickableNodes: Int = 0,
        val legacyBubbleNodes: Int = 0,
        val maxDepth: Int = 0,
        val rootChildCount: Int = 0,
        val rootWidth: Int = 0,
        val rootHeight: Int = 0,
        val windowCount: Int = 0,
        val wechatWindowCount: Int = 0,
        val activeWindowCount: Int = 0,
        val focusedWindowCount: Int = 0
    )

    companion object {
        const val ACTION_PROBE = "com.jev.probe.debug.PROBE_ACCESSIBILITY"
        private const val PREFS = "jev_debug_accessibility_probe"
        private const val TAG = "JEV_AB_PROBE"
        private const val WECHAT_PKG = "com.tencent.mm"
        private const val LEGACY_WECHAT_BUBBLE_ID = "com.tencent.mm:id/bkl"
        private const val MAX_NODES = 10000
    }
}
