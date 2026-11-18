package com.alekseevich47.notes.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.alekseevich47.notes.data.secure.NotesPrefs
import com.alekseevich47.notes.log.AppLog

/**
 * Starts warmup after boot and user unlock.
 * [Intent.ACTION_BOOT_COMPLETED] is delivered only once credential storage is
 * unlocked. [Intent.ACTION_LOCKED_BOOT_COMPLETED] is not registered.
 */
class ModelWarmupReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        val warmup = NotesPrefs.open(context).getBoolean(NotesPrefs.WARMUP_ON_BOOT, false)
        if (!warmup) {
            AppLog.i(TAG, "boot skipped")
            return
        }
        AppLog.i(TAG, "boot start")
        ModelWarmupService.start(context)
    }

    private companion object {
        const val TAG = "Warmup"
    }
}
