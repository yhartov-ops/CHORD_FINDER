package com.chordfinder.app

import kotlin.math.sqrt

enum class Quality(val suffix: String, val intervals: IntArray, val bias: Double, val hebrew: String) {
    MAJ("", intArrayOf(0, 4, 7), 1.00, "מז'ור"),
    MIN("m", intArrayOf(0, 3, 7), 1.00, "מינור"),
    DIM("dim", intArrayOf(0, 3, 6), 0.93, "מוקטן"),
    DOM7("7", intArrayOf(0, 4, 7, 10), 0.97, "דומיננט 7"),
    MAJ7("maj7", intArrayOf(0, 4, 7, 11), 0.93, "מז'ור 7"),
    MIN7("m7", intArrayOf(0, 3, 7, 10), 0.95, "מינור 7"),
    M7B5("m7b5", intArrayOf(0, 3, 6, 10), 0.92, "חצי מוקטן"),
    SUS4("sus4", intArrayOf(0, 5, 7), 0.92, "sus4"),
    SUS2("sus2", intArrayOf(0, 2, 7), 0.90, "sus2");
}

data class Chord(val root: Int, val quality: Quality) {
    fun name(flats: Boolean?): String = Notes.name(root, flats) + quality.suffix
    fun pitchClasses(): List<Int> = quality.intervals.map { (root + it) % 12 }
}

data class Key(val tonic: Int, val minor: Boolean) {
    /** Keys conventionally written with flats. */
    val useFlats: Boolean
        get() {
            val major = if (minor) (tonic + 3) % 12 else tonic
            return major in setOf(5, 10, 3, 8, 1)
        }

    fun name(): String = Notes.name(tonic, useFlats) + " " + (if (minor) "מינור" else "מז'ור")
    fun shortName(): String = Notes.name(tonic, useFlats) + (if (minor) "m" else "")

    fun scale(): List<Int> {
        val steps = if (minor) MINOR_STEPS else MAJOR_STEPS
        return steps.map { (tonic + it) % 12 }
    }

    /** Diatonic chords: numeral, triad, seventh chord. */
    fun diatonic(): List<Triple<String, Chord, Chord>> {
        val out = ArrayList<Triple<String, Chord, Chord>>()
        if (!minor) {
            val numerals = listOf("I", "ii", "iii", "IV", "V", "vi", "vii°")
            val triads = listOf(Quality.MAJ, Quality.MIN, Quality.MIN, Quality.MAJ, Quality.MAJ, Quality.MIN, Quality.DIM)
            val sevenths = listOf(Quality.MAJ7, Quality.MIN7, Quality.MIN7, Quality.MAJ7, Quality.DOM7, Quality.MIN7, Quality.M7B5)
            for (i in 0 until 7) {
                val r = (tonic + MAJOR_STEPS[i]) % 12
                out.add(Triple(numerals[i], Chord(r, triads[i]), Chord(r, sevenths[i])))
            }
        } else {
            val numerals = listOf("i", "ii°", "III", "iv", "v", "VI", "VII")
            val triads = listOf(Quality.MIN, Quality.DIM, Quality.MAJ, Quality.MIN, Quality.MIN, Quality.MAJ, Quality.MAJ)
            val sevenths = listOf(Quality.MIN7, Quality.M7B5, Quality.MAJ7, Quality.MIN7, Quality.MIN7, Quality.MAJ7, Quality.DOM7)
            for (i in 0 until 7) {
                val r = (tonic + MINOR_STEPS[i]) % 12
                out.add(Triple(numerals[i], Chord(r, triads[i]), Chord(r, sevenths[i])))
            }
            // Harmonic-minor dominant, very common in practice
            val v = (tonic + 7) % 12
            out.add(Triple("V", Chord(v, Quality.MAJ), Chord(v, Quality.DOM7)))
        }
        return out
    }

    /** Roman numeral of a chord within this key, or null if not diatonic. */
    fun numeralOf(chord: Chord): String? {
        for ((num, triad, seventh) in diatonic()) {
            if (triad == chord || seventh == chord) return num
        }
        return null
    }

    companion object {
        val MAJOR_STEPS = intArrayOf(0, 2, 4, 5, 7, 9, 11)
        val MINOR_STEPS = intArrayOf(0, 2, 3, 5, 7, 8, 10)
        fun all(): List<Key> = (0 until 12).flatMap { listOf(Key(it, false), Key(it, true)) }
    }
}

object Notes {
    private val SHARPS = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    private val FLATS = arrayOf("C", "Db", "D", "Eb", "E", "F", "Gb", "G", "Ab", "A", "Bb", "B")
    private val NEUTRAL = arrayOf("C", "C#", "D", "Eb", "E", "F", "F#", "G", "Ab", "A", "Bb", "B")

    fun name(pc: Int, flats: Boolean?): String {
        val p = ((pc % 12) + 12) % 12
        return when (flats) {
            true -> FLATS[p]
            false -> SHARPS[p]
            null -> NEUTRAL[p]
        }
    }
}

data class KeyResult(val key: Key, val score: Double)

object KeyDetector {
    // Krumhansl-Kessler key profiles
    private val MAJOR = doubleArrayOf(6.35, 2.23, 3.48, 2.33, 4.38, 4.09, 2.52, 5.19, 2.39, 3.66, 2.29, 2.88)
    private val MINOR = doubleArrayOf(6.33, 2.68, 3.52, 5.38, 2.60, 3.53, 2.54, 4.75, 3.98, 2.69, 3.34, 3.17)

    /** Returns all 24 keys sorted by correlation (best first), or empty if no signal. */
    fun rank(chroma: DoubleArray): List<KeyResult> {
        if (chroma.sum() <= 1e-9) return emptyList()
        val res = ArrayList<KeyResult>(24)
        for (t in 0 until 12) {
            res.add(KeyResult(Key(t, false), correlate(chroma, MAJOR, t)))
            res.add(KeyResult(Key(t, true), correlate(chroma, MINOR, t)))
        }
        res.sortByDescending { it.score }
        return res
    }

    private fun correlate(x: DoubleArray, profile: DoubleArray, shift: Int): Double {
        val y = DoubleArray(12) { profile[((it - shift) % 12 + 12) % 12] }
        val mx = x.average()
        val my = y.average()
        var num = 0.0
        var dx = 0.0
        var dy = 0.0
        for (i in 0 until 12) {
            val a = x[i] - mx
            val b = y[i] - my
            num += a * b
            dx += a * a
            dy += b * b
        }
        val den = sqrt(dx * dy)
        return if (den < 1e-12) 0.0 else num / den
    }
}

data class ChordResult(val chord: Chord, val score: Double)

object ChordDetector {
    fun best(chroma: DoubleArray, key: Key?): ChordResult? {
        val norm = sqrt(chroma.sumOf { it * it })
        if (norm < 1e-9) return null
        val diatonic = key?.diatonic()?.flatMap { listOf(it.second, it.third) }?.toSet() ?: emptySet()
        var best: ChordResult? = null
        for (root in 0 until 12) {
            for (q in Quality.values()) {
                var dot = 0.0
                for (iv in q.intervals) dot += chroma[(root + iv) % 12]
                // Cosine similarity with a binary template
                var score = dot / (norm * sqrt(q.intervals.size.toDouble())) * q.bias
                val chord = Chord(root, q)
                if (chord in diatonic) score *= 1.04
                if (best == null || score > best.score) best = ChordResult(chord, score)
            }
        }
        return best
    }
}
