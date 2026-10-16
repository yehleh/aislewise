package com.yehleh.aislewise.feature.scan

import android.net.Uri

/** One item read off a photo of a shopping list. */
data class ParsedItem(
    val name: String,
    val quantity: String? = null,
    /** One of the section names passed to [ListImageParser.parse], if the parser could tell. */
    val suggestedSection: String? = null,
)

/** Turns photos of a (usually handwritten) shopping list into items. */
interface ListImageParser {
    /** False when the parser can't run, e.g. no API key is configured. */
    val isConfigured: Boolean

    suspend fun parse(images: List<Uri>, sectionNames: List<String>): Result<List<ParsedItem>>
}

sealed class ScanException(message: String) : Exception(message) {
    /** Worth retrying with another model: the problem is load on Google's side, not the request. */
    open val isTransient: Boolean get() = false

    class NotConfigured : ScanException("Photo reading isn't set up. Add GEMINI_API_KEY to local.properties and rebuild.")
    class RateLimited : ScanException("Too many requests right now. Wait a minute and try again.") {
        override val isTransient get() = true
    }
    class Busy(cause: Throwable? = null) : ScanException("Gemini is busy right now. Try again in a moment.") {
        init { cause?.let { initCause(it) } }
        override val isTransient get() = true
    }
    class Network(cause: Throwable) : ScanException("Couldn't reach the server. Check your connection and try again.") {
        init { initCause(cause) }
    }
    class Api(message: String) : ScanException(message)
    class UnreadableImage : ScanException("Couldn't open one of the photos.")
}
