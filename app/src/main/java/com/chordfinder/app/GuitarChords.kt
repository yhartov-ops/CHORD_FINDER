package com.chordfinder.app

/**
 * A guitar voicing. frets[0] is the low E string, frets[5] the high E string.
 * -1 = muted, 0 = open. barreFret > 0 draws a barre across barreFrom..barreTo.
 */
data class GuitarVoicing(
    val frets: IntArray,
    val barreFret: Int = 0,
    val barreFrom: Int = 0,
    val barreTo: Int = 0
)

object GuitarChords {
    const val X = -1
    private const val M = Int.MIN_VALUE // muted, inside relative shapes

    /** Open-string pitch classes, low E to high E. */
    val OPEN = intArrayOf(4, 9, 2, 7, 11, 4)

    // Familiar open-position voicings, keyed by "root:quality".
    private val OPEN_CHORDS: Map<String, IntArray> = mapOf(
        "0:MAJ" to intArrayOf(X, 3, 2, 0, 1, 0),   // C
        "0:DOM7" to intArrayOf(X, 3, 2, 3, 1, 0),  // C7
        "0:MAJ7" to intArrayOf(X, 3, 2, 0, 0, 0),  // Cmaj7
        "2:MAJ" to intArrayOf(X, X, 0, 2, 3, 2),   // D
        "2:MIN" to intArrayOf(X, X, 0, 2, 3, 1),   // Dm
        "2:DOM7" to intArrayOf(X, X, 0, 2, 1, 2),  // D7
        "2:MAJ7" to intArrayOf(X, X, 0, 2, 2, 2),  // Dmaj7
        "2:MIN7" to intArrayOf(X, X, 0, 2, 1, 1),  // Dm7
        "2:SUS4" to intArrayOf(X, X, 0, 2, 3, 3),  // Dsus4
        "2:SUS2" to intArrayOf(X, X, 0, 2, 3, 0),  // Dsus2
        "7:MAJ" to intArrayOf(3, 2, 0, 0, 0, 3),   // G
        "7:DOM7" to intArrayOf(3, 2, 0, 0, 0, 1),  // G7
        "5:MAJ7" to intArrayOf(X, X, 3, 2, 1, 0),  // Fmaj7
    )

    // Movable shapes as offsets from the root fret. Root on the 6th string (E shape).
    private val E_SHAPES: Map<Quality, IntArray> = mapOf(
        Quality.MAJ to intArrayOf(0, 2, 2, 1, 0, 0),
        Quality.MIN to intArrayOf(0, 2, 2, 0, 0, 0),
        Quality.DOM7 to intArrayOf(0, 2, 0, 1, 0, 0),
        Quality.MIN7 to intArrayOf(0, 2, 0, 0, 0, 0),
        Quality.MAJ7 to intArrayOf(0, M, 1, 1, 0, M),
        Quality.DIM to intArrayOf(0, 1, 2, 0, M, M),
        Quality.M7B5 to intArrayOf(0, M, 0, 0, -1, M),
        Quality.SUS4 to intArrayOf(0, 2, 2, 2, 0, 0),
    )

    // Root on the 5th string (A shape).
    private val A_SHAPES: Map<Quality, IntArray> = mapOf(
        Quality.MAJ to intArrayOf(M, 0, 2, 2, 2, 0),
        Quality.MIN to intArrayOf(M, 0, 2, 2, 1, 0),
        Quality.DOM7 to intArrayOf(M, 0, 2, 0, 2, 0),
        Quality.MIN7 to intArrayOf(M, 0, 2, 0, 1, 0),
        Quality.MAJ7 to intArrayOf(M, 0, 2, 1, 2, 0),
        Quality.DIM to intArrayOf(M, 0, 1, 2, 1, M),
        Quality.M7B5 to intArrayOf(M, 0, 1, 0, 1, M),
        Quality.SUS4 to intArrayOf(M, 0, 2, 2, 3, 0),
        Quality.SUS2 to intArrayOf(M, 0, 2, 2, 0, 0),
    )

    fun voicing(chord: Chord): GuitarVoicing {
        OPEN_CHORDS["${chord.root}:${chord.quality.name}"]?.let { return GuitarVoicing(it) }

        val candidates = ArrayList<GuitarVoicing>()
        E_SHAPES[chord.quality]?.let { candidates.add(place(it, (chord.root - 4 + 12) % 12)) }
        A_SHAPES[chord.quality]?.let { candidates.add(place(it, (chord.root - 9 + 12) % 12)) }
        // Prefer the voicing closest to the nut
        return candidates.minByOrNull { v -> v.frets.filter { it > 0 }.maxOrNull() ?: 0 }
            ?: GuitarVoicing(intArrayOf(X, X, X, X, X, X))
    }

    private fun place(shape: IntArray, rootFretIn: Int): GuitarVoicing {
        var rootFret = rootFretIn
        val minOffset = shape.filter { it != M }.minOrNull() ?: 0
        if (rootFret + minOffset < 0) rootFret += 12
        val frets = IntArray(6) { i -> if (shape[i] == M) X else shape[i] + rootFret }
        // Barre: strings with offset 0 when the root fret is above the nut
        if (rootFret > 0) {
            val zero = (0 until 6).filter { shape[it] == 0 }
            if (zero.size >= 2 && (zero.first()..zero.last()).none { shape[it] == M }) {
                return GuitarVoicing(frets, rootFret, zero.first(), zero.last())
            }
        }
        return GuitarVoicing(frets)
    }

    /** Pitch classes that sound for a voicing (for verification). */
    fun soundingPitchClasses(v: GuitarVoicing): List<Int> =
        (0 until 6).filter { v.frets[it] >= 0 }.map { (OPEN[it] + v.frets[it]) % 12 }
}
