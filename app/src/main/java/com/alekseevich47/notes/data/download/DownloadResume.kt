package com.alekseevich47.notes.data.download

/**
 * Pure helpers for pause / resume downloads that use HTTP Range.
 */
object DownloadResume {
    enum class Action { FRESH, RESUME, COMPLETE }

    data class Plan(
        val action: Action,
        val rangeHeader: String?,
        val existingBytes: Long,
    )

    fun plan(existingBytes: Long, totalBytes: Long?): Plan {
        val have = existingBytes.coerceAtLeast(0L)
        if (totalBytes != null && totalBytes > 0L && have >= totalBytes) {
            return Plan(Action.COMPLETE, null, have)
        }
        if (have > 0L) {
            return Plan(Action.RESUME, "bytes=$have-", have)
        }
        return Plan(Action.FRESH, null, 0L)
    }

    fun remaining(existingBytes: Long, totalBytes: Long?): Long? {
        if (totalBytes == null || totalBytes < 0L) return null
        return (totalBytes - existingBytes.coerceAtLeast(0L)).coerceAtLeast(0L)
    }

    fun progress(downloadedBytes: Long, totalBytes: Long?): Float {
        if (totalBytes == null || totalBytes <= 0L) return 0f
        return (downloadedBytes.coerceAtLeast(0L).toDouble() / totalBytes.toDouble())
            .toFloat()
            .coerceIn(0f, 1f)
    }

    /**
     * @param downloaded bytes already on disk per file
     * @param totals remote size per file, null when the server has not said yet
     */
    fun aggregate(downloaded: List<Long>, totals: List<Long?>): Float {
        if (downloaded.isEmpty() || downloaded.size != totals.size) return 0f
        var knownGot = 0L
        var knownTotal = 0L
        var anyKnown = false
        for (index in downloaded.indices) {
            val total = totals[index]
            if (total == null || total <= 0L) continue
            anyKnown = true
            knownTotal += total
            knownGot += downloaded[index].coerceIn(0L, total)
        }
        if (!anyKnown || knownTotal <= 0L) return 0f
        return (knownGot.toDouble() / knownTotal.toDouble()).toFloat().coerceIn(0f, 1f)
    }

    /** Content-Range: bytes 0-99/240 or bytes star-slash-240. */
    fun parseTotal(contentRange: String?): Long? {
        if (contentRange.isNullOrBlank()) return null
        val total = contentRange.substringAfter('/', missingDelimiterValue = "").trim()
        if (total.isEmpty() || total == "*") return null
        return total.toLongOrNull()?.takeIf { it >= 0L }
    }

    /**
     * A resumed request that comes back as 200 ignored Range and must be rewritten
     * from byte 0 instead of appended.
     */
    fun shouldRewriteFromStart(statusCode: Int, resumed: Boolean): Boolean {
        return resumed && statusCode == 200
    }

    fun isPartialContent(statusCode: Int): Boolean = statusCode == 206
}
