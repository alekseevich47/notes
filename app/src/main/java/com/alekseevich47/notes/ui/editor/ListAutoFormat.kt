package com.alekseevich47.notes.ui.editor

/**
 * Numbered list mechanics (Cursor/ChatGPT style).
 * Triggers only at line start with "1." etc.
 */
object ListAutoFormat {
    private val itemRegex = Regex("""^(\d+)\.\s?(.*)$""")

    fun onTextChanged(old: String, new: String, cursor: Int): Pair<String, Int>? {
        // Detect Enter inside/after a list item
        if (new.length <= old.length) {
            // maybe deleted — renumber
            return renumberIfNeeded(new, cursor)
        }
        val inserted = new.length - old.length
        if (inserted == 1 && new.getOrNull(cursor - 1) == '\n') {
            return handleEnter(new, cursor)
        }
        // Auto-space + capitalize after creating "N."
        return handleAutoStart(new, cursor) ?: renumberIfNeeded(new, cursor)
    }

    private fun handleEnter(text: String, cursor: Int): Pair<String, Int>? {
        val before = text.substring(0, cursor - 1)
        val after = text.substring(cursor)
        val lineStart = before.lastIndexOf('\n').let { if (it < 0) 0 else it + 1 }
        val prevLine = before.substring(lineStart)
        val match = itemRegex.matchEntire(prevLine) ?: return null
        val num = match.groupValues[1].toInt()
        val content = match.groupValues[2]
        if (content.isBlank()) {
            // empty item → exit list, remove empty marker
            val without = before.substring(0, lineStart) + after
            return without to lineStart
        }
        val next = "${before}\n${num + 1}. " + after
        val newCursor = before.length + 1 + "${num + 1}. ".length
        return renumber(next) to newCursor
    }

    private fun handleAutoStart(text: String, cursor: Int): Pair<String, Int>? {
        val lineStart = text.lastIndexOf('\n', (cursor - 1).coerceAtLeast(0)).let { if (it < 0) 0 else it + 1 }
        val line = text.substring(lineStart, cursor.coerceAtMost(text.length))
        if (line == "1.") {
            val next = text.substring(0, cursor) + " " + text.substring(cursor)
            return next to cursor + 1
        }
        return null
    }

    private fun renumberIfNeeded(text: String, cursor: Int): Pair<String, Int>? {
        val renumbered = renumber(text)
        return if (renumbered != text) renumbered to cursor.coerceAtMost(renumbered.length) else null
    }

    fun renumber(text: String): String {
        val lines = text.split('\n')
        var n = 0
        var inList = false
        val out = lines.map { line ->
            val m = itemRegex.matchEntire(line)
            if (m != null) {
                if (!inList) {
                    inList = true
                    n = 1
                } else {
                    n += 1
                }
                val content = m.groupValues[2]
                "$n. $content".trimEnd().let { if (content.isEmpty()) "$n. " else it }
            } else {
                inList = false
                n = 0
                line
            }
        }
        return out.joinToString("\n")
    }
}
