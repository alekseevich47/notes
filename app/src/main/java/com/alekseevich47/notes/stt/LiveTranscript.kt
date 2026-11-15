package com.alekseevich47.notes.stt

/**
 * Streaming transcript.
 *
 * [committed] is only finalized text. [partial] is the latest hypothesis for
 * the open segment and replaces itself on every update. Display is the join of
 * the two. Writing that join back into [committed] on each decode is what made
 * T-one repeat a phrase once per audio frame.
 *
 * Fold [partial] into [committed] only from [onEndpoint] or [onFinish].
 */
data class LiveTranscript(
    val committed: String = "",
    val partial: String = "",
) {
    val display: String
        get() = join(committed, partial)

    fun onPartial(text: String): LiveTranscript = copy(partial = text.trim())

    fun onEndpoint(): LiveTranscript = LiveTranscript(committed = join(committed, partial))

    fun onFinish(): LiveTranscript = onEndpoint()

    companion object {
        fun join(committed: String, partial: String): String {
            val head = committed.trim()
            val tail = partial.trim()
            return when {
                tail.isEmpty() -> head
                head.isEmpty() -> tail
                else -> "$head $tail"
            }
        }

        /**
         * Prefer the on-screen string when it is the finalized text plus the
         * still-open partial (finish failed before the partial was folded in).
         */
        fun prefer(finalized: String, displayed: String): String {
            val head = finalized.trim()
            val shown = displayed.trim()
            return when {
                head.isEmpty() -> shown
                shown.isEmpty() || shown == head -> head
                shown.startsWith("$head ") -> shown
                else -> head
            }
        }
    }
}
