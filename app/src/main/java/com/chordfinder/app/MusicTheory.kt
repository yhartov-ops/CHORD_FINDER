package com.chordfinder.app

import kotlin.math.abs
import kotlin.math.sqrt

enum class Quality(
    val suffix: String,
    val intervals: IntArray,
    val bias: Double,
    val hebrew: String,
    val detect: Boolean
) {
    MAJ("", intArrayOf(0, 4, 7), 1.00, "מז'ור", true),
    MIN("m", intArrayOf(0, 3, 7), 1.00, "מינור", true),
    DIM("dim", intArrayOf(0, 3, 6), 0.93, "מוקטן", true),
    AUG("aug", intArrayOf(0, 4, 8), 0.90, "מוגדל", false),
    DOM7("7", intArrayOf(0, 4, 7, 10), 0.97, "דומיננט 7", true),
    MAJ7("maj7", intArrayOf(0, 4, 7, 11), 0.93, "מז'ור 7", true),
    MIN7("m7", intArrayOf(0, 3, 7, 10), 0.95, "מינור 7", true),
    M7B5("m7b5", intArrayOf(0, 3, 6, 10), 0.92, "חצי מוקטן", true),
    DIM7("dim7", intArrayOf(0, 3, 6, 9), 0.90, "מוקטן 7", false),
    MMAJ7("m(maj7)", intArrayOf(0, 3, 7, 11), 0.90, "מינור מז'ור 7", false),
    AUGMAJ7("maj7#5", intArrayOf(0, 4, 8, 11), 0.90, "מז'ור 7 מוגדל", false),
    SUS4("sus4", intArrayOf(0, 5, 7), 0.92, "sus4", true),
    SUS2("sus2", intArrayOf(0, 2, 7), 0.90, "sus2", true),
    SEVSUS4("7sus4", intArrayOf(0, 5, 7, 10), 0.90, "7sus4", false),
    SIX("6", intArrayOf(0, 4, 7, 9), 0.90, "מז'ור 6", false),
    MIN6("m6", intArrayOf(0, 3, 7, 9), 0.90, "מינור 6", false),
    ADD9("add9", intArrayOf(0, 4, 7, 14), 0.90, "add9", false),
    NINE("9", intArrayOf(0, 4, 7, 10, 14), 0.90, "דומיננט 9", false),
    MAJ9("maj9", intArrayOf(0, 4, 7, 11, 14), 0.90, "מז'ור 9", false),
    MIN9("m9", intArrayOf(0, 3, 7, 10, 14), 0.90, "מינור 9", false);

    val pcSet: Set<Int> get() = intervals.map { it % 12 }.toSet()
}

data class Chord(val root: Int, val quality: Quality) {
    fun name(sp: Spelling): String = sp.name(root) + quality.suffix
    fun pitchClasses(): List<Int> = quality.intervals.map { (root + it) % 12 }
}

/** Note naming: spelled names for scale tones, sharps/flats/neutral for the rest. */
class Spelling(private val names: Map<Int, String>, val flats: Boolean?) {
    fun name(pc: Int): String = names[((pc % 12) + 12) % 12] ?: Notes.name(pc, flats)

    companion object {
        val NEUTRAL = Spelling(emptyMap(), null)
    }
}

enum class ScaleGroup(val hebrew: String) {
    REGULAR("סולמות רגילים"),
    EASTERN("סולמות מזרחיים (מקאמים)"),
    MODES("מודוסים")
}

enum class ScaleType(
    val hebrew: String,
    val description: String,
    val group: ScaleGroup,
    val steps: IntArray,
    /** Degrees (0-based) that are really a quarter tone lower than written. */
    val quarterFlat: Set<Int> = emptySet(),
    /** Extra fit required before auto-detection prefers it over plain major/minor; null = never auto-detected. */
    val margin: Double? = 0.04,
    /** Name in the scale picker, when it differs from the short name. */
    private val pickerName: String? = null
) {
    MAJOR("מז'ור", "עג'ם", ScaleGroup.REGULAR, intArrayOf(0, 2, 4, 5, 7, 9, 11), margin = 0.0, pickerName = "מז'ור רגיל"),
    MINOR("מינור", "מינור טבעי", ScaleGroup.REGULAR, intArrayOf(0, 2, 3, 5, 7, 8, 10), margin = 0.0, pickerName = "מינור רגיל"),
    HARMONIC_MINOR("מינור הרמוני", "נהוונד", ScaleGroup.REGULAR, intArrayOf(0, 2, 3, 5, 7, 8, 11), margin = 0.08),
    HIJAZ("חיג'אז", "אהבה רבה, פריגי דומיננטי", ScaleGroup.EASTERN, intArrayOf(0, 1, 4, 5, 7, 8, 10)),
    HIJAZ_KAR("חיג'אז כאר", "הרמוני כפול", ScaleGroup.EASTERN, intArrayOf(0, 1, 4, 5, 7, 8, 11), margin = 0.10),
    KURD("כורד", "פריגי", ScaleGroup.EASTERN, intArrayOf(0, 1, 3, 5, 7, 8, 10)),
    NIKRIZ("ניכריז", "", ScaleGroup.EASTERN, intArrayOf(0, 2, 3, 6, 7, 9, 10), margin = 0.06),
    RAST("ראסט", "קירוב, רבעי טון", ScaleGroup.EASTERN, intArrayOf(0, 2, 4, 5, 7, 9, 11), setOf(2, 6), null),
    BAYATI("ביאתי", "קירוב, רבע טון", ScaleGroup.EASTERN, intArrayOf(0, 2, 3, 5, 7, 8, 10), setOf(1), null),
    SABA("סבא", "קירוב, רבע טון", ScaleGroup.EASTERN, intArrayOf(0, 2, 3, 4, 7, 8, 10), setOf(1), null),
    DORIAN("דורי", "מינור עם דרגה 6 גבוהה", ScaleGroup.MODES, intArrayOf(0, 2, 3, 5, 7, 9, 10), margin = 0.06),
    MIXOLYDIAN("מיקסולידי", "מז'ור עם דרגה 7 נמוכה", ScaleGroup.MODES, intArrayOf(0, 2, 4, 5, 7, 9, 10), margin = 0.06);

    fun pickerLabel(): String {
        val name = pickerName ?: hebrew
        return if (description.isEmpty()) name else "$name  ($description)"
    }
}

data class Key(val tonic: Int, val type: ScaleType) {
    val spelling: Spelling by lazy { Speller.spell(tonic, type.steps) }
    val useFlats: Boolean? get() = spelling.flats

    fun name(): String = spelling.name(tonic) + " " + type.hebrew
    fun scale(): List<Int> = type.steps.map { (tonic + it) % 12 }

    /** Scale notes for display; quarter-tone degrees get a ↓ mark. */
    fun scaleText(): String = type.steps.indices.joinToString("  ") { i ->
        spelling.name((tonic + type.steps[i]) % 12) + if (i in type.quarterFlat) "↓" else ""
    }

    /** Chords built by stacking thirds on each degree: numeral, triad, seventh chord. */
    fun diatonic(): List<Triple<String, Chord, Chord>> = cached

    private val cached: List<Triple<String, Chord, Chord>> by lazy {
        val s = type.steps
        val out = ArrayList<Triple<String, Chord, Chord>>()
        for (i in 0 until 7) {
            fun st(k: Int) = s[(i + k) % 7] + if (i + k >= 7) 12 else 0
            val triadQ = Theory.classify(listOf(0, st(2) - s[i], st(4) - s[i])) ?: continue
            val seventhQ = Theory.classify(listOf(0, st(2) - s[i], st(4) - s[i], st(6) - s[i])) ?: triadQ
            val root = (tonic + s[i]) % 12
            out.add(Triple(Theory.numeral(i, s[i], triadQ), Chord(root, triadQ), Chord(root, seventhQ)))
        }
        if (type == ScaleType.MINOR) {
            val v = (tonic + 7) % 12
            out.add(Triple("V", Chord(v, Quality.MAJ), Chord(v, Quality.DOM7)))
        }
        out
    }

    fun numeralOf(chord: Chord): String? {
        for ((num, triad, seventh) in diatonic()) if (triad == chord || seventh == chord) return num
        return null
    }

    companion object {
        fun major(t: Int) = Key(t, ScaleType.MAJOR)
        fun minor(t: Int) = Key(t, ScaleType.MINOR)
    }
}

object Theory {
    private val TERTIAN = listOf(
        Quality.MAJ, Quality.MIN, Quality.DIM, Quality.AUG,
        Quality.DOM7, Quality.MAJ7, Quality.MIN7, Quality.M7B5, Quality.DIM7, Quality.MMAJ7, Quality.AUGMAJ7
    )

    fun classify(intervals: List<Int>): Quality? {
        val set = intervals.map { ((it % 12) + 12) % 12 }.toSet()
        return TERTIAN.firstOrNull { it.intervals.size == intervals.size && it.pcSet == set }
    }

    private val ROMAN = arrayOf("I", "II", "III", "IV", "V", "VI", "VII")
    private val MAJOR_REF = intArrayOf(0, 2, 4, 5, 7, 9, 11)

    fun numeral(degree: Int, step: Int, q: Quality): String {
        val acc = when (step - MAJOR_REF[degree]) {
            -2 -> "bb"; -1 -> "b"; 1 -> "#"; 2 -> "##"; else -> ""
        }
        val lower = q == Quality.MIN || q == Quality.DIM
        val base = if (lower) ROMAN[degree].lowercase() else ROMAN[degree]
        val suffix = when (q) {
            Quality.DIM -> "°"
            Quality.AUG -> "+"
            else -> ""
        }
        return acc + base + suffix
    }
}

/** Gives each scale degree its own letter (D Eb F# G A Bb C rather than D D# F# ...). */
object Speller {
    private const val LETTERS = "CDEFGAB"
    private val NATURAL = intArrayOf(0, 2, 4, 5, 7, 9, 11)

    fun spell(tonic: Int, steps: IntArray): Spelling {
        var bestNames: Map<Int, String>? = null
        var bestCost = Int.MAX_VALUE
        for (letter in 0 until 7) {
            val tAcc = norm(tonic - NATURAL[letter])
            if (abs(tAcc) > 1) continue
            val names = HashMap<Int, String>()
            var cost = 0
            var ok = true
            for (i in steps.indices) {
                val l = (letter + i) % 7
                val pc = (tonic + steps[i]) % 12
                val acc = norm(pc - NATURAL[l])
                if (abs(acc) > 2) { ok = false; break }
                cost += abs(acc) + if (abs(acc) == 2) 4 else 0
                names[pc] = LETTERS[l] + accidental(acc)
            }
            if (ok && (cost < bestCost || cost == bestCost && tAcc < 0)) { bestCost = cost; bestNames = names }
        }
        val names = bestNames ?: emptyMap()
        val flatCount = names.values.count { it.contains('b') }
        val sharpCount = names.values.count { it.contains('#') }
        val flats: Boolean? = when {
            flatCount > sharpCount -> true
            sharpCount > flatCount -> false
            else -> null
        }
        return Spelling(names, flats)
    }

    private fun norm(d: Int): Int {
        var x = ((d % 12) + 12) % 12
        if (x > 6) x -= 12
        return x
    }

    private fun accidental(a: Int) = when (a) {
        -2 -> "bb"; -1 -> "b"; 1 -> "#"; 2 -> "##"; else -> ""
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
    // Krumhansl-Kessler profiles: the most reliable choice between major and minor
    private val MAJOR = doubleArrayOf(6.35, 2.23, 3.48, 2.33, 4.38, 4.09, 2.52, 5.19, 2.39, 3.66, 2.29, 2.88)
    private val MINOR = doubleArrayOf(6.33, 2.68, 3.52, 5.38, 2.60, 3.53, 2.54, 4.75, 3.98, 2.69, 3.34, 3.17)

    /** All candidates sorted best first. Other scales win only when they clearly fit better. */
    fun rank(chroma: DoubleArray): List<KeyResult> {
        if (chroma.sum() <= 1e-9) return emptyList()
        val kk = ArrayList<KeyResult>(24)
        for (t in 0 until 12) {
            kk.add(KeyResult(Key.major(t), correlate(chroma, rotate(MAJOR, t))))
            kk.add(KeyResult(Key.minor(t), correlate(chroma, rotate(MINOR, t))))
        }
        kk.sortByDescending { it.score }

        // Same yardstick for every scale, so they can be compared fairly
        var bestPlain = -1.0
        var bestOther: KeyResult? = null
        var bestOtherGain = 0.0
        for (type in ScaleType.values()) {
            val margin = type.margin ?: continue
            for (t in 0 until 12) {
                val s = correlate(chroma, template(type, t))
                if (type == ScaleType.MAJOR || type == ScaleType.MINOR) {
                    if (s > bestPlain) bestPlain = s
                } else {
                    val gain = s - margin
                    if (bestOther == null || gain > bestOtherGain) {
                        bestOther = KeyResult(Key(t, type), s)
                        bestOtherGain = gain
                    }
                }
            }
        }
        return if (bestOther != null && bestOtherGain > bestPlain) listOf(bestOther) + kk else kk
    }

    private fun template(type: ScaleType, tonic: Int): DoubleArray {
        val t = DoubleArray(12)
        type.steps.forEachIndexed { i, st ->
            t[(tonic + st) % 12] = when {
                i == 0 -> 3.0
                st == 7 -> 2.0
                i == 2 -> 1.5
                else -> 1.0
            }
        }
        return t
    }

    private fun rotate(p: DoubleArray, shift: Int) = DoubleArray(12) { p[((it - shift) % 12 + 12) % 12] }

    private fun correlate(x: DoubleArray, y: DoubleArray): Double {
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
    /**
     * Scores every detectable chord against the chroma. [bass] (low-register chroma) adds
     * weight to chords whose root is the bass note; [keyPrior] favours chords of the key.
     */
    fun score(chord: Chord, chroma: DoubleArray, norm: Double, bassPc: Int, diatonic: Set<Chord>, keyPrior: Double): Double {
        var dot = 0.0
        for (iv in chord.quality.intervals) dot += chroma[(chord.root + iv) % 12]
        var s = dot / (norm * sqrt(chord.quality.intervals.size.toDouble())) * chord.quality.bias
        if (chord in diatonic) s *= keyPrior
        if (bassPc == chord.root) s *= 1.06
        return s
    }

    fun best(chroma: DoubleArray, bass: DoubleArray?, key: Key?, keyPrior: Double = 1.04): ChordResult? {
        val norm = sqrt(chroma.sumOf { it * it })
        if (norm < 1e-9) return null
        val bassPc = bassPc(bass)
        val diatonic = diatonicSet(key)
        var best: ChordResult? = null
        for (root in 0 until 12) for (q in Quality.values()) {
            if (!q.detect) continue
            val c = Chord(root, q)
            val s = score(c, chroma, norm, bassPc, diatonic, keyPrior)
            if (best == null || s > best.score) best = ChordResult(c, s)
        }
        return best
    }

    fun scoreOf(chord: Chord, chroma: DoubleArray, bass: DoubleArray?, key: Key?, keyPrior: Double): Double {
        val norm = sqrt(chroma.sumOf { it * it })
        if (norm < 1e-9) return 0.0
        return score(chord, chroma, norm, bassPc(bass), diatonicSet(key), keyPrior)
    }

    private fun diatonicSet(key: Key?) = key?.diatonic()?.flatMap { listOf(it.second, it.third) }?.toSet() ?: emptySet()

    private fun bassPc(bass: DoubleArray?): Int {
        if (bass == null) return -1
        var bi = -1
        var bv = 0.0
        for (i in 0 until 12) if (bass[i] > bv) { bv = bass[i]; bi = i }
        // Only trust a clearly dominant bass note
        val total = bass.sum()
        return if (total > 0 && bv / total > 0.35) bi else -1
    }
}

object ChordParser {
    private val SUFFIXES: Map<String, Quality> = linkedMapOf(
        "" to Quality.MAJ, "maj" to Quality.MAJ, "M" to Quality.MAJ, "major" to Quality.MAJ,
        "m" to Quality.MIN, "min" to Quality.MIN, "-" to Quality.MIN, "minor" to Quality.MIN,
        "dim" to Quality.DIM, "°" to Quality.DIM, "o" to Quality.DIM,
        "aug" to Quality.AUG, "+" to Quality.AUG, "#5" to Quality.AUG,
        "7" to Quality.DOM7, "dom7" to Quality.DOM7,
        "maj7" to Quality.MAJ7, "M7" to Quality.MAJ7, "Δ" to Quality.MAJ7, "Δ7" to Quality.MAJ7, "ma7" to Quality.MAJ7,
        "m7" to Quality.MIN7, "min7" to Quality.MIN7, "-7" to Quality.MIN7,
        "m7b5" to Quality.M7B5, "ø" to Quality.M7B5, "ø7" to Quality.M7B5, "min7b5" to Quality.M7B5, "-7b5" to Quality.M7B5,
        "dim7" to Quality.DIM7, "°7" to Quality.DIM7, "o7" to Quality.DIM7,
        "m(maj7)" to Quality.MMAJ7, "mmaj7" to Quality.MMAJ7, "mM7" to Quality.MMAJ7, "minmaj7" to Quality.MMAJ7,
        "maj7#5" to Quality.AUGMAJ7, "+maj7" to Quality.AUGMAJ7, "augmaj7" to Quality.AUGMAJ7,
        "sus4" to Quality.SUS4, "sus" to Quality.SUS4, "sus2" to Quality.SUS2,
        "7sus4" to Quality.SEVSUS4, "7sus" to Quality.SEVSUS4,
        "6" to Quality.SIX, "maj6" to Quality.SIX, "m6" to Quality.MIN6, "min6" to Quality.MIN6,
        "add9" to Quality.ADD9, "add2" to Quality.ADD9,
        "9" to Quality.NINE, "maj9" to Quality.MAJ9, "M9" to Quality.MAJ9, "m9" to Quality.MIN9, "min9" to Quality.MIN9
    )
    private val LETTER_PC = mapOf('C' to 0, 'D' to 2, 'E' to 4, 'F' to 5, 'G' to 7, 'A' to 9, 'B' to 11)

    fun parse(input: String): Chord? {
        val s = input.trim().replace(" ", "").replace('♯', '#').replace('♭', 'b')
        if (s.isEmpty()) return null
        val letter = s[0].uppercaseChar()
        var pc = LETTER_PC[letter] ?: return null
        var i = 1
        while (i < s.length && (s[i] == '#' || s[i] == 'b')) {
            // "Bb" is B-flat, but in "Cb5"-like typos we still read b as flat
            pc += if (s[i] == '#') 1 else -1
            i++
        }
        val suffix = s.substring(i)
        val q = SUFFIXES[suffix]
            ?: SUFFIXES.entries.firstOrNull { it.key.equals(suffix, ignoreCase = true) && it.key != "M" }?.value
            ?: return null
        return Chord(((pc % 12) + 12) % 12, q)
    }
}
