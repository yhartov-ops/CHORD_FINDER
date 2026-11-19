package com.chordfinder.app

import kotlin.math.abs

/**
 * A guitar voicing. frets[0] is the low E string, frets[5] the high E string.
 * -1 = muted, 0 = open. barreFret > 0 draws a barre across barreFrom..barreTo.
 */
class GuitarVoicing(
    val frets: IntArray,
    val barreFret: Int = 0,
    val barreFrom: Int = 0,
    val barreTo: Int = 0,
    val cost: Double = 0.0
) {
    val bassString: Int get() = frets.indexOfFirst { it >= 0 }
    val position: Int get() = frets.filter { it > 0 }.minOrNull() ?: 0
}

object GuitarChords {
    const val X = -1

    /** Open-string pitch classes, low E to high E. */
    val OPEN = intArrayOf(4, 9, 2, 7, 11, 4)

    private const val MAX_FRET = 15

    private val cache = HashMap<Chord, List<GuitarVoicing>>()

    /** Best voicings first, spread over different areas of the neck. */
    fun voicings(chord: Chord, max: Int = 6): List<GuitarVoicing> {
        val all = cache.getOrPut(chord) { search(chord) }
        val picked = ArrayList<GuitarVoicing>()
        for (v in all) {
            val clash = picked.any { p ->
                p.frets.contentEquals(v.frets) ||
                    (p.bassString == v.bassString && abs(p.position - v.position) < 3)
            }
            if (!clash) picked.add(v)
            if (picked.size >= max) break
        }
        return picked
    }

    private fun search(chord: Chord): List<GuitarVoicing> {
        val tones = chord.pitchClasses().toSet()
        val root = chord.root
        // The perfect fifth may be left out of chords with four or more notes
        val optional = if (tones.size >= 4 && 7 in chord.quality.intervals) setOf((root + 7) % 12) else emptySet()
        val required = tones - optional

        val found = HashMap<String, GuitarVoicing>()
        val options = Array(6) { IntArray(8) }
        val counts = IntArray(6)
        val current = IntArray(6)

        fun rec(s: Int) {
            if (s == 6) {
                evaluate(current, root, required)?.let { v ->
                    val key = v.frets.joinToString(",")
                    val old = found[key]
                    if (old == null || v.cost < old.cost) found[key] = v
                }
                return
            }
            for (i in 0 until counts[s]) {
                current[s] = options[s][i]
                rec(s + 1)
            }
        }

        for (start in 1..(MAX_FRET - 3)) {
            val allowOpen = start <= 3
            for (s in 0 until 6) {
                var c = 0
                options[s][c++] = X
                if (allowOpen && OPEN[s] in tones) options[s][c++] = 0
                for (f in start..start + 3) if ((OPEN[s] + f) % 12 in tones) options[s][c++] = f
                counts[s] = c
            }
            rec(0)
        }
        return found.values.sortedBy { it.cost }
    }

    private fun evaluate(frets: IntArray, root: Int, required: Set<Int>): GuitarVoicing? {
        val bass = frets.indexOfFirst { it >= 0 }
        if (bass < 0 || bass > 2) return null
        if ((OPEN[bass] + frets[bass]) % 12 != root) return null

        // Muting: at most one muted string inside the chord, plus optionally the top string
        var innerMutes = 0
        for (s in bass + 1 until 5) if (frets[s] < 0) innerMutes++
        if (innerMutes > 1) return null
        val sounding = (bass until 6).count { frets[it] >= 0 }
        if (sounding < 4) return null

        val pcs = HashSet<Int>()
        for (s in bass until 6) if (frets[s] >= 0) pcs.add((OPEN[s] + frets[s]) % 12)
        if (!pcs.containsAll(required)) return null

        val fretted = (0 until 6).filter { frets[it] > 0 }
        val minF = fretted.minOfOrNull { frets[it] } ?: 0
        val maxF = fretted.maxOfOrNull { frets[it] } ?: 0
        if (maxF - minF > 3) return null

        // Fingers: a barre is possible when no open string lies under it
        var barreFret = 0
        var barreFrom = 0
        var barreTo = 0
        val atMin = fretted.filter { frets[it] == minF }
        var fingers = fretted.size
        if (atMin.size >= 2) {
            val from = atMin.first()
            val to = atMin.last()
            val clear = (from..to).all { frets[it] != 0 }
            if (clear) {
                val barreFingers = 1 + fretted.count { frets[it] > minF }
                if (fingers > 4) {
                    fingers = barreFingers
                    barreFret = minF
                    barreFrom = from
                    barreTo = to
                }
            }
        }
        if (fingers > 4) return null

        val opens = (bass until 6).count { frets[it] == 0 }
        val mutes = 6 - sounding
        var cost = 0.0
        cost += maxF * 0.25
        cost += mutes * 0.8
        cost += innerMutes * 2.0
        cost += (maxF - minF) * 0.35
        cost += fingers * 0.2
        // Classic full barre shapes (E and A forms) are what players expect first
        if (barreFret > 0 && barreFrom == bass && barreTo == 5) cost -= 0.5
        if (maxF <= 3) cost -= opens * 0.15
        return GuitarVoicing(frets.copyOf(), barreFret, barreFrom, barreTo, cost)
    }

    fun soundingPitchClasses(v: GuitarVoicing): List<Int> =
        (0 until 6).filter { v.frets[it] >= 0 }.map { (OPEN[it] + v.frets[it]) % 12 }
}
