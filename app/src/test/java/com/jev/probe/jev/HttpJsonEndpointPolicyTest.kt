package com.jev.probe.jev

import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class HttpJsonEndpointPolicyTest {

    @Test
    fun httpsProvidersAreAccepted() {
        HttpJson.validateEndpoint("https://openrouter.ai/api/alpha/decisions", Route.JUDGE)
        HttpJson.validateEndpoint("https://open.bigmodel.cn/api/paas/v4/chat/completions", Route.REPLY)
        HttpJson.validateEndpoint("https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions", Route.VISION)
    }

    @Test
    fun loopbackHttpIsAcceptedForDevelopment() {
        HttpJson.validateEndpoint("http://localhost:8080/v1/chat/completions", Route.REPLY)
        HttpJson.validateEndpoint("http://127.0.0.1:8080/v1/chat/completions", Route.REPLY)
        HttpJson.validateEndpoint("http://[::1]:8080/v1/chat/completions", Route.REPLY)
    }

    @Test
    fun remoteCleartextHttpIsRejected() {
        assertRejected("http://example.com/v1/chat/completions", "仅允许 HTTPS")
    }

    @Test
    fun embeddedCredentialsAndFragmentsAreRejected() {
        assertRejected("https://user:pass@example.com/v1", "用户名或密码")
        assertRejected("https://example.com/v1#secret", "#fragment")
    }

    @Test
    fun unsupportedSchemesAndMalformedHostsAreRejected() {
        assertRejected("ftp://example.com/model", "不支持的接口协议")
        assertRejected("https:///missing-host", "缺少协议或主机名")
    }

    private fun assertRejected(url: String, snippetPart: String) {
        try {
            HttpJson.validateEndpoint(url, Route.JUDGE)
            fail("Expected endpoint rejection for $url")
        } catch (e: ApiException) {
            assertTrue(
                "Expected snippet to contain '$snippetPart' but was '${e.snippet}'",
                e.snippet.contains(snippetPart)
            )
        }
    }
}
