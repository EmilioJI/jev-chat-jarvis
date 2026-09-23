package com.jev.probe.jev

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelRequestTuningTest {

    @Test
    fun deepSeekFlashDisablesThinking() {
        val p = ModelRequestTuning.profileFor("deepseek-flash")
        assertTrue(p.disableThinking)
        assertNull(p.reasoningEffort)
    }

    @Test
    fun routedDeepSeekFlashAlsoDisablesThinking() {
        val p = ModelRequestTuning.profileFor("deepseek/deepseek-flash")
        assertTrue(p.disableThinking)
    }

    @Test
    fun glm53FlashForcesLowReasoning() {
        val p = ModelRequestTuning.profileFor("glm-5.3-flash")
        assertFalse(p.disableThinking)
        assertEquals("low", p.reasoningEffort)
    }

    @Test
    fun routedGlm53FlashAlsoForcesLowReasoning() {
        val p = ModelRequestTuning.profileFor("zai-org/GLM-5.3-Flash")
        assertEquals("low", p.reasoningEffort)
    }


    @Test
    fun deepSeekFlashMutatesRequestBodyToDisabledThinking() {
        val body = org.json.JSONObject()
            .put("model", "deepseek-flash")
            .put("reasoning_effort", "max")
        ModelRequestTuning.apply(body)
        assertEquals("disabled", body.getJSONObject("thinking").getString("type"))
        assertFalse(body.has("reasoning_effort"))
    }

    @Test
    fun glm53FlashMutatesRequestBodyToLowReasoning() {
        val body = org.json.JSONObject()
            .put("model", "glm-5.3-flash")
            .put("thinking", org.json.JSONObject().put("type", "disabled"))
        ModelRequestTuning.apply(body)
        assertEquals("low", body.getString("reasoning_effort"))
        assertFalse(body.has("thinking"))
    }

    @Test
    fun otherModelsAreUntouched() {
        val p = ModelRequestTuning.profileFor("bocha-jev-v1")
        assertFalse(p.disableThinking)
        assertNull(p.reasoningEffort)
    }
}
