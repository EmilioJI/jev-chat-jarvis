package com.jev.probe

import android.app.Activity
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast

/**
 * Foreground, user-initiated clipboard handoff.
 *
 * Android 10+ restricts background clipboard reads. The overlay launches this
 * transparent Activity only after the user taps "分析剪贴板"; the Activity reads
 * the clipboard while foreground, broadcasts the text only inside this package,
 * and immediately finishes. Nothing is persisted here.
 */
class ClipboardImportActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)

        val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val clip = cm.primaryClip
        val text = if (
            clip != null &&
            clip.itemCount > 0 &&
            (clip.description.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) ||
                clip.description.hasMimeType(ClipDescription.MIMETYPE_TEXT_HTML))
        ) {
            clip.getItemAt(0).coerceToText(this)?.toString().orEmpty().trim()
        } else {
            ""
        }

        if (text.isBlank()) {
            Toast.makeText(this, "剪贴板没有可分析文字", Toast.LENGTH_SHORT).show()
        } else {
            sendBroadcast(
                Intent(ACTION_CLIPBOARD_READY)
                    .setPackage(packageName)
                    .putExtra(EXTRA_TEXT, text.take(MAX_TEXT_CHARS))
            )
        }
        finish()
        overridePendingTransition(0, 0)
    }

    companion object {
        const val ACTION_CLIPBOARD_READY = "com.jev.probe.action.CLIPBOARD_READY"
        const val EXTRA_TEXT = "text"
        private const val MAX_TEXT_CHARS = 12_000
    }
}
