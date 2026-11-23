package com.alekseevich47.notes.ui.editor

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.View

/** One short tick for a long-press that lands on a word. Never repeats. */
internal object SelectionHaptics {
    fun tick(view: View) {
        val performed = view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        if (performed) return
        val resolver = view.context.contentResolver
        val enabled = Settings.System.getInt(resolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, 0)
        if (enabled == 0) return
        vibrateFallback(view.context)
    }

    private fun vibrateFallback(context: Context) {
        val vibrator = if (Build.VERSION.SDK_INT >= 31) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        } ?: return
        if (!vibrator.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= 29) {
            vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
        } else {
            vibrator.vibrate(VibrationEffect.createOneShot(12, 60))
        }
    }
}
