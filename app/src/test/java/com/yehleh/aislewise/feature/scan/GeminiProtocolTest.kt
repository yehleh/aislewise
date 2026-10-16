package com.yehleh.aislewise.feature.scan

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class GeminiProtocolTest {

    private fun response(text: String) = """
        {"candidates":[{"content":{"role":"model","parts":[{"text":${Json.encodeToString(String.serializer(), text)}}]}}]}
    """.trimIndent()

    @Test fun `request carries prompt, images and a schema limited to the app's sections`() {
        val body = Json.parseToJsonElement(GeminiProtocol.buildRequest(listOf("AAA", "BBB"), listOf("Dairy", "Produce"))).jsonObject
        val parts = body["contents"]!!.jsonArray[0].jsonObject["parts"]!!.jsonArray
        assertEquals(3, parts.size)
        assertTrue(parts[0].jsonObject["text"]!!.jsonPrimitive.content.contains("Dairy, Produce"))
        assertEquals("BBB", parts[2].jsonObject["inlineData"]!!.jsonObject["data"]!!.jsonPrimitive.content)
        val enum = body["generationConfig"]!!.jsonObject["responseSchema"]!!.jsonObject["items"]!!.jsonObject["properties"]!!
            .jsonObject["section"]!!.jsonObject["enum"]!!.jsonArray.map { it.jsonPrimitive.content }
        assertEquals(listOf("Dairy", "Produce"), enum)
    }

    @Test fun `response is parsed, trimmed and deduplicated`() {
        val items = GeminiProtocol.parseResponse(
            response(
                """[{"name":"Eggs","quantity":"12","section":"Dairy"},{"name":" egg "},{"name":"Bread","quantity":""},{"name":""}]""",
            ),
        )
        assertEquals(
            listOf(ParsedItem("Eggs", "12", "Dairy"), ParsedItem("Bread", null, null)),
            items,
        )
    }

    @Test fun `blocked prompt is reported`() {
        val e = runCatching { GeminiProtocol.parseResponse("""{"promptFeedback":{"blockReason":"SAFETY"}}""") }.exceptionOrNull()
        assertTrue(e is ScanException.Api && e.message!!.contains("SAFETY"))
    }

    private fun parser(server: MockWebServer, models: List<String>, http: OkHttpClient = OkHttpClient()) =
        GeminiListImageParser(
            http,
            GeminiConfig(apiKey = "test-key", models = models, baseUrl = server.url("/v1beta").toString().trimEnd('/')),
            ImagePreprocessor(ApplicationProvider.getApplicationContext<Context>()),
        )

    @Test fun `request goes to the first model with the api key header`() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(response("""[{"name":"Milk"}]""")))
            assertEquals(listOf(ParsedItem("Milk")), parser(server, listOf("lite", "backup")).parse(emptyList(), listOf("Dairy")).getOrThrow())

            val request = server.takeRequest()
            assertEquals("/v1beta/models/lite:generateContent", request.path)
            assertEquals("test-key", request.getHeader("x-goog-api-key"))
            assertEquals(1, server.requestCount)
        }
    }

    @Test fun `overloaded or rate limited models fall back to the next one`() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(503).setBody("""{"error":{"message":"high demand"}}"""))
            server.enqueue(MockResponse().setResponseCode(429))
            server.enqueue(MockResponse().setBody(response("""[{"name":"Eggs"}]""")))

            val items = parser(server, listOf("a", "b", "c")).parse(emptyList(), listOf("Dairy")).getOrThrow()

            assertEquals(listOf(ParsedItem("Eggs")), items)
            assertEquals(
                listOf("/v1beta/models/a:generateContent", "/v1beta/models/b:generateContent", "/v1beta/models/c:generateContent"),
                List(3) { server.takeRequest().path },
            )
        }
    }

    @Test fun `when every model is busy the last busy error is reported`() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(429))
            server.enqueue(MockResponse().setResponseCode(503))
            val error = parser(server, listOf("a", "b")).parse(emptyList(), listOf("Dairy")).exceptionOrNull()
            assertTrue(error is ScanException.Busy)
        }
    }

    @Test fun `a bad request is reported without trying other models`() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(400).setBody("""{"error":{"code":400,"message":"API key not valid."}}"""))
            val error = parser(server, listOf("a", "b")).parse(emptyList(), listOf("Dairy")).exceptionOrNull()
            assertEquals("API key not valid.", error?.message)
            assertEquals(1, server.requestCount)
        }
    }

    @Test fun `a model that stalls times out and falls back`() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(response("[]")).setHeadersDelay(2, TimeUnit.SECONDS))
            server.enqueue(MockResponse().setBody(response("""[{"name":"Bread"}]""")))
            val http = OkHttpClient.Builder().callTimeout(300, TimeUnit.MILLISECONDS).build()

            val items = parser(server, listOf("slow", "fast"), http).parse(emptyList(), listOf("Bakery")).getOrThrow()

            assertEquals(listOf(ParsedItem("Bread")), items)
        }
    }

    @Test fun `missing api key is reported without a network call`() = runTest {
        val parser = GeminiListImageParser(
            OkHttpClient(),
            GeminiConfig(apiKey = "", models = listOf("m")),
            ImagePreprocessor(ApplicationProvider.getApplicationContext()),
        )
        assertTrue(!parser.isConfigured)
        assertTrue(parser.parse(emptyList(), emptyList()).exceptionOrNull() is ScanException.NotConfigured)
    }
}
