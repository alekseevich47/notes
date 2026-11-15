package com.alekseevich47.notes.data.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.sin

object SoftBeep {
    fun play() {
        runCatching {
            val sampleRate = 22050
            val count = (sampleRate * 0.09).toInt()
            val pcm = ShortArray(count)
            val freq = 880.0
            for (index in 0 until count) {
                val envelope = sin(PI * index / count)
                val wave = sin(2.0 * PI * freq * index / sampleRate)
                pcm[index] = (wave * envelope * 0.16 * 32767.0).toInt().toShort()
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
                .setBufferSizeInBytes(count * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
            track.write(pcm, 0, count)
            track.setVolume(0.35f)
            track.play()
            track.setNotificationMarkerPosition(count)
            track.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
                override fun onMarkerReached(track: AudioTrack) {
                    track.stop()
                    track.release()
                }

                override fun onPeriodicNotification(track: AudioTrack) = Unit
            })
        }
    }
}
