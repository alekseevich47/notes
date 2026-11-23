package com.alekseevich47.notes.domain

import java.net.URI
import java.util.Locale

/**
 * A link may only leave the app through http, https, mailto, or tel.
 * Anything else, including an empty string, is not stored and is not opened.
 */
internal fun isSafeLink(raw: String): Boolean {
    val text = raw.trim()
    if (text.isEmpty() || text.any { it.isWhitespace() || it.isISOControl() }) return false
    val uri = try {
        URI(text)
    } catch (_: Exception) {
        return false
    }
    val scheme = uri.scheme?.lowercase(Locale.US) ?: return false
    if (scheme != "http" && scheme != "https" && scheme != "mailto" && scheme != "tel") return false
    return when (scheme) {
        "http", "https" -> !uri.host.isNullOrBlank()
        else -> uri.schemeSpecificPart.isNotBlank()
    }
}

/**
 * The url under [offset], or null when that character is still hidden or the
 * stored url is not one of the allowed schemes.
 */
internal fun linkTarget(marks: List<TextMark>, offset: Int, hiddenUnlocked: Boolean): String? {
    val hidden = marks.any { mark ->
        mark.style == MarkStyle.HIDDEN && offset >= mark.start && offset < mark.end
    }
    if (hidden && !hiddenUnlocked) return null
    val url = marks.firstOrNull { mark ->
        mark.style == MarkStyle.LINK && offset >= mark.start && offset < mark.end
    }?.url ?: return null
    return url.takeIf { isSafeLink(it) }
}
