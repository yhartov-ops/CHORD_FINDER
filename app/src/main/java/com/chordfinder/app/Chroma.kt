package com.chordfinder.app

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Turns a block of audio into a 12-bin pitch-class profile (chroma).
 * Uses spectral peaks with parabolic interpolation, so each peak is
 * mapped to the nearest semitone.
 */
class ChromaExtractor(private val sampleRate: Int, val size: Int) {
    private val window = DoubleArray(size) { 0.5 - 0.5 * cos(2 * PI * it / (size - 1)) }
    private val re = DoubleArray(size)
    private val im = DoubleArray(size)
    private val mag = DoubleArray(size / 2)
    private val cosT = DoubleArray(size / 2) { cos(-2 * PI * it / size) }
    private val sinT = DoubleArray(size / 2) { sin(-2 * PI * it / size) }

    private val minBin = (70.0 * size / sampleRate).toInt().coerceAtLeast(2)
    private val maxBin = (2500.0 * size / sampleRate).toInt().coerceAtMost(size / 2 - 2)

    /** Level of the last processed block in dBFS. */
    var levelDb = -100.0
        private set

    /** Returns chroma normalised to sum 1, or all zeros when the input is silent. */
    fun process(samples: FloatArray): DoubleArray {
        require(samples.size == size)
        var sumSq = 0.0
        for (i in 0 until size) {
            val s = samples[i].toDouble()
            sumSq += s * s
            re[i] = s * window[i]
            im[i] = 0.0
        }
        val rms = sqrt(sumSq / size)
        levelDb = if (rms > 1e-9) 20 * log10(rms) else -100.0
        val chroma = DoubleArray(12)
        if (levelDb < SILENCE_DB) return chroma

        fft()
        var peak = 0.0
        for (k in 0 until size / 2) {
            mag[k] = sqrt(re[k] * re[k] + im[k] * im[k])
            if (k in minBin..maxBin && mag[k] > peak) peak = mag[k]
        }
        val threshold = peak * 0.04
        for (k in minBin..maxBin) {
            val b = mag[k]
            if (b < threshold || b <= mag[k - 1] || b < mag[k + 1]) continue
            val a = ln(mag[k - 1] + 1e-12)
            val bl = ln(b + 1e-12)
            val c = ln(mag[k + 1] + 1e-12)
            val denom = a - 2 * bl + c
            val delta = if (denom != 0.0) (0.5 * (a - c) / denom).coerceIn(-0.5, 0.5) else 0.0
            val freq = (k + delta) * sampleRate / size
            val midi = 69 + 12 * ln(freq / 440.0) / ln(2.0)
            val pc = ((midi.roundToInt() % 12) + 12) % 12
            chroma[pc] += sqrt(b)
        }
        val total = chroma.sum()
        if (total > 0) for (i in 0 until 12) chroma[i] /= total
        return chroma
    }

    private fun fft() {
        val n = size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                var t = re[i]; re[i] = re[j]; re[j] = t
                t = im[i]; im[i] = im[j]; im[j] = t
            }
        }
        var len = 2
        while (len <= n) {
            val half = len / 2
            val step = n / len
            var i = 0
            while (i < n) {
                for (k in 0 until half) {
                    val wr = cosT[k * step]
                    val wi = sinT[k * step]
                    val xr = re[i + k + half] * wr - im[i + k + half] * wi
                    val xi = re[i + k + half] * wi + im[i + k + half] * wr
                    re[i + k + half] = re[i + k] - xr
                    im[i + k + half] = im[i + k] - xi
                    re[i + k] += xr
                    im[i + k] += xi
                }
                i += len
            }
            len = len shl 1
        }
    }

    companion object {
        const val SILENCE_DB = -55.0
    }
}

/** Combines frames over time: a long memory for the key and a short one for the chord. */
class HarmonyTracker {
    val keyChroma = DoubleArray(12)
    val chordChroma = DoubleArray(12)
    var lastFrame = DoubleArray(12)
        private set

    var lockedKey: Key? = null
    var keyRanking: List<KeyResult> = emptyList()
        private set
    var currentChord: ChordResult? = null
        private set
    val history = ArrayList<Chord>()

    private var candidate: Chord? = null
    private var candidateCount = 0
    private var silentFrames = 0

    val key: Key?
        get() = lockedKey ?: keyRanking.firstOrNull()?.key

    fun reset() {
        keyChroma.fill(0.0)
        chordChroma.fill(0.0)
        keyRanking = emptyList()
        currentChord = null
        history.clear()
        candidate = null
        candidateCount = 0
    }

    fun add(frame: DoubleArray) {
        lastFrame = frame
        val silent = frame.sum() <= 0.0
        if (silent) {
            silentFrames++
            for (i in 0 until 12) chordChroma[i] *= 0.7
            if (silentFrames > 6) currentChord = null
            return
        }
        silentFrames = 0
        for (i in 0 until 12) {
            keyChroma[i] = keyChroma[i] * KEY_DECAY + frame[i]
            chordChroma[i] = chordChroma[i] * (1 - CHORD_ALPHA) + frame[i] * CHORD_ALPHA
        }
        keyRanking = KeyDetector.rank(keyChroma)

        val best = ChordDetector.best(chordChroma, key) ?: return
        if (best.score < MIN_CHORD_SCORE) return
        if (best.chord == candidate) candidateCount++ else {
            candidate = best.chord
            candidateCount = 1
        }
        if (candidateCount >= STABLE_FRAMES) {
            if (currentChord?.chord != best.chord) {
                if (history.lastOrNull() != best.chord) {
                    history.add(best.chord)
                    if (history.size > 16) history.removeAt(0)
                }
            }
            currentChord = best
        }
    }

    companion object {
        const val KEY_DECAY = 0.997      // about 30 seconds of memory
        const val CHORD_ALPHA = 0.35
        const val MIN_CHORD_SCORE = 0.72
        const val STABLE_FRAMES = 4
    }
}
