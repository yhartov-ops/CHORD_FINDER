package com.chordfinder.app

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private val tracker = HarmonyTracker()
    private lateinit var capture: AudioCapture

    private var selectedChord: Chord? = null
    private var showSevenths = false
    private var chipsKey: Key? = null
    private var chipsSevenths = false
    private var chipsSelected: Chord? = null
    private var lastShownChord: Chord? = null

    private lateinit var listenButton: Button
    private lateinit var statusText: TextView
    private lateinit var chordText: TextView
    private lateinit var chordSub: TextView
    private lateinit var historyText: TextView
    private lateinit var keyText: TextView
    private lateinit var keySub: TextView
    private lateinit var scaleNotesText: TextView
    private lateinit var scalePiano: PianoView
    private lateinit var chromaView: ChromaView
    private lateinit var triadsButton: Button
    private lateinit var seventhsButton: Button
    private lateinit var chipsBox: LinearLayout
    private lateinit var detailTitle: TextView
    private lateinit var detailSub: TextView
    private lateinit var detailPiano: PianoView
    private lateinit var detailGuitar: GuitarView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Palette.BG
        window.navigationBarColor = Palette.BG
        capture = AudioCapture(
            onFrame = { chroma, level ->
                runOnUiThread {
                    tracker.add(chroma)
                    chromaView.update(chroma, level)
                    refresh()
                }
            },
            onError = { msg ->
                runOnUiThread {
                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                    updateListenButton()
                }
            }
        )
        setContentView(buildUi())
        refresh()
    }

    override fun onPause() {
        super.onPause()
        if (capture.isRunning) {
            capture.stop()
            updateListenButton()
        }
    }

    // ---------- Listening ----------

    private fun toggleListening() {
        if (capture.isRunning) {
            capture.stop()
        } else {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQ_MIC)
                return
            }
            capture.start()
        }
        updateListenButton()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQ_MIC) return
        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            capture.start()
            updateListenButton()
        } else {
            Toast.makeText(this, "בלי הרשאת מיקרופון אפשר רק לבחור סולם ידנית ולעיין באקורדים", Toast.LENGTH_LONG).show()
        }
    }

    private fun updateListenButton() {
        val on = capture.isRunning
        listenButton.text = if (on) "עצור האזנה" else "התחל להאזין"
        listenButton.background = pill(if (on) Palette.SURFACE_2 else Palette.AMBER)
        listenButton.setTextColor(if (on) Palette.TEXT else Palette.BG)
        statusText.text = if (on) "מאזין. נגן או השמע מוזיקה ליד הטלפון." else "ההאזנה כבויה"
        if (on) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    // ---------- Key selection ----------

    private fun chooseKey() {
        val keys = Key.all().sortedWith(compareBy({ it.minor }, { it.tonic }))
        val labels = listOf("זיהוי אוטומטי") + keys.map { it.name() }
        AlertDialog.Builder(this)
            .setTitle("בחר סולם")
            .setItems(labels.toTypedArray()) { _, which ->
                tracker.lockedKey = if (which == 0) null else keys[which - 1]
                selectedChord = null
                refresh()
            }
            .show()
    }

    private fun resetAnalysis() {
        tracker.reset()
        selectedChord = null
        lastShownChord = null
        chromaView.update(DoubleArray(12), -100.0)
        refresh()
    }

    // ---------- Rendering ----------

    private fun refresh() {
        val key = tracker.key
        val flats = key?.useFlats
        val scale = key?.scale()?.toSet() ?: emptySet()

        // Key
        if (key == null) {
            keyText.text = "—"
            keySub.text = "השמע מוזיקה, או בחר סולם ידנית"
            scaleNotesText.text = ""
        } else {
            keyText.text = key.name()
            keySub.text = if (tracker.lockedKey != null) {
                "נבחר ידנית"
            } else {
                val r = tracker.keyRanking
                val fit = (r[0].score * 100).toInt().coerceIn(0, 100)
                val alt = r.getOrNull(1)?.key?.name()
                "התאמה $fit%" + (if (alt != null) "   ·   חלופה: $alt" else "")
            }
            scaleNotesText.text = "תווי הסולם: " + key.scale().joinToString("  ") { Notes.name(it, flats) }
        }
        scalePiano.flats = flats
        scalePiano.scalePcs = scale
        chromaView.flats = flats
        chromaView.scalePcs = scale

        // Current chord
        val current = tracker.currentChord?.chord
        if (current == null) {
            chordText.text = "—"
            chordSub.text = if (capture.isRunning) "מחכה לאקורד ברור" else "האקורד שנשמע יופיע כאן"
        } else {
            chordText.text = current.name(flats)
            val numeral = key?.numeralOf(current)
            chordSub.text = current.quality.hebrew + if (numeral != null) "   ·   דרגה $numeral בסולם" else "   ·   מחוץ לסולם"
        }
        historyText.text = if (tracker.history.isEmpty()) "" else
            tracker.history.takeLast(8).joinToString("  ›  ") { it.name(flats) }
        historyText.visibility = if (tracker.history.isEmpty()) View.GONE else View.VISIBLE

        // Chips
        triadsButton.background = pill(if (!showSevenths) Palette.TEAL else Palette.SURFACE_2)
        triadsButton.setTextColor(if (!showSevenths) Palette.BG else Palette.TEXT)
        seventhsButton.background = pill(if (showSevenths) Palette.TEAL else Palette.SURFACE_2)
        seventhsButton.setTextColor(if (showSevenths) Palette.BG else Palette.TEXT)
        if (key != chipsKey || showSevenths != chipsSevenths || selectedChord != chipsSelected) buildChips(key)

        // Detail: the tapped chord, or follow the live chord
        val detail = selectedChord ?: current
        if (detail != lastShownChord || selectedChord == null) showDetail(detail, flats, key)
    }

    private fun buildChips(key: Key?) {
        chipsKey = key
        chipsSevenths = showSevenths
        chipsSelected = selectedChord
        chipsBox.removeAllViews()
        if (key == null) {
            chipsBox.addView(text("כשיזוהה סולם (או תבחר אחד) יופיעו כאן כל האקורדים שלו", 14f, Palette.MUTED))
            return
        }
        val flats = key.useFlats
        val items = key.diatonic()
        var row: LinearLayout? = null
        items.forEachIndexed { i, (numeral, triad, seventh) ->
            if (i % 4 == 0) {
                row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutDirection = View.LAYOUT_DIRECTION_LTR
                }
                chipsBox.addView(row, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(8) })
            }
            val chord = if (showSevenths) seventh else triad
            val isSel = chord == selectedChord
            val chip = TextView(this).apply {
                gravity = Gravity.CENTER
                setPadding(dp(4), dp(10), dp(4), dp(10))
                text = "${chord.name(flats)}\n$numeral"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                setTextColor(if (isSel) Palette.BG else Palette.TEXT)
                typeface = Typeface.DEFAULT_BOLD
                background = rounded(if (isSel) Palette.AMBER else Palette.SURFACE_2, 12f)
                setOnClickListener {
                    selectedChord = if (selectedChord == chord) null else chord
                    refresh()
                }
            }
            row!!.addView(chip, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply {
                marginStart = dp(4); marginEnd = dp(4)
            })
        }
        // Keep the last row's chips the same width as full rows
        val remainder = items.size % 4
        if (remainder != 0) repeat(4 - remainder) {
            row!!.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f).apply { marginStart = dp(4); marginEnd = dp(4) })
        }
    }

    private fun showDetail(chord: Chord?, flats: Boolean?, key: Key?) {
        lastShownChord = chord
        if (chord == null) {
            detailTitle.text = "בחר אקורד"
            detailSub.text = "הקש על אקורד ברשימה, או השמע מוזיקה והאקורד שנשמע יוצג כאן"
            detailPiano.chordNotes = emptyList()
            detailGuitar.voicing = null
            detailGuitar.visibility = View.GONE
            return
        }
        detailGuitar.visibility = View.VISIBLE
        detailTitle.text = chord.name(flats)
        val notes = chord.pitchClasses().joinToString("  ") { Notes.name(it, flats) }
        val numeral = key?.numeralOf(chord)
        val follow = if (selectedChord == null) "   ·   עוקב אחרי ההאזנה" else ""
        detailSub.text = "${chord.quality.hebrew}   ·   $notes" +
            (if (numeral != null) "   ·   $numeral" else "") + follow
        detailPiano.flats = flats
        detailPiano.rootPc = chord.root
        detailPiano.chordNotes = chord.quality.intervals.map { chord.root + it }
        detailGuitar.flats = flats
        detailGuitar.rootPc = chord.root
        detailGuitar.voicing = GuitarChords.voicing(chord)
    }

    // ---------- Layout ----------

    private fun buildUi(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(20), dp(16), dp(32))
        }

        root.addView(text("מזהה אקורדים וסולמות", 15f, Palette.MUTED))
        listenButton = Button(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            typeface = Typeface.DEFAULT_BOLD
            isAllCaps = false
            setOnClickListener { toggleListening() }
        }
        root.addView(listenButton, LinearLayout.LayoutParams(MATCH_PARENT, dp(56)).apply { topMargin = dp(12) })
        statusText = text("", 13f, Palette.MUTED).apply { gravity = Gravity.CENTER }
        root.addView(statusText, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(6) })

        // Current chord: the large, central element
        val now = panel()
        chordText = TextView(this).apply {
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 84f)
            typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
            setTextColor(Palette.AMBER)
            textDirection = View.TEXT_DIRECTION_LTR
            setOnClickListener { selectedChord = null; refresh() }
        }
        now.addView(chordText)
        chordSub = text("", 15f, Palette.TEXT).apply { gravity = Gravity.CENTER }
        now.addView(chordSub)
        historyText = text("", 14f, Palette.MUTED).apply {
            gravity = Gravity.CENTER
            textDirection = View.TEXT_DIRECTION_LTR
        }
        now.addView(historyText, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(10) })
        root.addView(now, panelParams())

        // Key
        val keyPanel = panel()
        keyPanel.addView(text("סולם", 13f, Palette.MUTED))
        keyText = text("", 30f, Palette.TEXT).apply { typeface = Typeface.DEFAULT_BOLD }
        keyPanel.addView(keyText)
        keySub = text("", 14f, Palette.MUTED)
        keyPanel.addView(keySub)
        val keyButtons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        keyButtons.addView(smallButton("בחר סולם") { chooseKey() }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginEnd = dp(8) })
        keyButtons.addView(smallButton("התחל זיהוי מחדש") { resetAnalysis() }, LinearLayout.LayoutParams(0, dp(44), 1f))
        keyPanel.addView(keyButtons, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(12) })
        scaleNotesText = text("", 15f, Palette.TEXT).apply { textDirection = View.TEXT_DIRECTION_LTR; gravity = Gravity.START }
        keyPanel.addView(scaleNotesText, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(14) })
        scalePiano = PianoView(this)
        keyPanel.addView(scalePiano, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(8) })
        root.addView(keyPanel, panelParams())

        // Live notes
        val notesPanel = panel()
        notesPanel.addView(text("תווים שנשמעים עכשיו", 13f, Palette.MUTED))
        chromaView = ChromaView(this).apply { layoutDirection = View.LAYOUT_DIRECTION_LTR }
        notesPanel.addView(chromaView, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(8) })
        root.addView(notesPanel, panelParams())

        // Chords in the key
        val chordsPanel = panel()
        chordsPanel.addView(text("אקורדים בסולם", 13f, Palette.MUTED))
        val toggle = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        triadsButton = smallButton("משולשים") { showSevenths = false; refresh() }
        seventhsButton = smallButton("ספטאקורדים") { showSevenths = true; refresh() }
        toggle.addView(triadsButton, LinearLayout.LayoutParams(0, dp(40), 1f).apply { marginEnd = dp(8) })
        toggle.addView(seventhsButton, LinearLayout.LayoutParams(0, dp(40), 1f))
        chordsPanel.addView(toggle, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(10); bottomMargin = dp(12) })
        chipsBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        chordsPanel.addView(chipsBox)
        root.addView(chordsPanel, panelParams())

        // Chord detail
        val detail = panel()
        detailTitle = text("", 34f, Palette.TEXT).apply {
            typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
            textDirection = View.TEXT_DIRECTION_LTR
            gravity = Gravity.START
        }
        detail.addView(detailTitle)
        detailSub = text("", 14f, Palette.MUTED)
        detail.addView(detailSub)
        detail.addView(text("פסנתר", 13f, Palette.MUTED), LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(16) })
        detailPiano = PianoView(this)
        detail.addView(detailPiano, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(6) })
        detail.addView(text("גיטרה", 13f, Palette.MUTED), LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(16) })
        detailGuitar = GuitarView(this)
        detail.addView(detailGuitar, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(6) })
        root.addView(detail, panelParams())

        updateListenButton()
        return ScrollView(this).apply {
            setBackgroundColor(Palette.BG)
            isFillViewport = true
            addView(root, MATCH_PARENT, WRAP_CONTENT)
        }
    }

    private fun panel() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(14), dp(16), dp(16))
        background = rounded(Palette.SURFACE, 18f)
    }

    private fun panelParams() = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(14) }

    private fun text(s: String, sizeSp: Float, color: Int) = TextView(this).apply {
        text = s
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        setTextColor(color)
    }

    private fun smallButton(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        setTextColor(Palette.TEXT)
        background = pill(Palette.SURFACE_2)
        stateListAnimator = null
        setOnClickListener { onClick() }
    }

    private fun rounded(color: Int, radiusDp: Float) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radiusDp).toFloat()
    }

    private fun pill(color: Int) = rounded(color, 28f)

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()
    private fun dp(v: Float): Int = (v * resources.displayMetrics.density + 0.5f).toInt()

    companion object {
        private const val REQ_MIC = 1
    }
}
