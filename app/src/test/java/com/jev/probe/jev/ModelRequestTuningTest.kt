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
    fun otherModelsAreUntouched() {
        val p = ModelRequestTuning.profileFor("bocha-jev-v1")
        assertFalse(p.disableThinking)
        assertNull(p.reasoningEffort)
    }
}
