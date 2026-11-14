package com.alekseevich47.notes.data.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.ToneGenerator
import android.media.AudioManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.PI
import kotlin.math.sin

@Singleton
class BeepPlayer @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun playStartBeep() = withContext(Dispatchers.Default) {
        playTone(frequencyHz = 660.0, durationMs = 90, volume = 0.18)
    }

    suspend fun playStopBeep() = withContext(Dispatchers.Default) {
        playTone(frequencyHz = 520.0, durationMs = 70, volume = 0.14)
    }

    private fun playTone(frequencyHz: Double, durationMs: Int, volume: Double) {
        val sampleRate = 22_050
        val count = sampleRate * durationMs / 1000
        val buf = ShortArray(count)
        for (i in 0 until count) {
            val t = i.toDouble() / sampleRate
            val env = when {
                i < count / 10 -> i.toDouble() / (count / 10)
                i > count * 8 / 10 -> (count - i).toDouble() / (count / 5)
                else -> 1.0
            }
            val sample = sin(2.0 * PI * frequencyHz * t) * volume * env
            buf[i] = (sample * Short.MAX_VALUE).toInt().toShort()
        }
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(buf.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        track.write(buf, 0, buf.size)
        track.play()
        Thread.sleep(durationMs.toLong() + 30)
        track.release()
    }
}
