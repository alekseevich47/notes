package com.alekseevich47.notes.data.report

/**
 * Text of a bug-report letter. No Android types: safe to call from unit tests
 * and from the main thread. [body] and [BugReportForm.validate] only touch strings.
 */
object BugReportMail {
    const val NAME_MAX = 60
    const val TEXT_MIN = 10
    const val TEXT_MAX = 2000
    const val BODY_MAX = 8000
    const val MAX_SHOTS = 5

    fun subject(versionName: String): String {
        return "Заметки ${stripControlCharacters(versionName)}: сообщение об ошибке"
    }

    fun body(form: BugReportForm, info: BugReportInfo): String {
        val clean = stripControlCharacters(buildString {
            append("Имя: ")
            append(form.name)
            append("\n\nЧто случилось:\n")
            append(form.text)
            append("\n\n—\nЗаметки ")
            append(info.versionName)
            append(" (")
            append(info.versionCode)
            append("), ")
            append(info.buildType)
            append("\nAndroid ")
            append(info.release)
            append(" (API ")
            append(info.sdk)
            append("), ")
            append(info.manufacturer)
            append(' ')
            append(info.model)
        })
        return if (clean.length <= BODY_MAX) clean else clean.substring(0, BODY_MAX)
    }
}

data class BugReportForm(
    val name: String,
    val text: String,
) {
    fun validate(): BugReportValidity {
        if (name.isBlank()) return BugReportValidity.nameBlank
        val length = text.trim().length
        if (length < BugReportMail.TEXT_MIN) return BugReportValidity.textTooShort
        if (length > BugReportMail.TEXT_MAX) return BugReportValidity.textTooLong
        return BugReportValidity.ok
    }
}

enum class BugReportValidity {
    ok,
    nameBlank,
    textTooShort,
    textTooLong,
}

data class BugReportInfo(
    val versionName: String,
    val versionCode: Int,
    val buildType: String,
    val release: String,
    val sdk: Int,
    val manufacturer: String,
    val model: String,
)

/**
 * Largest power-of-two sample size whose decoded long side is at most [maxSide].
 * BitmapFactory divides each side by this value, so the full-resolution bitmap
 * is never allocated when the source is larger than [maxSide].
 */
internal fun imageSampleSize(width: Int, height: Int, maxSide: Int): Int {
    if (width <= 0 || height <= 0 || maxSide <= 0) return 1
    val longSide = if (width > height) width else height
    var sample = 1
    while (sample < longSide && longSide / sample > maxSide) {
        val next = sample shl 1
        if (next <= sample) break
        sample = next
    }
    return sample
}

/** Drops ISO control characters and keeps line breaks. */
internal fun stripControlCharacters(raw: String): String {
    var dirty = false
    for (ch in raw) {
        if (ch != '\n' && ch.isISOControl()) {
            dirty = true
            break
        }
    }
    if (!dirty) return raw
    val out = StringBuilder(raw.length)
    for (ch in raw) {
        if (ch == '\n' || !ch.isISOControl()) out.append(ch)
    }
    return out.toString()
}
