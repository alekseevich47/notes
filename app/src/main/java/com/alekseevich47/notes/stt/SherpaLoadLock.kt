package com.alekseevich47.notes.stt

import kotlinx.coroutines.sync.Mutex

/** Serializes sherpa construction so warmup and a recording do not init the same native runtime together. */
object SherpaLoadLock {
    val mutex = Mutex()
}
