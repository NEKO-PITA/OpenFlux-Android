package ru.openflux.deployer.util

import android.util.Base64
import java.net.URI

object UrlNormalizer {

    private val editRegex = Regex("""^https://(docs|disk)\.yandex\.(ru|com|by|kz|uz)/edit/d/([A-Za-z0-9_-]{16,200})$""")
    private val extractIdRegex = Regex("""https?://(?:docs|disk)\.yandex\.(?:ru|com|by|kz|uz)/edit/d/([A-Za-z0-9_-]{16,200})""")

    fun normalize(input: String): String {
        var clean = input.trim()
        if (clean.isEmpty()) return ""

        if (clean.contains("retpath=")) {
            try {
                val uri = URI(clean)
                val query = uri.query ?: ""
                for (param in query.split("&")) {
                    val pair = param.split("=")
                    if (pair.size == 2 && pair[0] == "retpath") {
                        val rawB64 = pair[1].substringBefore("_")
                        val decodedBytes = Base64.decode(rawB64, Base64.DEFAULT or Base64.URL_SAFE)
                        val decodedStr = String(decodedBytes, Charsets.UTF_8)
                        if (decodedStr.contains("/edit/d/")) {
                            clean = decodedStr
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        if (clean.contains("#")) {
            clean = clean.substringBefore("#")
        }
        if (clean.contains("?")) {
            clean = clean.substringBefore("?")
        }
        clean = clean.trimEnd('/')

        val match = extractIdRegex.find(clean)
        if (match != null) {
            val docId = match.groupValues[1]
            return "https://docs.yandex.ru/edit/d/$docId"
        }

        return clean
    }

    fun isValid(url: String): Boolean {
        return editRegex.matches(url)
    }
}
