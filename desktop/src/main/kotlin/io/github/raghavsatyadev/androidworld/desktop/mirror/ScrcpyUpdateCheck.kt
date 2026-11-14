package io.github.raghavsatyadev.androidworld.desktop.mirror

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Compares the installed scrcpy with the latest release on GitHub, so the Screen tab can warn when
 * it is out of date (an old scrcpy-server sends a stream the mirror cannot read). Asks GitHub once
 * per app run; offline or on any error it stays quiet.
 */
object ScrcpyUpdateCheck {
    private const val LATEST_RELEASE = "https://api.github.com/repos/Genymobile/scrcpy/releases/latest"
    private val VERSION = Regex("""\d+(\.\d+)*""")

    @Volatile private var latest: String? = null

    /** A warning to show, or `null` when [installed] is the latest release or the check failed. */
    fun warning(installed: String): String? {
        val current = VERSION.find(installed)?.value ?: return null
        val newest = latest ?: fetchLatest()?.also { latest = it } ?: return null
        if (compare(current, newest) >= 0) return null
        return "scrcpy $current is installed, but the latest is $newest. Update scrcpy if screen share fails."
    }

    private fun fetchLatest(): String? = runCatching {
        val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()
        val request = HttpRequest.newBuilder(URI(LATEST_RELEASE))
            .timeout(Duration.ofSeconds(5))
            .header("Accept", "application/vnd.github+json")
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() != 200) return null
        val tag = Json.parseToJsonElement(response.body()).jsonObject["tag_name"]?.jsonPrimitive?.content
        tag?.let { VERSION.find(it)?.value }
    }.getOrNull()

    /** Compares dotted versions part by part; missing parts count as 0 (so 5.0 == 5.0.0). */
    internal fun compare(a: String, b: String): Int {
        val left = a.split('.').map { it.toIntOrNull() ?: 0 }
        val right = b.split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(left.size, right.size)) {
            val diff = left.getOrElse(i) { 0 } - right.getOrElse(i) { 0 }
            if (diff != 0) return diff
        }
        return 0
    }
}
