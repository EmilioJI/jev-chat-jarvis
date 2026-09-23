package com.jev.probe.capture

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.jev.probe.capture.ocr.MlKitOcr
import com.jev.probe.capture.ocr.OcrLine
import com.jev.probe.capture.ocr.ScreenCapture
import com.jev.probe.capture.ocr.VisionDialogParser
import com.jev.probe.core.BubbleRect
import com.jev.probe.core.ChatSnapshot
import com.jev.probe.core.DiagnosticsStore
import com.jev.probe.core.Msg
import com.jev.probe.core.Prefs
import com.jev.probe.core.RankedReply
import com.jev.probe.core.kb.ChatContext
import com.jev.probe.core.kb.ContextBuilder
import com.jev.probe.core.kb.HistoryCaptureHint
import com.jev.probe.core.kb.KbStore
import com.jev.probe.jev.ContactSummaryManager
import com.jev.probe.jev.JevClient
import com.jev.probe.jev.VisionClient
import com.jev.probe.overlay.OverlayController
import com.jev.probe.overlay.OverlayRuntime
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException

/**
 * Standard Android AccessibilityService used transparently under this app's own
 * class/package identity.
 *
 * WeChat is deliberately excluded from this service's accessibility event
 * subscription and has no app-specific node adapter. The persistent overlay is
 * the entry point; clipboard text and optional on-device OCR are explicit user
 * actions. No WeChat resource id or internal node tree is used.
 *
 * The service never sends a message and does not automate WeChat input controls.
 * Candidates are copied, then the user pastes and sends them.
 */
open class ChatCaptureService : AccessibilityService() {

    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newFixedThreadPool(2)
    /** Low-priority maintenance work must never occupy the real-time analysis pool. */
    private val maintenanceWorker = Executors.newSingleThreadExecutor()

    /** Adapted chat apps, keyed by package name. */
    private val adapters = listOf(QQAdapter(), XAdapter(), FeishuAdapter()).associateBy { it.pkg }

    /** Submit to the worker, ignoring rejection after the service is torn down
     *  (a stale overlay callback must never crash the process). */
    private fun submit(task: () -> Unit) {
        try { worker.execute(task) } catch (_: RejectedExecutionException) { }
    }

    private fun submitMaintenance(task: () -> Unit) {
        try { maintenanceWorker.execute(task) } catch (_: RejectedExecutionException) { }
    }

    private lateinit var prefs: Prefs
    private var overlay: OverlayController? = null

    private var lastSignature: String = ""
    @Volatile private var activePkg: String? = null
    private var analyzing = false

    /**
     * Monotonic generation for analysis work. Every real conversation/content
     * change invalidates the previous generation. Network calls are not forcibly
     * cancelled, but their callbacks are ignored once their generation is stale.
     */
    @Volatile private var analysisEpoch = 0L

    private data class AnalysisSession(
        val epoch: Long,
        val pkg: String,
        val signature: String,
        val messageSignature: String,
        val title: String?
    )

    private data class TitleEvidence(
        val title: String,
        val messageKeys: Set<String>
    )

    /** Why a screen was re-read. Only CONTENT_CHANGED may auto-analyze. */
    private enum class CaptureTrigger {
        CONTENT_CHANGED,
        VIEW_SCROLLED,
        WINDOW_CHANGED,
        RESTORE
    }

    /** Last known-good title per package, tied to recent message evidence.
     *  A transient title from a newly opened conversation must never inherit the
     *  previous conversation's contact/title just because the package is equal. */
    private val lastGoodTitle: MutableMap<String, TitleEvidence> = HashMap()
    private val debounce = Runnable { runAnalysis() }

    // Android/WeChat window transitions are not atomic: TYPE_WINDOW_STATE_CHANGED
    // can arrive before the new chat's accessibility tree is populated. Re-read
    // shortly afterwards so the bubble does not depend on a lucky later scroll
    // or content event. Separate Runnables let us cancel event storms cleanly.
    private val windowSettleFast = Runnable { retryCapture(CaptureTrigger.WINDOW_CHANGED) }
    private val windowSettleLate = Runnable { retryCapture(CaptureTrigger.WINDOW_CHANGED) }
    private val contentSettle = Runnable { retryCapture(CaptureTrigger.CONTENT_CHANGED) }
    private val restoreSettleFast = Runnable { retryCapture(CaptureTrigger.RESTORE) }
    private val restoreSettleLate = Runnable { retryCapture(CaptureTrigger.RESTORE) }

    private var pendingSnapshot: ChatSnapshot? = null
    private var pendingHistoryHint: HistoryCaptureHint = HistoryCaptureHint.CONSERVATIVE
    @Volatile private var currentSnapshot: ChatSnapshot? = null
    private var foregroundPkg: String? = null
    /** True after vivo clone resolver until a normal user-0 chat event is seen. */
    private var cloneUserMode: Boolean = false

    // ---- OCR path. Screenshot and local ML Kit callbacks return on the main
    // thread; remote Vision OCR explicitly moves JPEG encoding + network I/O to
    // the worker pool and is only used when the user selects it.
    private val screenCapture by lazy {
        ScreenCapture(this,
            hideOverlay = { overlay?.setHiddenForShot(true) },
            restoreOverlay = { overlay?.setHiddenForShot(false) })
    }
    private val ocr = MlKitOcr()
    private var ocrBusy = false

    /** What the screen looked like the last time we fired an automatic shot.
     *  See [ocrSignature]: this is the brake on the OCR path. */
    private var lastOcrSignature: String = ""

    override fun onServiceConnected() {
        super.onServiceConnected()
        prefs = Prefs(this)
        overlay = OverlayRuntime.get(this)
        overlay?.onManualAnalyze = { manualAnalyzeCurrentWindow() }
        // Bubble menu: file the open conversation as a knowledge-base contact.
        // Contacts are never created automatically — this is the one-tap way in.
        overlay?.onSaveContact = {
            val title = currentSnapshot?.title
            val pkg = activePkg ?: foregroundPkg ?: ""
            when {
                title.isNullOrBlank() -> overlay?.toast("当前会话没有标题，存不了")
                isTransientTitle(title) -> overlay?.toast("当前会话标题还没加载出来，稍后再试")
                else -> submit {
                    val msg = try {
                        KbStore.get(this).saveOrMergeContact(title, pkg)
                    } catch (e: Exception) { "保存失败：${e.javaClass.simpleName}" }
                    main.post { overlay?.toast(msg) }
                }
            }
        }
        // Bubble menu: one manual screenshot + OCR, for any app at all.
        overlay?.onOcrCapture = { ocrCaptureManual() }
        // Keep the process at foreground importance so MIUI does not freeze us.
        runCatching { KeepAliveService.start(this) }
        // Load the bundled OCR model now, off the main thread: the first
        // recognize() otherwise pays for it inside the screenshot callback.
        submit { MlKitOcr.warmUp() }
        // OEMs may kill/restart the service while a chat is already open. Probe
        // twice: an early read for responsiveness and a later read after the
        // accessibility tree has fully settled.
        main.postDelayed(restoreSettleFast, 120)
        main.postDelayed(restoreSettleLate, 650)
        Log.i(TAG, "capture service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (!prefs.enabled) {
            if (analyzing || pendingSnapshot != null) invalidateAnalysis()
            main.post { overlay?.hide() }
            return
        }

        val type = event.eventType

        // Decide "did we leave the chat app" from the REAL active window, not the
        // event's package. The event package can be an IME (e.g. com.tencent.wetype)
        // or the status bar while the chat app is still foreground — keying off it
        // made the bubble flicker (hide → re-show → hide…). rootInActiveWindow stays
        // on the chat app while the keyboard is up, so this is stable.
        //
        // An app with no adapter is NOT a reason to take the bubble away: the only
        // way into DingTalk / Telegram / anything else is the bubble menu's
        // "截屏识别一次", and a bubble that is gone cannot be tapped. So we park
        // the idle bubble there instead — still no automatic capture, no analysis.
        // The bubble does come off for places where it would only be in the way:
        // our own settings screens, the launcher, and the system UI.
        if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val fg = rootInActiveWindow?.packageName?.toString()
            if (fg != null && fg != foregroundPkg) {
                foregroundPkg = fg
                // Leaving the package that produced the current analysis is enough
                // to make every in-flight result/fill callback stale immediately.
                if (fg != activePkg) invalidateAnalysis()
            }
            if (fg == "com.vivo.doubleinstance") cloneUserMode = true
            if (fg != null && fg !in adapters) {
                // Keep the collapsed bubble even over our own activity.
                // Clone-user transitions may emit no accessibility event back to
                // user 0, so destroying the overlay here can make it impossible
                // to re-create when the user enters WeChat B.
                val drop = fg == "com.android.systemui"
                if (!drop) {
                    DiagnosticsStore.record(
                        this,
                        packageName = fg,
                        adapter = "未适配",
                        source = "unknown_app",
                        titlePresent = false,
                        messageCount = 0,
                        latestFrom = null,
                        status = "capture_only"
                    )
                }
                // A clone-app resolver is the hand-off into another Android user.
                // The user-0 accessibility service cannot trust any old snapshot
                // after that boundary, but its display overlay can remain visible.
                currentSnapshot = null
                activePkg = null
                main.post {
                    if (drop) {
                        overlay?.hide()
                    } else {
                        val note = when {
                            fg == "com.vivo.doubleinstance" -> "分身应用兼容模式"
                            fg == packageName ||
                                fg.contains("launcher", ignoreCase = true) ||
                                fg == "com.miui.home" -> null
                            else -> "当前应用需截图识别"
                        }
                        overlay?.showCaptureOnly(note)
                    }
                }
                return
            }
        }

        when (type) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                maybeCapture(CaptureTrigger.WINDOW_CHANGED)
                scheduleWindowSettle()
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                maybeCapture(CaptureTrigger.CONTENT_CHANGED)
                scheduleContentSettle()
            }
            AccessibilityEvent.TYPE_VIEW_SCROLLED ->
                maybeCapture(CaptureTrigger.VIEW_SCROLLED)
        }
    }

    private fun retryCapture(trigger: CaptureTrigger) {
        if (!::prefs.isInitialized || !prefs.enabled) return
        runCatching { maybeCapture(trigger) }
            .onFailure { Log.d(TAG, "settle capture skipped: ${it.javaClass.simpleName}") }
    }

    private fun scheduleWindowSettle() {
        main.removeCallbacks(windowSettleFast)
        main.removeCallbacks(windowSettleLate)
        main.postDelayed(windowSettleFast, 120)
        main.postDelayed(windowSettleLate, 420)
    }

    private fun scheduleContentSettle() {
        // One short retry catches the common case where the event precedes the
        // updated bubble subtree. Repeated content events coalesce into one retry.
        main.removeCallbacks(contentSettle)
        main.postDelayed(contentSettle, 140)
    }

    private fun maybeCapture(trigger: CaptureTrigger) {
        val root = rootInActiveWindow ?: return
        val pkg = root.packageName?.toString()
        // Apps with no adapter are never handled automatically (v1.3 revision):
        // the only way in for them is the bubble menu's "截屏识别一次".
        val adapter = adapters[pkg] ?: return
        // Only act inside a chat window (the adapter returns null elsewhere).
        val rawSnapshot = adapter.extract(root, resources) ?: return
        // Stabilize the title BEFORE anything below reads it: some apps (X) show
        // a transient "连接中…" title for a moment right after opening a thread.
        val snapshot = stabilizeTitle(pkg ?: "", rawSnapshot)
        DiagnosticsStore.record(
            this,
            packageName = pkg ?: "",
            adapter = adapterLabel(pkg ?: ""),
            source = if (snapshot.messages.isEmpty()) "tree_empty" else "tree",
            titlePresent = !isTransientTitle(snapshot.title),
            messageCount = snapshot.messages.size,
            latestFrom = snapshot.latestFrom,
            status = if (snapshot.messages.isEmpty()) "tree_empty" else "ok"
        )
        if (!prefs.isAllowed(snapshot.title)) { main.post { overlay?.hide() }; return }
        // In a chat window but the tree holds no text (Feishu draws its bodies,
        // some apps expose only limited nodes) → screenshot + OCR, subject
        // to ScreenCapture's own >=1s throttle and failure backoff.
        if (snapshot.messages.isEmpty()) {
            if (prefs.ocrFallback) {
                // Gate BEFORE the shot, not after the OCR. Feishu's tree is empty
                // on every content-changed event, and a successful shot resets the
                // failure backoff — so without this the caret blinking or an
                // "online" badge flipping keeps a screenshot going out every
                // second forever. The picture can only differ if the bubbles moved
                // or the conversation changed, and that is exactly what the
                // signature measures.
                val sig = ocrSignature(pkg ?: "", snapshot.title, snapshot.bubbleRects)
                if (sig == lastOcrSignature && overlay?.isShowing() == true) return
                lastOcrSignature = sig
                ocrCapture(
                    snapshot.title,
                    snapshot.bubbleRects,
                    pkg ?: "",
                    manual = false,
                    autoEligible = trigger == CaptureTrigger.CONTENT_CHANGED
                )
            }
            return
        }

        // Switching to another adapted app resets the dedupe signature, so two apps
        // whose last few messages happen to match cannot swallow each other.
        if (pkg != activePkg) { activePkg = pkg; lastSignature = "" }

        currentSnapshot = snapshot
        val sig = snapshot.signature()
        val showing = overlay?.isShowing() == true
        // Same content and the bubble is already up → nothing to do.
        if (sig == lastSignature && showing) return
        // Same content but the bubble is gone (killed by MIUI, or we left and came
        // back) → just put the bubble back, do NOT re-analyze (saves tokens/time).
        if (sig == lastSignature && !showing) { main.post { overlay?.showIdle(snapshot.title) }; return }
        // Anything else reaching here is a genuinely different conversation (new
        // app, title, or content). Invalidate the old async generation BEFORE
        // clearing the UI so a late network callback cannot repopulate it.
        invalidateAnalysis()
        main.post { overlay?.resetForNewConversation() }
        lastSignature = sig
        Log.d(TAG, "snapshot[$pkg] title.len=${snapshot.title?.length ?: 0} n=${snapshot.messages.size} " +
            snapshot.messages.takeLast(6).joinToString(" | ") { "${it.side}:${it.text.length}" }) // metadata lengths only, never title/content

        // Auto-analysis is only eligible for a real CONTENT_CHANGED event.
        // Scrolling, opening a window, or service restore may expose different
        // messages but must never be mistaken for a new incoming message.
        val autoEligible = trigger == CaptureTrigger.CONTENT_CHANGED
        if (!autoEligible || snapshot.latestFrom != "other" || !prefs.autoAnalyze) {
            main.post { overlay?.showIdle(snapshot.title) }
            return
        }

        // The bubble is UI feedback, not an analysis result. Show it immediately
        // so the user never waits on debounce/network just to know the assistant
        // is alive; the panel changes to "分析中…" when runAnalysis starts.
        main.post { overlay?.showIdle(snapshot.title) }
        pendingSnapshot = snapshot
        pendingHistoryHint = HistoryCaptureHint.NEWEST_SCREEN
        main.removeCallbacks(debounce)
        main.postDelayed(debounce, 350) // short debounce: dedupe already absorbs event bursts
    }

    /** A placeholder title an app shows only for a moment (e.g. X's "连接中…"
     *  right after opening a DM thread) — never a real conversation title.
     *  Blank/null counts too, so a caller can always fall back the same way. */
    private fun adapterLabel(pkg: String): String = when (pkg) {
        "com.tencent.mm" -> "WeChat"
        "com.tencent.mobileqq" -> "QQ"
        "com.ss.android.lark" -> "Feishu"
        "com.twitter.android" -> "X"
        else -> if (pkg.isBlank()) "未知" else "未适配"
    }

    private fun isTransientTitle(t: String?): Boolean {
        val trimmed = t?.trim()?.removeSuffix("…")?.removeSuffix("...")?.trim()
        if (trimmed.isNullOrEmpty()) return true
        val lower = trimmed.lowercase()
        return TRANSIENT_TITLE_WORDS.any { lower.contains(it.lowercase()) }
    }

    /** Recent message keys used only as evidence that two transient-title reads
     *  still belong to the same conversation. */
    private fun messageKeys(snapshot: ChatSnapshot): Set<String> =
        snapshot.messages.takeLast(6).map { it.side + "\u0000" + it.text }.toSet()

    private fun messageSignature(snapshot: ChatSnapshot): String =
        snapshot.messages.takeLast(6).joinToString("\u0002") { it.side + "\u0000" + it.text }

    /** Replace a transient title only when recent message evidence overlaps with
     *  the last titled snapshot for this package. A package-level title cache by
     *  itself can attach chat B to chat A while B still says "连接中…". */
    private fun stabilizeTitle(pkg: String, snapshot: ChatSnapshot): ChatSnapshot {
        if (isTransientTitle(snapshot.title)) {
            val evidence = lastGoodTitle[pkg] ?: return snapshot
            val keys = messageKeys(snapshot)
            val sameConversation = keys.isNotEmpty() && keys.any { it in evidence.messageKeys }
            return if (sameConversation) snapshot.copy(title = evidence.title) else snapshot
        }
        snapshot.title?.let { lastGoodTitle[pkg] = TitleEvidence(it, messageKeys(snapshot)) }
        return snapshot
    }

    /** Mark every older analysis callback stale and cancel any pending debounce. */
    private fun invalidateAnalysis() {
        analysisEpoch++
        analyzing = false
        pendingSnapshot = null
        pendingHistoryHint = HistoryCaptureHint.CONSERVATIVE
        main.removeCallbacks(debounce)
    }

    private fun sessionStillCurrent(session: AnalysisSession): Boolean {
        val current = currentSnapshot ?: return false
        return session.epoch == analysisEpoch &&
            session.pkg == (activePkg ?: "") &&
            session.signature == current.signature()
    }

    /**
     * Resolve identity and relationship for this exact conversation.
     *
     * The visible conversation title supplies identity (e.g. WeChat remark or
     * nickname). Relationship semantics are never guessed from that name:
     * per-contact configuration wins, then the optional global fallback, else
     * the model is told explicitly that the relationship is unknown.
     */
    private fun effectiveRelationship(snapshot: ChatSnapshot, ctx: ChatContext?): String {
        val display = ctx?.contact?.name?.trim().orEmpty()
            .ifBlank { KbStore.displayName(snapshot.title) }
        val relation = ctx?.contact?.relationship?.trim().orEmpty()
            .ifBlank { prefs.relationship.trim() }

        return buildString {
            if (display.isNotBlank()) {
                append("当前会话名称/备注：").append(display).append('；')
            }
            if (relation.isNotBlank()) {
                append("与我的关系：").append(relation)
            } else {
                append("与当前会话对象的关系未设置")
            }
        }
    }

    private fun runAnalysis() {
        val snapshot = pendingSnapshot ?: return
        val historyHint = pendingHistoryHint
        if (analyzing) return
        if (!prefs.hasKey()) { main.post { overlay?.showError("未设置判断接口密钥，去设置里填") }; return }

        val pkg = activePkg ?: rootInActiveWindow?.packageName?.toString().orEmpty()
        val session = AnalysisSession(
            epoch = analysisEpoch,
            pkg = pkg,
            signature = snapshot.signature(),
            messageSignature = messageSignature(snapshot),
            title = snapshot.title
        )

        analyzing = true
        main.post { overlay?.showLoading(); overlay?.setNote(snapshot.note) }
        val client = JevClient(prefs)

        // Both network branches are concurrent, but publishing is coordinated on
        // the main thread: replies that beat the judgment are held instead of
        // being dropped by OverlayController.showReplies().
        var judgmentReady = false
        var judgmentOk = false
        var pendingRanked: List<RankedReply>? = null
        var pendingReplyError: String? = null
        var pendingRepliesFinal = false

        fun publishRepliesIfReady() {
            val ranked = pendingRanked ?: return
            if (!judgmentReady || !judgmentOk || !sessionStillCurrent(session)) return
            if (pendingRepliesFinal) analyzing = false
            overlay?.showReplies(
                ranked,
                pendingReplyError,
                sorting = !pendingRepliesFinal
            )
        }

        // Knowledge context first (local file reads only, a few ms), then the two
        // network calls in parallel on the pool. A failure here must never stop
        // the analysis — it just means no extra context this round.
        submit contextTask@{
            if (!sessionStillCurrent(session)) return@contextTask
            val ctx = try {
                ContextBuilder.build(this, snapshot, pkg, prefs, historyHint)
            } catch (e: Exception) {
                Log.w(TAG, "context build failed: ${e.javaClass.simpleName}"); null
            }
            if (!sessionStillCurrent(session)) return@contextTask
            val rel = effectiveRelationship(snapshot, ctx)
            main.post contextPost@{
                if (!sessionStillCurrent(session)) return@contextPost
                overlay?.setContextInfo(ctx?.notes?.size ?: 0, ctx?.history?.size ?: 0)
            }

            // Judgment is usually fast — show it as soon as it arrives.
            submit judgeTask@{
                if (!sessionStillCurrent(session)) return@judgeTask
                val judgment = client.judge(snapshot, rel, ctx)
                main.post judgePost@{
                    if (!sessionStillCurrent(session)) return@judgePost
                    judgmentReady = true
                    if (judgment.error != null) {
                        analyzing = false
                        overlay?.showError(judgment.error)
                    } else {
                        judgmentOk = true
                        overlay?.showJudgment(judgment)
                        publishRepliesIfReady()
                    }
                }
            }

            // Reply path is progressive: generate 3 usable candidates first and
            // publish them immediately; Jev ranking then upgrades the same cards.
            // This removes one whole network round trip from perceived latency
            // without giving up the final judgment-engine ordering.
            submit replyTask@{
                if (!sessionStillCurrent(session)) return@replyTask

                var replyError: String? = null
                val draftStarted = System.currentTimeMillis()
                val candidates = try {
                    client.draftCandidates(snapshot, rel, ctx)
                } catch (e: Exception) {
                    replyError = e.message ?: e.javaClass.simpleName
                    emptyList()
                }
                Log.i(TAG, "latency reply_draft_ms=${System.currentTimeMillis() - draftStarted}")

                if (candidates.isEmpty()) {
                    main.post replyPost@{
                        if (!sessionStillCurrent(session)) return@replyPost
                        pendingReplyError = replyError ?: "未生成候选回复"
                        pendingRanked = emptyList()
                        pendingRepliesFinal = true
                        if (judgmentReady && !judgmentOk) analyzing = false
                        publishRepliesIfReady()
                    }
                    return@replyTask
                }

                val provisional = candidates.map { RankedReply(it, 0.0) }
                main.post draftPost@{
                    if (!sessionStillCurrent(session)) return@draftPost
                    pendingReplyError = null
                    pendingRanked = provisional
                    pendingRepliesFinal = false
                    publishRepliesIfReady()
                }

                val rankStarted = System.currentTimeMillis()
                val ranked = try {
                    client.rankCandidates(snapshot, rel, candidates, ctx)
                } catch (e: Exception) {
                    replyError = e.message ?: e.javaClass.simpleName
                    emptyList()
                }
                Log.i(TAG, "latency reply_rank_ms=${System.currentTimeMillis() - rankStarted}")

                main.post rankPost@{
                    if (!sessionStillCurrent(session)) return@rankPost
                    pendingReplyError = replyError
                    pendingRanked = if (ranked.isNotEmpty()) ranked else provisional
                    pendingRepliesFinal = true
                    if (judgmentReady && !judgmentOk) analyzing = false
                    publishRepliesIfReady()
                }

                // Rolling contact summary is opt-in, low-frequency and off the
                // critical UI path. A healthy draft is sufficient; ranking
                // failure must not suppress maintenance work.
                if (
                    candidates.isNotEmpty() &&
                    historyHint == HistoryCaptureHint.NEWEST_SCREEN &&
                    sessionStillCurrent(session)
                ) {
                    ctx?.contact?.let { contact ->
                        submitMaintenance {
                            runCatching {
                                ContactSummaryManager.maybeRefresh(
                                    KbStore.get(this),
                                    contact,
                                    prefs
                                )
                            }.onFailure { e ->
                                Log.w(TAG, "contact summary failed: ${e.javaClass.simpleName}")
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Explicit one-shot standard-node read for supported non-WeChat apps.
     * Failure never falls through to screenshot automatically.
     */
    private fun manualAnalyzeCurrentWindow() {
        val root = rootInActiveWindow
        if (root == null) {
            overlay?.toast("当前窗口控件不可读，可改用剪贴板或本地截屏")
            return
        }
        val pkg = root.packageName?.toString().orEmpty()
        val adapter = adapters[pkg]
        if (adapter == null) {
            overlay?.showCaptureOnly("当前应用没有控件适配")
            return
        }
        val raw = runCatching { adapter.extract(root, resources) }.getOrNull()
        if (raw == null || raw.messages.isEmpty()) {
            overlay?.showCaptureOnly(
                "当前页面控件不可读，可选剪贴板或本地 OCR"
            )
            return
        }

        val snapshot = stabilizeTitle(pkg, raw)
        if (!prefs.isAllowed(snapshot.title)) {
            overlay?.toast("当前会话不在白名单")
            return
        }
        cloneUserMode = false
        activePkg = pkg
        foregroundPkg = pkg
        currentSnapshot = snapshot
        lastSignature = snapshot.signature()
        invalidateAnalysis()
        // invalidateAnalysis clears current async work, not the snapshot itself.
        currentSnapshot = snapshot
        pendingSnapshot = snapshot
        pendingHistoryHint = HistoryCaptureHint.CONSERVATIVE
        runAnalysis()
    }

    // ------------------------------------------------------------------ OCR

    /**
     * Bubble menu → "截屏识别一次". Works on ANY app, adapted or not: one whole
     * screen shot, every line OCR'd, lines grouped into pseudo-bubbles by line
     * spacing. Nobody can tell who said what this way, so everything is filed as
     * the other person and the panel says so.
     */
    private fun ocrCaptureManual() {
        // Manual OCR deliberately avoids rootInActiveWindow and title nodes.
        // The whole visible display is captured only after the user taps.
        val pkg = if (cloneUserMode) WECHAT_PKG else ""
        ocrCapture(null, emptyList(), pkg, manual = true, autoEligible = false)
    }

    /**
     * What the screen would look like to a camera, as far as the tree can tell.
     *
     * Feishu: the conversation title plus every bubble rectangle and its side —
     * the bubbles move whenever the list scrolls or a message arrives, and stay
     * put when only chrome (caret, presence dot, timestamp) redraws. Apps that
     * give us no rectangles fall back to package + title, which at least stops a
     * burst of events on one screen from becoming a burst of screenshots.
     */
    private fun ocrSignature(pkg: String, title: String?, rects: List<BubbleRect>): String {
        if (rects.isEmpty()) return pkg + "|" + (title ?: "")
        return (title ?: "") + "|" + rects.joinToString(";") { br ->
            val r = br.rect
            "${r.left},${r.top},${r.right},${r.bottom},${br.side}"
        }
    }

    /**
     * Screenshot, then either OCR each known bubble rect (Feishu: the tree knows
     * where the bubbles are and who sent them, just not what they say) or OCR
     * the whole screen (everything else).
     */
    private fun ocrCapture(
        treeTitle: String?,
        rects: List<BubbleRect>,
        pkg: String,
        manual: Boolean,
        autoEligible: Boolean
    ) {
        if (ocrBusy) return
        ocrBusy = true
        val captureFn: (((ScreenCapture.Result) -> Unit) -> Unit) =
            if (manual) screenCapture::captureDisplay else screenCapture::capture
        captureFn { res ->
            when (res) {
                is ScreenCapture.Result.Failed -> {
                    ocrBusy = false
                    DiagnosticsStore.record(
                        this,
                        packageName = pkg,
                        adapter = adapterLabel(pkg),
                        source = "screenshot_failed",
                        titlePresent = !isTransientTitle(treeTitle),
                        messageCount = 0,
                        latestFrom = null,
                        status = "screenshot_error:${res.code}"
                    )
                    Log.i(TAG, "ocr: screenshot failed code=${res.code}")
                    // Nothing was read, so the signature must not claim this screen
                    // is done — the next event may retry, still held back by
                    // ScreenCapture's own throttle and failure backoff.
                    if (!manual) lastOcrSignature = ""
                    // Throttle/interval codes are transient timing, not something
                    // the user can act on — nagging about them would be constant.
                    val transient = res.code == ScreenCapture.CODE_THROTTLED || res.code == 3
                    if (manual || !transient) overlay?.showError(res.humanMessage)
                }
                is ScreenCapture.Result.Ok -> {
                    ocr.scaleX = res.scaleX; ocr.scaleY = res.scaleY
                    ocr.originX = res.originX; ocr.originY = res.originY

                    // Re-measure bubble rectangles after the overlay-hide wait and
                    // the screenshot itself; one scroll tick can otherwise shift
                    // every crop. The same fresh geometry is used by local and
                    // remote OCR.
                    val effectiveRects = if (rects.isNotEmpty() && !manual) {
                        val fresh = rootInActiveWindow?.let {
                            collectFeishuBubbleRects(it, resources)
                        }
                        if (fresh.isNullOrEmpty()) rects else fresh
                    } else {
                        rects
                    }

                    val visionSelected = prefs.ocrEngine == Prefs.OCR_VISION
                    val visionReady = visionSelected &&
                        prefs.effectiveVisionKey().isNotBlank() &&
                        VisionClient.supportsVision(
                            prefs.visionBaseUrl.ifBlank { Prefs.DEFAULT_VISION_BASE },
                            prefs.visionModel
                        )

                    if (visionReady) {
                        ocrByVision(
                            res,
                            effectiveRects,
                            treeTitle,
                            pkg,
                            manual,
                            autoEligible
                        )
                    } else {
                        if (manual && visionSelected) {
                            overlay?.toast("视觉 OCR 未配置可用密钥，已使用本地识别")
                        }
                        runLocalOcr(
                            res.bitmap,
                            effectiveRects,
                            treeTitle,
                            pkg,
                            manual,
                            autoEligible
                        )
                    }
                }
            }
        }
    }

    /** Route one captured bitmap through the bundled on-device OCR path. */
    private fun runLocalOcr(
        bmp: Bitmap,
        rects: List<BubbleRect>,
        title: String?,
        pkg: String,
        manual: Boolean,
        autoEligible: Boolean
    ) {
        if (rects.isNotEmpty() && !manual) {
            ocrByRects(bmp, rects, title, pkg, autoEligible)
        } else {
            ocrWholeScreen(bmp, title, pkg, manual, autoEligible)
        }
    }

    /**
     * Remote vision OCR. Only the cropped chat-content region is encoded and
     * uploaded. A failed request or malformed/unlabelled answer falls back to
     * bundled ML Kit using the original bitmap, so remote OCR can never make the
     * local fallback unavailable.
     */
    private fun ocrByVision(
        res: ScreenCapture.Result.Ok,
        rects: List<BubbleRect>,
        title: String?,
        pkg: String,
        manual: Boolean,
        autoEligible: Boolean
    ) {
        val original = res.bitmap
        val crop = try {
            cropVisionRegion(res, rects)
        } catch (e: Exception) {
            Log.w(TAG, "vision crop failed: ${e.javaClass.simpleName}")
            null
        }

        if (crop == null) {
            runLocalOcr(original, rects, title, pkg, manual, autoEligible)
            return
        }

        try {
            worker.execute {
                var error: String? = null
                val messages = try {
                    val jpeg = VisionClient.encodeJpeg(crop, quality = 76)
                    val raw = VisionClient(prefs).extractDialog(jpeg)
                    VisionDialogParser.parse(raw)
                } catch (e: Exception) {
                    error = e.javaClass.simpleName
                    Log.w(TAG, "vision ocr failed: ${e.javaClass.simpleName}")
                    emptyList()
                } finally {
                    if (crop !== original) runCatching { crop.recycle() }
                }

                main.post {
                    if (messages.isEmpty()) {
                        if (manual) {
                            overlay?.toast(
                                if (error != null) "视觉 OCR 失败，已回退本地识别"
                                else "视觉 OCR 未返回可靠说话人标签，已回退本地识别"
                            )
                        }
                        runLocalOcr(
                            original,
                            rects,
                            title,
                            pkg,
                            manual,
                            autoEligible
                        )
                    } else {
                        runCatching { original.recycle() }
                        finishOcrSnapshot(
                            ChatSnapshot(title, messages, note = VISION_OCR_NOTE),
                            pkg,
                            manual,
                            autoEligible
                        )
                    }
                }
            }
        } catch (_: RejectedExecutionException) {
            if (crop !== original) runCatching { crop.recycle() }
            runCatching { original.recycle() }
            ocrBusy = false
        }
    }

    /**
     * Produce the smallest useful upload region.
     *
     * With known bubble rectangles (Feishu) upload their union plus a small
     * margin. Without geometry, reuse the same middle-of-window crop as local
     * whole-screen OCR, excluding the title/status area and input controls.
     */
    private fun cropVisionRegion(
        res: ScreenCapture.Result.Ok,
        rects: List<BubbleRect>
    ): Bitmap? {
        val bmp = res.bitmap
        val bounds = Rect()

        if (rects.isNotEmpty()) {
            var hasRect = false
            for (br in rects) {
                val mapped = Rect(
                    ((br.rect.left - res.originX) * res.scaleX).toInt(),
                    ((br.rect.top - res.originY) * res.scaleY).toInt(),
                    ((br.rect.right - res.originX) * res.scaleX).toInt(),
                    ((br.rect.bottom - res.originY) * res.scaleY).toInt()
                )
                if (!mapped.intersect(0, 0, bmp.width, bmp.height)) continue
                if (mapped.width() < 4 || mapped.height() < 4) continue
                if (!hasRect) {
                    bounds.set(mapped)
                    hasRect = true
                } else {
                    bounds.union(mapped)
                }
            }
            if (hasRect) {
                val mx = (bmp.width * 0.025f).toInt().coerceAtLeast(8)
                val my = (bmp.height * 0.015f).toInt().coerceAtLeast(8)
                bounds.left = (bounds.left - mx).coerceAtLeast(0)
                bounds.right = (bounds.right + mx).coerceAtMost(bmp.width)
                bounds.top = (bounds.top - my).coerceAtLeast(0)
                bounds.bottom = (bounds.bottom + my).coerceAtMost(bmp.height)
            } else {
                bounds.set(
                    0,
                    (bmp.height * TOP_CROP).toInt(),
                    bmp.width,
                    (bmp.height * BOTTOM_CROP).toInt()
                )
            }
        } else {
            bounds.set(
                0,
                (bmp.height * TOP_CROP).toInt(),
                bmp.width,
                (bmp.height * BOTTOM_CROP).toInt()
            )
        }

        if (bounds.width() < 8 || bounds.height() < 8) return null
        return Bitmap.createBitmap(
            bmp,
            bounds.left,
            bounds.top,
            bounds.width(),
            bounds.height()
        )
    }

    /** One OCR pass per bubble rectangle; each rect becomes exactly one message. */
    private fun ocrByRects(
        bmp: Bitmap,
        rects: List<BubbleRect>,
        title: String?,
        pkg: String,
        autoEligible: Boolean
    ) {
        val sx = ocr.scaleX; val sy = ocr.scaleY
        // Screen -> bitmap: drop the window origin first. A window shot does not
        // start at (0,0) in split screen or when it excludes the status bar.
        val ox = ocr.originX; val oy = ocr.originY
        val out = arrayOfNulls<Msg>(rects.size)
        var remaining = rects.size
        rects.forEachIndexed { i, br ->
            val region = Rect(
                ((br.rect.left - ox) * sx).toInt(), ((br.rect.top - oy) * sy).toInt(),
                ((br.rect.right - ox) * sx).toInt(), ((br.rect.bottom - oy) * sy).toInt())
            ocr.recognize(bmp, region) { lines ->
                val text = cleanBubbleText(lines.joinToString(" ") { it.text })
                if (text.isNotEmpty()) out[i] = Msg(br.side, text)
                remaining--
                if (remaining == 0) {
                    runCatching { bmp.recycle() }
                    finishOcrSnapshot(
                        ChatSnapshot(title, out.filterNotNull()),
                        pkg,
                        manual = false,
                        autoEligible = autoEligible
                    )
                }
            }
        }
    }

    /** Whole screen minus the top bar and the input area, grouped by line gaps. */
    private fun ocrWholeScreen(
        bmp: Bitmap,
        treeTitle: String?,
        pkg: String,
        manual: Boolean,
        autoEligible: Boolean
    ) {
        val region = Rect(0, (bmp.height * TOP_CROP).toInt(), bmp.width, (bmp.height * BOTTOM_CROP).toInt())
        ocr.recognize(bmp, region) { lines ->
            runCatching { bmp.recycle() }
            val msgs = groupOcrLines(lines)
            val title = treeTitle?.takeIf { it.isNotBlank() }
                ?: lines.firstOrNull()?.text?.trim()?.take(24)
            finishOcrSnapshot(
                ChatSnapshot(title, msgs, note = OCR_NOTE),
                pkg,
                manual,
                autoEligible
            )
        }
    }

    /**
     * OCR lines → "bubbles": a gap larger than 1.2x the previous line's height
     * starts a new one. Side is unknowable from a flat screen read, so every
     * group is filed as the other person (and [OCR_NOTE] says so on the panel).
     */
    private fun groupOcrLines(lines: List<OcrLine>): List<Msg> {
        val usable = lines
            .filter { it.text.isNotBlank() && !PURE_TIME.matches(it.text.trim()) }
            .sortedBy { it.bounds.top }
        val out = ArrayList<Msg>()
        val buf = StringBuilder()
        var prev: OcrLine? = null
        for (l in usable) {
            val p = prev
            if (p != null) {
                val gap = l.bounds.top - p.bounds.bottom
                val lineHeight = maxOf(p.bounds.height(), 1)
                if (gap > lineHeight * 1.2f) {
                    if (buf.isNotEmpty()) { out.add(Msg("other", buf.toString())); buf.setLength(0) }
                }
            }
            if (buf.isNotEmpty()) buf.append(' ')
            buf.append(l.text.trim())
            prev = l
        }
        if (buf.isNotEmpty()) out.add(Msg("other", buf.toString()))
        return out
    }

    /** Strip the read receipt and the timestamp Feishu glues onto a bubble. */
    private fun cleanBubbleText(raw: String): String {
        var t = raw.trim()
        var changed = true
        while (changed && t.isNotEmpty()) {
            changed = false
            for (tail in arrayOf("已读", "未读")) {
                if (t.endsWith(tail)) { t = t.removeSuffix(tail).trim(); changed = true }
            }
            TAIL_TIME.find(t)?.let { t = t.substring(0, it.range.first).trim(); changed = true }
        }
        return t
    }

    /** Shared tail of both OCR paths: dedupe, then analyze or park the bubble. */
    private fun finishOcrSnapshot(
        snapshot: ChatSnapshot,
        pkg: String,
        manual: Boolean,
        autoEligible: Boolean
    ) {
        ocrBusy = false
        // Counts only — OCR'd chat text never goes to logcat or diagnostics.
        DiagnosticsStore.record(
            this,
            packageName = pkg,
            adapter = adapterLabel(pkg),
            source = if (snapshot.note == VISION_OCR_NOTE) "ocr_vision" else "ocr_local",
            titlePresent = !isTransientTitle(snapshot.title),
            messageCount = snapshot.messages.size,
            latestFrom = snapshot.latestFrom,
            status = if (snapshot.messages.isEmpty()) "empty" else "ok"
        )
        Log.i(TAG, "ocr[$pkg] msgs=${snapshot.messages.size} manual=$manual")
        if (snapshot.messages.isEmpty()) {
            if (manual) overlay?.showError("这一屏没认出文字")
            return
        }
        if (!prefs.isAllowed(snapshot.title)) { overlay?.hide(); return }

        if (pkg.isNotEmpty() && pkg != activePkg) { activePkg = pkg; lastSignature = "" }
        currentSnapshot = snapshot
        val sig = snapshot.signature()
        // Manual taps always re-run; the automatic path dedupes like the tree path.
        if (!manual && sig == lastSignature) {
            if (overlay?.isShowing() != true) overlay?.showIdle(snapshot.title)
            return
        }
        // Same rule as the tree path: past this point the conversation is either
        // new or being force-refreshed. Invalidate late async callbacks first.
        invalidateAnalysis()
        overlay?.resetForNewConversation()
        lastSignature = sig

        val auto = autoEligible && prefs.ocrAutoAnalyze &&
            prefs.autoAnalyze && snapshot.latestFrom == "other"
        if (manual || auto) {
            pendingSnapshot = snapshot
            pendingHistoryHint = if (auto) {
                HistoryCaptureHint.NEWEST_SCREEN
            } else {
                HistoryCaptureHint.CONSERVATIVE
            }
            main.removeCallbacks(debounce)
            runAnalysis()
        } else {
            overlay?.setNote(snapshot.note)
            overlay?.showIdle(snapshot.title)
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        invalidateAnalysis()
        main.removeCallbacks(windowSettleFast)
        main.removeCallbacks(windowSettleLate)
        main.removeCallbacks(contentSettle)
        main.removeCallbacks(restoreSettleFast)
        main.removeCallbacks(restoreSettleLate)
        // Keep the process-wide overlay alive; only remove callbacks that require
        // this AccessibilityService instance.
        overlay?.onManualAnalyze = null
        overlay?.onOcrCapture = null
        overlay = null
        worker.shutdownNow()
        maintenanceWorker.shutdownNow()
    }

    companion object {
        private const val TAG = "JEVASSIST"
        private const val WECHAT_PKG = "com.tencent.mm"

        /** Whole-screen OCR keeps the middle: no action bar, no input area. */
        private const val TOP_CROP = 0.12f
        private const val BOTTOM_CROP = 0.84f

        /** Said on the panel whenever a snapshot came from flat-screen OCR. */
        private const val OCR_NOTE = "OCR 未分边，把全部消息当作对方所说"

        /** Explicit privacy note for the opt-in remote vision OCR path. */
        private const val VISION_OCR_NOTE =
            "视觉 API OCR · 已上传裁剪后的聊天区域到你配置的视觉服务商"

        private val PURE_TIME = Regex("""\d{1,2}[:：]\d{2}""")
        private val TAIL_TIME = Regex("""\d{1,2}[:：]\d{2}$""")

        /** Transient placeholder titles apps show while a chat page is still
         *  connecting/loading — see [isTransientTitle]. Matched as a substring,
         *  case-insensitive, after trimming a trailing ellipsis. */
        private val TRANSIENT_TITLE_WORDS = listOf(
            "连接中", "正在连接", "未连接", "Connecting",
            "加载中", "Loading", "同步中", "Syncing"
        )
    }
}
