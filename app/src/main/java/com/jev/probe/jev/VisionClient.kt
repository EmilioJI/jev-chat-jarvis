package com.jev.probe.jev

import android.graphics.Bitmap
import android.util.Base64
import com.jev.probe.core.Prefs
import java.io.ByteArrayOutputStream
import org.json.JSONArray
import org.json.JSONObject

/**
 * The vision route: an OpenAI-compatible `/chat/completions` endpoint that
 * accepts `image_url` content parts. When the user explicitly selects the
 * remote Vision OCR engine, the live screenshot pipeline sends only a cropped
 * chat-content region through this client. Local ML Kit remains the default.
 *
 * Reads visionBaseUrl / visionKey / visionModel from [Prefs]. The base URL does
 * not inherit from the reply route; the key still falls back reply -> judge.
 * Current DeepSeek Flash is multimodal, but older/non-Flash DeepSeek model names
 * are rejected on the official DeepSeek host to avoid sending image_url to an
 * incompatible model.
 *
 * Wire format notes that cost real debugging time:
 * - JPEG, not PNG: a screenshot as PNG base64 is several times larger.
 * - `Base64.NO_WRAP`: Android's default inserts newlines, which corrupts the
 *   data URL.
 * - The image part goes BEFORE the text part — DashScope's compatible-mode
 *   rejects the other order.
 */
class VisionClient(private val prefs: Prefs) {

    /**
     * Send a cropped chat screenshot and get the transcribed dialog back as
     * labelled plain text. The caller parses only explicit 我/对方 lines.
     *
     * @param imageBase64Jpeg base64 of a JPEG, without the `data:` prefix.
     */
    fun extractDialog(imageBase64Jpeg: String): String = ask(
        imageBase64Jpeg,
        "你是聊天截图转写助手。只转写图中真实可见的聊天气泡，不要转写标题栏、状态栏、输入框、按钮或通知。" +
            "按从上到下顺序，每个气泡一行；必须严格写成 `我：正文` 或 `对方：正文`。" +
            "无法确定说话人时不要猜，直接省略该气泡。不要概括、补全、改写或解释，只输出转写结果。"
    )

    /** Generic single-question call against the image (used by the settings test). */
    fun ask(imageBase64Jpeg: String, prompt: String): String {
        val url = prefs.visionEndpoint()
        val key = prefs.effectiveVisionKey()
        if (key.isBlank()) throw ApiException(Route.VISION, null, "视觉接口未配置密钥")
        // Image first, then text: DashScope compatible-mode requires this order.
        val content = JSONArray()
            .put(JSONObject()
                .put("type", "image_url")
                .put("image_url", JSONObject().put("url", "data:image/jpeg;base64,$imageBase64Jpeg")))
            .put(JSONObject().put("type", "text").put("text", prompt))
        val messages = JSONArray().put(
            JSONObject().put("role", "user").put("content", content))
        val body = JSONObject()
            .put("model", prefs.visionModel)
            .put("messages", messages)
            .put("temperature", 0.0)
        val resp = HttpJson.post(url, key, body, Route.VISION, HttpJson.headersFor(url))
        return resp.optJSONArray("choices")?.optJSONObject(0)
            ?.optJSONObject("message")?.optString("content") ?: ""
    }

    companion object {
        /** Bitmap -> JPEG base64 in the exact form [ask] expects. */
        fun encodeJpeg(bitmap: Bitmap, quality: Int = 80): String {
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        }

        /** DeepSeek official vision is supported only by the current Flash model. */
        fun supportsVision(baseUrl: String, model: String): Boolean {
            if (!baseUrl.contains("api.deepseek.com", ignoreCase = true)) return true
            val m = model.trim().lowercase()
            return m == "deepseek-flash" || m.endsWith("/deepseek-flash")
        }
    }
}
