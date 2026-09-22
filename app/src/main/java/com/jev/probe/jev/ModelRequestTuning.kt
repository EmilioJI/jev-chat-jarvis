package com.jev.probe.jev

import org.json.JSONObject

/**
 * Provider/model-specific latency policy applied centrally before every
 * OpenAI-compatible request leaves the app.
 *
 * Product policy:
 * - DeepSeek deepseek-flash: force non-thinking mode.
 * - GLM-5.3-Flash: keep thinking enabled but force the lightest supported
 *   reasoning budget.
 *
 * Matching also accepts routed names such as deepseek/deepseek-flash and
 * zai-org/GLM-5.3-Flash.
 */
internal object ModelRequestTuning {

    internal data class Profile(
        val disableThinking: Boolean = false,
        val reasoningEffort: String? = null
    )

    internal fun profileFor(model: String): Profile {
        val m = model.trim().lowercase()
        return when {
            m == "deepseek-flash" || m.endsWith("/deepseek-flash") ->
                Profile(disableThinking = true)

            m == "glm-5.3-flash" || m.endsWith("/glm-5.3-flash") ->
                Profile(reasoningEffort = "low")

            else -> Profile()
        }
    }

    fun apply(body: JSONObject): JSONObject {
        val profile = profileFor(body.optString("model"))
        when {
            profile.disableThinking -> {
                body.put("thinking", JSONObject().put("type", "disabled"))
                body.remove("reasoning_effort")
            }

            profile.reasoningEffort != null -> {
                // GLM-5.3-Flash is an always-thinking model. Never carry over a
                // generic "thinking.disabled" knob from another provider.
                body.remove("thinking")
                body.put("reasoning_effort", profile.reasoningEffort)
            }
        }
        return body
    }
}
