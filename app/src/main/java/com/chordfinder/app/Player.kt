package com.chordfinder.app

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack

/** Plays generated sound through the media stream. */
class Player {
    private var track: AudioTrack? = null
    @Volatile var busyUntil = 0L
        private set

    fun play(make: () -> FloatArray) {
        Thread {
            val samples = make()
            val pcm = ShortArray(samples.size) { (samples[it].coerceIn(-1f, 1f) * 32767).toInt().toShort() }
            synchronized(this) {
                stopLocked()
                try {
                    val t = AudioTrack.Builder()
                        .setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                .build()
                        )
                        .setAudioFormat(
                            AudioFormat.Builder()
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                .setSampleRate(SynthMath.SR)
                                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                                .build()
                        )
                        .setTransferMode(AudioTrack.MODE_STATIC)
                        .setBufferSizeInBytes(pcm.size * 2)
                        .build()
                    t.write(pcm, 0, pcm.size)
                    t.play()
                    track = t
                    busyUntil = System.currentTimeMillis() + pcm.size * 1000L / SynthMath.SR + 300
                } catch (_: Exception) {
                    busyUntil = 0
                }
            }
        }.start()
    }

    fun isPlaying() = System.currentTimeMillis() < busyUntil

    @Synchronized
    fun stop() = stopLocked()

    private fun stopLocked() {
        track?.let {
            try { it.stop() } catch (_: Exception) {}
            it.release()
        }
        track = null
        busyUntil = 0
    }
}
