package com.alekseevich47.notes.stt

import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizer

/**
 * Process-wide recognizers owned by the warmup service.
 *
 * Recording borrows them and must not call release. [retireAll] is the only
 * path that frees an idle engine; a borrowed engine is freed once, when the
 * recording calls [unborrowTone] or [unborrowGiga].
 */
object WarmEngines {
    private val tone = EnginePool<OnlineRecognizer>()
    private val giga = EnginePool<OfflineRecognizer>()

    fun toneReady(): Boolean = tone.ready()

    fun gigaReady(): Boolean = giga.ready()

    fun offerTone(engine: OnlineRecognizer, accept: () -> Boolean = { true }) {
        tone.offer(engine, accept) { it.release() }
    }

    fun offerGiga(engine: OfflineRecognizer, accept: () -> Boolean = { true }) {
        giga.offer(engine, accept) { it.release() }
    }

    fun borrowTone(): OnlineRecognizer? = tone.borrow()

    fun borrowGiga(): OfflineRecognizer? = giga.borrow()

    fun unborrowTone(engine: OnlineRecognizer) {
        tone.unborrow(engine) { it.release() }
    }

    fun unborrowGiga(engine: OfflineRecognizer) {
        giga.unborrow(engine) { it.release() }
    }

    fun retireAll() {
        tone.retire { it.release() }
        giga.retire { it.release() }
    }
}
