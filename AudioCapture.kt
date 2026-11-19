package com.chordfinder.app

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder

/**
 * Reads the microphone and calls [onFrame] (on the capture thread) with a chroma
 * vector and the input level roughly ten times per second.
 */
class AudioCapture(
    private val onFrame: (Frame) -> Unit,
    private val onError: (String) -> Unit
) {
    private val sampleRate = 44100
    private val size = 8192
    private val hop = 4096

    @Volatile
    private var running = false
    private var thread: Thread? = null

    val isRunning: Boolean get() = running

    @SuppressLint("MissingPermission")
    fun start() {
        if (running) return
        running = true
        thread = Thread({
            val minBuf = AudioRecord.getMinBufferSize(
                sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
            )
            if (minBuf <= 0) {
                running = false
                onError("המיקרופון לא תומך בפורמט ההקלטה")
                return@Thread
            }
            val recorder = try {
                AudioRecord(
                    MediaRecorder.AudioSource.MIC, sampleRate,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                    maxOf(minBuf, size * 2)
                )
            } catch (e: Exception) {
                running = false
                onError("לא ניתן לפתוח את המיקרופון")
                return@Thread
            }
            if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                recorder.release()
                running = false
                onError("המיקרופון תפוס על ידי אפליקציה אחרת")
                return@Thread
            }

            val extractor = ChromaExtractor(sampleRate, size)
            val window = FloatArray(size)
            val buf = ShortArray(hop)
            var filled = 0
            recorder.startRecording()
            try {
                while (running) {
                    var read = 0
                    while (read < hop && running) {
                        val n = recorder.read(buf, read, hop - read)
                        if (n <= 0) break
                        read += n
                    }
                    if (read < hop) { Thread.sleep(20); continue }
                    System.arraycopy(window, hop, window, 0, size - hop)
                    for (i in 0 until hop) window[size - hop + i] = buf[i] / 32768f
                    filled = minOf(size, filled + hop)
                    if (filled < size) continue
                    onFrame(extractor.process(window))
                }
            } finally {
                try { recorder.stop() } catch (_: Exception) {}
                recorder.release()
            }
        }, "audio-capture")
        thread?.start()
    }

    fun stop() {
        running = false
        thread?.join(500)
        thread = null
    }
}
