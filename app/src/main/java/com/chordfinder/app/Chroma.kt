package com.chordfinder.app

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** One analysed block: full chroma, low-register chroma, input level. */
class Frame(val chroma: DoubleArray, val bass: DoubleArray, val levelDb: Double) {
    val silent: Boolean get() = chroma.sum() <= 0.0
}

/**
 * Turns a block of audio into a 12-bin pitch-class profile (chroma).
 * Spectral peaks are interpolated, overtones of lower peaks are damped,
 * and notes below ~260 Hz are also collected separately as the bass line.
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

    private val peakFreq = DoubleArray(512)
    private val peakMag = DoubleArray(512)

    var levelDb = -100.0
        private set

    fun process(samples: FloatArray): Frame {
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
        val bass = DoubleArray(12)
        if (levelDb < SILENCE_DB) return Frame(chroma, bass, levelDb)

        fft()
        var peak = 0.0
        for (k in 0 until size / 2) {
            mag[k] = sqrt(re[k] * re[k] + im[k] * im[k])
            if (k in minBin..maxBin && mag[k] > peak) peak = mag[k]
        }
        val threshold = peak * 0.04
        var n = 0
        for (k in minBin..maxBin) {
            val b = mag[k]
            if (b < threshold || b <= mag[k - 1] || b < mag[k + 1]) continue
            val a = ln(mag[k - 1] + 1e-12)
            val bl = ln(b + 1e-12)
            val c = ln(mag[k + 1] + 1e-12)
            val denom = a - 2 * bl + c
            val delta = if (denom != 0.0) (0.5 * (a - c) / denom).coerceIn(-0.5, 0.5) else 0.0
            if (n < peakFreq.size) {
                peakFreq[n] = (k + delta) * sampleRate / size
                peakMag[n] = b
                n++
            }
        }
        for (p in 0 until n) {
            val f = peakFreq[p]
            var w = sqrt(peakMag[p])
            // Damp a peak that is the 2nd-5th overtone of a stronger lower peak
            for (q in 0 until p) {
                if (peakMag[q] < peakMag[p] * 0.5) continue
                val ratio = f / peakFreq[q]
                val h = ratio.roundToInt()
                if (h in 2..8 && abs(ratio - h) < 0.03 * h) { w *= 0.4; break }
            }
            val midi = 69 + 12 * ln(f / 440.0) / ln(2.0)
            val pc = ((midi.roundToInt() % 12) + 12) % 12
            chroma[pc] += w
            if (f < 260) bass[pc] += w
        }
        val total = chroma.sum()
        if (total > 0) for (i in 0 until 12) chroma[i] /= total
        val bt = bass.sum()
        if (bt > 0) for (i in 0 until 12) bass[i] /= bt
        return Frame(chroma, bass, levelDb)
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

/** How quickly the shown chord may change. */
enum class Stability(
    val hebrew: String,
    val alpha: Double,
    val stableFrames: Int,
    val margin: Double,
    val keyPrior: Double
) {
    FAST("מהיר", 0.35, 4, 0.0, 1.04),
    NORMAL("רגיל", 0.22, 6, 0.03, 1.05),
    SONG("שיר מלא", 0.16, 7, 0.05, 1.08)
}

/** Combines frames over time: a long memory for the key and a short one for the chord. */
class HarmonyTracker {
    val keyChroma = DoubleArray(12)
    val chordChroma = DoubleArray(12)
    val bassChroma = DoubleArray(12)

    var stability = Stability.NORMAL
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
        bassChroma.fill(0.0)
        keyRanking = emptyList()
        currentChord = null
        history.clear()
        candidate = null
        candidateCount = 0
    }

    fun add(frame: Frame) {
        if (frame.silent) {
            silentFrames++
            for (i in 0 until 12) { chordChroma[i] *= 0.7; bassChroma[i] *= 0.7 }
            if (silentFrames > 6) currentChord = null
            return
        }
        silentFrames = 0
        val a = stability.alpha
        for (i in 0 until 12) {
            keyChroma[i] = keyChroma[i] * KEY_DECAY + frame.chroma[i]
            chordChroma[i] = chordChroma[i] * (1 - a) + frame.chroma[i] * a
            bassChroma[i] = bassChroma[i] * (1 - a) + frame.bass[i] * a
        }
        keyRanking = KeyDetector.rank(keyChroma)
        val k = key

        val best = ChordDetector.best(chordChroma, bassChroma, k, stability.keyPrior) ?: return
        if (best.score < MIN_CHORD_SCORE) return
        val cur = currentChord
        if (cur != null && cur.chord != best.chord) {
            // Hysteresis: the new chord must clearly beat the one on screen
            val curScore = ChordDetector.scoreOf(cur.chord, chordChroma, bassChroma, k, stability.keyPrior)
            if (best.score < curScore + stability.margin) {
                candidate = null
                candidateCount = 0
                return
            }
        }
        if (best.chord == candidate) candidateCount++ else {
            candidate = best.chord
            candidateCount = 1
        }
        if (candidateCount >= stability.stableFrames || cur == null && candidateCount >= 2) {
            if (cur?.chord != best.chord && history.lastOrNull() != best.chord) {
                history.add(best.chord)
                if (history.size > 16) history.removeAt(0)
            }
            currentChord = best
        }
    }

    companion object {
        const val KEY_DECAY = 0.997      // about 30 seconds of memory
        const val MIN_CHORD_SCORE = 0.72
    }
}
