package com.jev.probe.core.kb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ContactProfileScopeTest {

    private val a = Contact(
        id = "a",
        name = "张三",
        apps = listOf("com.tencent.mm@UserHandle{0}")
    )
    private val b = Contact(
        id = "b",
        name = "张三",
        apps = listOf("com.tencent.mm@UserHandle{999}")
    )
    private val legacy = Contact(
        id = "legacy",
        name = "张三",
        apps = listOf("com.tencent.mm")
    )

    @Test
    fun scopedWechatPrefersExactProfile() {
        assertEquals(
            "a",
            KbStore.chooseContactForApp(
                listOf(legacy, b, a),
                "com.tencent.mm@UserHandle{0}"
            )?.id
        )
        assertEquals(
            "b",
            KbStore.chooseContactForApp(
                listOf(a, legacy, b),
                "com.tencent.mm@UserHandle{999}"
            )?.id
        )
    }

    @Test
    fun scopedWechatNeverFallsBackToOtherProfileOrLegacyContact() {
        assertNull(
            KbStore.chooseContactForApp(
                listOf(legacy, a),
                "com.tencent.mm@UserHandle{999}"
            )
        )
    }

    @Test
    fun ordinaryAppsKeepLegacyFallbackBehavior() {
        assertEquals(
            "legacy",
            KbStore.chooseContactForApp(
                listOf(legacy, a),
                "com.tencent.mobileqq"
            )?.id
        )
    }
}
