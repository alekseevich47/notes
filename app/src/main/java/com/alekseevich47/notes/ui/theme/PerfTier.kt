package com.alekseevich47.notes.ui.theme

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

enum class PerfTier { LOW, NORMAL }

val LocalPerfTier: ProvidableCompositionLocal<PerfTier> = compositionLocalOf { PerfTier.NORMAL }

val LocalReduceMotion: ProvidableCompositionLocal<Boolean> = compositionLocalOf { false }

/**
 * LOW when any hardware signal says the device will struggle with live blur
 * and multi-step shadows. Callers that never provide the local stay NORMAL.
 */
internal fun perfTierFor(
    lowRam: Boolean,
    sdkInt: Int,
    processors: Int,
    memoryClass: Int,
): PerfTier {
    val low = lowRam || sdkInt < 28 || processors <= 4 || memoryClass < 192
    return if (low) PerfTier.LOW else PerfTier.NORMAL
}

@Composable
internal fun rememberHardwarePerfTier(): PerfTier {
    val context = LocalContext.current
    return remember(context) { hardwarePerfTier(context) }
}

internal fun hardwarePerfTier(context: Context): PerfTier {
    val manager = context.getSystemService(ActivityManager::class.java)
    return perfTierFor(
        lowRam = manager?.isLowRamDevice == true,
        sdkInt = Build.VERSION.SDK_INT,
        processors = Runtime.getRuntime().availableProcessors(),
        memoryClass = manager?.memoryClass ?: Int.MAX_VALUE,
    )
}

/**
 * Read on every composition. A later theme recomposition picks up a settings
 * change; the scale itself is not remembered.
 */
@Composable
internal fun currentReduceMotion(): Boolean {
    val context = LocalContext.current
    val scale = Settings.Global.getFloat(
        context.contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f,
    )
    return scale == 0f
}
