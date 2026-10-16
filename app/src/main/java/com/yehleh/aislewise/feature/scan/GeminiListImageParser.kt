package com.yehleh.aislewise.feature.scan

import android.net.Uri
import android.util.Base64
import com.yehleh.aislewise.data.normalize.ItemNameNormalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.io.InterruptedIOException
import javax.inject.Inject

data class GeminiConfig(
    val apiKey: String,
    /** Tried in order; the next one is used when a model is overloaded, rate limited or times out. */
    val models: List<String>,
    val baseUrl: String = "https://generativelanguage.googleapis.com/v1beta",
)

/** Reads shopping list photos with the Gemini API (REST, structured JSON output). */
class GeminiListImageParser @Inject constructor(
    private val http: OkHttpClient,
    private val config: GeminiConfig,
    private val images: ImagePreprocessor,
) : ListImageParser {

    override val isConfigured: Boolean get() = config.apiKey.isNotBlank()

    override suspend fun parse(images: List<Uri>, sectionNames: List<String>): Result<List<ParsedItem>> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (!isConfigured) throw ScanException.NotConfigured()
                val jpegs = images.map { this@GeminiListImageParser.images.toJpeg(it) ?: throw ScanException.UnreadableImage() }
                val body = GeminiProtocol.buildRequest(jpegs.map { Base64.encodeToString(it, Base64.NO_WRAP) }, sectionNames)
                GeminiProtocol.parseResponse(postWithFallback(body))
            }
        }

    private fun postWithFallback(body: String): String {
        var lastError: ScanException? = null
        for (model in config.models) {
            try {
                return post(model, body)
            } catch (e: ScanException) {
                if (!e.isTransient) throw e
                lastError = e
            }
        }
        throw lastError ?: ScanException.Api("No Gemini model is configured.")
    }

    private fun post(model: String, body: String): String {
        val request = Request.Builder()
            .url("${config.baseUrl}/models/$model:generateContent")
            .header("x-goog-api-key", config.apiKey)
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
        val response = try {
            http.newCall(request).execute()
        } catch (e: InterruptedIOException) {
            throw ScanException.Busy(e)
        } catch (e: IOException) {
            throw ScanException.Network(e)
        }
        response.use {
            val text = it.body?.string().orEmpty()
            when {
                it.code == 429 -> throw ScanException.RateLimited()
                it.code >= 500 -> throw ScanException.Busy()
                !it.isSuccessful -> throw ScanException.Api(GeminiProtocol.errorMessage(text) ?: "Request failed (HTTP ${it.code}).")
            }
            return text
        }
    }
}

/** Request/response shapes for generateContent, kept free of Android types so they can be unit tested. */
internal object GeminiProtocol {
    private val json = Json { ignoreUnknownKeys = true }

    fun prompt(sectionNames: List<String>) = """
        These photos show a shopping list, usually handwritten. Several photos may show different parts of the
        same list or different lists. Extract every item to buy.
        - Correct obvious spelling mistakes and expand abbreviations, but keep the item the writer meant.
        - Capitalize the item name naturally (e.g. "Eggs", "Whole milk", "Paper towels").
        - Put any amount or size written next to the item in "quantity" (e.g. "2", "1 lb", "2 gallons"); otherwise omit it.
        - Skip crossed-out items, headings, dates and doodles.
        - List an item only once even if it appears in several photos.
        - Choose the best grocery store section for each item from: ${sectionNames.joinToString(", ")}.
        If there is no shopping list in the photos, return an empty array.
    """.trimIndent()

    fun buildRequest(base64Jpegs: List<String>, sectionNames: List<String>): String = buildJsonObject {
        putJsonArray("contents") {
            addJsonObject {
                put("role", "user")
                putJsonArray("parts") {
                    addJsonObject { put("text", prompt(sectionNames)) }
                    base64Jpegs.forEach { data ->
                        addJsonObject {
                            putJsonObject("inlineData") {
                                put("mimeType", "image/jpeg")
                                put("data", data)
                            }
                        }
                    }
                }
            }
        }
        putJsonObject("generationConfig") {
            put("temperature", 0)
            put("responseMimeType", "application/json")
            putJsonObject("responseSchema") {
                put("type", "ARRAY")
                putJsonObject("items") {
                    put("type", "OBJECT")
                    putJsonObject("properties") {
                        putJsonObject("name") { put("type", "STRING") }
                        putJsonObject("quantity") { put("type", "STRING") }
                        putJsonObject("section") {
                            put("type", "STRING")
                            if (sectionNames.isNotEmpty()) putJsonArray("enum") { sectionNames.forEach { add(it) } }
                        }
                    }
                    putJsonArray("required") { add("name") }
                }
            }
        }
    }.toString()

    fun parseResponse(body: String): List<ParsedItem> {
        val root = json.parseToJsonElement(body).jsonObject
        val candidate = (root["candidates"] as? JsonArray)?.firstOrNull()?.jsonObject
        if (candidate == null) {
            val blocked = (root["promptFeedback"] as? JsonObject)?.get("blockReason")?.jsonPrimitive?.contentOrNull
            throw ScanException.Api(if (blocked != null) "The photos were rejected ($blocked)." else "No answer came back. Try again.")
        }
        val text = (candidate["content"] as? JsonObject)?.get("parts")?.jsonArray
            ?.mapNotNull { (it as? JsonObject)?.get("text")?.jsonPrimitive?.contentOrNull }
            ?.joinToString("")
            .orEmpty()
        if (text.isBlank()) throw ScanException.Api("No answer came back. Try again.")

        val items = json.parseToJsonElement(text).jsonArray.mapNotNull { el ->
            val obj = el as? JsonObject ?: return@mapNotNull null
            val name = obj.string("name")?.trim().orEmpty()
            if (name.isEmpty()) return@mapNotNull null
            ParsedItem(name = name, quantity = obj.string("quantity")?.trim()?.ifEmpty { null }, suggestedSection = obj.string("section"))
        }
        // The model is asked to dedupe, but enforce it with the same key the catalog uses.
        return items.distinctBy { ItemNameNormalizer.normalize(it.name) }
    }

    fun errorMessage(body: String): String? = runCatching {
        json.parseToJsonElement(body).jsonObject["error"]?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull
    }.getOrNull()

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
}
