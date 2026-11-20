package com.chordfinder.app

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Sound generation: plain math, no Android, so it can be tested on its own. */
object SynthMath {
    const val SR = 44100

    fun freq(midi: Double) = 440.0 * 2.0.pow((midi - 69) / 12.0)

    /** Piano-like tone: decaying, slightly inharmonic partials plus a short hammer click. */
    fun piano(out: FloatArray, start: Int, midi: Double, seconds: Double, amp: Double) {
        val f = freq(midi)
        val n = (seconds * SR).toInt()
        val rnd = java.util.Random(midi.toLong() * 7919)
        val b = 0.0004
        for (h in 1..8) {
            val fh = f * h * sqrt(1 + b * h * h)
            if (fh > SR / 2.2) break
            val a = amp / h.toDouble().pow(1.3)
            val decay = 1.4 + 0.5 * h + f / 600.0
            val w = 2 * PI * fh / SR
            for (i in 0 until n) {
                val idx = start + i
                if (idx >= out.size) break
                val t = i.toDouble() / SR
                val att = if (i < 220) i / 220.0 else 1.0
                out[idx] += (a * att * exp(-decay * t) * sin(w * i)).toFloat()
            }
        }
        for (i in 0 until 400) {
            val idx = start + i
            if (idx >= out.size) break
            out[idx] += (amp * 0.15 * rnd.nextGaussian() * exp(-i / 60.0)).toFloat()
        }
    }

    /** Plucked string (Karplus-Strong) with an all-pass filter for exact tuning. */
    fun pluck(out: FloatArray, start: Int, midi: Double, seconds: Double, amp: Double) {
        val f = freq(midi)
        // The averaging filter reads one newer sample, so the loop is n - 0.5 + d long
        val period = SR / f + 0.5
        var n = floor(period).toInt()
        var d = period - n
        if (d < 0.1) { n -= 1; d += 1.0 }
        val c = (1 - d) / (1 + d)
        val rnd = java.util.Random(midi.toLong() * 104729)
        val buf = DoubleArray(n)
        var prev = 0.0
        for (i in 0 until n) {
            // Slightly smoothed noise: a softer, more natural attack
            val x = rnd.nextDouble() * 2 - 1
            prev = 0.6 * x + 0.4 * prev
            buf[i] = prev
        }
        val decay = 0.9965 - f / 200000.0
        var idx = 0
        var apX = 0.0
        var apY = 0.0
        val len = (seconds * SR).toInt()
        for (i in 0 until len) {
            val o = start + i
            if (o >= out.size) break
            val cur = buf[idx]
            val nxt = buf[(idx + 1) % n]
            val lp = decay * 0.5 * (cur + nxt)
            val ap = c * lp + apX - c * apY
            apX = lp
            apY = ap
            buf[idx] = ap
            idx = (idx + 1) % n
            val fade = if (len - i < 2000) (len - i) / 2000.0 else 1.0
            out[o] += (amp * cur * fade).toFloat()
        }
    }

    /** Scale up and back down, starting near middle C. Quarter-tone degrees are played as quarter tones. */
    fun scale(key: Key): FloatArray {
        val base = 60 + key.tonic - if (key.tonic >= 7) 12 else 0
        val notes = ArrayList<Double>()
        for (i in key.type.steps.indices) {
            notes.add(base + key.type.steps[i] - if (i in key.type.quarterFlat) 0.5 else 0.0)
        }
        notes.add(base + 12.0)
        val all = notes + notes.dropLast(1).reversed()
        val step = (0.36 * SR).toInt()
        val out = FloatArray(step * all.size + SR)
        all.forEachIndexed { i, m -> piano(out, i * step, m, 1.1, 0.30) }
        return normalize(out)
    }

    /** Strum from the bass string up, then let it ring. */
    fun strum(v: GuitarVoicing): FloatArray {
        val out = FloatArray((3.0 * SR).toInt())
        var t = 0
        for (s in 0 until 6) {
            val f = v.frets[s]
            if (f < 0) continue
            pluck(out, t, (OPEN_MIDI[s] + f).toDouble(), 2.9, 0.5)
            t += (0.035 * SR).toInt()
        }
        return normalize(out)
    }

    /** Chord on piano: notes together, root position. */
    fun pianoChord(chord: Chord): FloatArray {
        val out = FloatArray((2.2 * SR).toInt())
        val base = 48 + chord.root
        piano(out, 0, (base - 12).toDouble(), 2.0, 0.25)
        for (iv in chord.quality.intervals) piano(out, 0, (base + iv).toDouble(), 2.0, 0.22)
        return normalize(out)
    }

    val OPEN_MIDI = intArrayOf(40, 45, 50, 55, 59, 64)

    private fun normalize(x: FloatArray): FloatArray {
        var peak = 0f
        for (v in x) if (kotlin.math.abs(v) > peak) peak = kotlin.math.abs(v)
        if (peak > 0) { val g = 0.85f / peak; for (i in x.indices) x[i] *= g }
        return x
    }
}

