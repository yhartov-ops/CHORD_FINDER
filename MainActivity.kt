package com.chordfinder.app

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private val tracker = HarmonyTracker()
    private lateinit var capture: AudioCapture

    /** Chord the user picked (from the list or the search box); null = follow the live chord. */
    private var selectedChord: Chord? = null
    private var selectedSpelling: Spelling? = null
    private var showSevenths = false
    private var moreVoicings = false

    private var chipsKey: Key? = null
    private var chipsSevenths = false
    private var chipsSelected: Chord? = null
    private var shownChord: Chord? = null
    private var shownMore = false
    private var shownSpellingKey: Key? = null

    private lateinit var listenButton: Button
    private lateinit var statusText: TextView
    private val stabilityButtons = HashMap<Stability, Button>()
    private lateinit var chordText: TextView
    private lateinit var chordSub: TextView
    private lateinit var historyText: TextView
    private lateinit var keyText: TextView
    private lateinit var keySub: TextView
    private lateinit var scaleNotesText: TextView
    private lateinit var quarterNote: TextView
    private lateinit var scalePiano: PianoView
    private lateinit var chromaView: ChromaView
    private lateinit var triadsButton: Button
    private lateinit var seventhsButton: Button
    private lateinit var chipsBox: LinearLayout
    private lateinit var searchInput: EditText
    private lateinit var searchError: TextView
    private lateinit var detailTitle: TextView
    private lateinit var detailSub: TextView
    private lateinit var detailPiano: PianoView
    private lateinit var guitarLabel: TextView
    private lateinit var guitarGrid: LinearLayout
    private lateinit var moreButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Palette.BG
        window.navigationBarColor = Palette.BG
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        capture = AudioCapture(
            onFrame = { frame ->
                runOnUiThread {
                    tracker.add(frame)
                    chromaView.update(frame.chroma, frame.levelDb)
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
            Toast.makeText(this, "בלי הרשאת מיקרופון אפשר רק לבחור סולם ידנית ולחפש אקורדים", Toast.LENGTH_LONG).show()
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

    private fun setStability(s: Stability) {
        tracker.stability = s
        for ((st, b) in stabilityButtons) {
            val on = st == s
            b.background = pill(if (on) Palette.TEAL else Palette.SURFACE_2)
            b.setTextColor(if (on) Palette.BG else Palette.TEXT)
        }
    }

    // ---------- Key selection ----------

    private fun chooseKey() {
        val types = ScaleType.values()
        val labels = listOf("זיהוי אוטומטי") + types.map { t ->
            if (t.description.isEmpty()) t.hebrew else "${t.hebrew}  (${t.description})"
        }
        AlertDialog.Builder(this)
            .setTitle("סוג הסולם")
            .setItems(labels.toTypedArray()) { _, which ->
                if (which == 0) {
                    tracker.lockedKey = null
                    selectedChord = null
                    refresh()
                } else chooseTonic(types[which - 1])
            }
            .show()
    }

    private fun chooseTonic(type: ScaleType) {
        val keys = (0 until 12).map { Key(it, type) }
        val labels = keys.map { it.spelling.name(it.tonic) }
        AlertDialog.Builder(this)
            .setTitle("${type.hebrew}: על איזה תו?")
            .setItems(labels.toTypedArray()) { _, which ->
                tracker.lockedKey = keys[which]
                selectedChord = null
                refresh()
            }
            .show()
    }

    private fun resetAnalysis() {
        tracker.reset()
        selectedChord = null
        chromaView.update(DoubleArray(12), -100.0)
        refresh()
    }

    // ---------- Chord search ----------

    private fun searchChord() {
        val text = searchInput.text.toString()
        val chord = ChordParser.parse(text)
        if (chord == null) {
            searchError.visibility = View.VISIBLE
            searchError.text = if (text.isBlank()) "הקלד שם אקורד, למשל Am או F#7"
            else "לא זיהיתי את \"$text\". דוגמאות: Am, F#7, Bbmaj7, Cdim, Gsus4, Em7b5, D9"
            return
        }
        searchError.visibility = View.GONE
        val afterLetter = text.trim().drop(1)
        selectedSpelling = when {
            afterLetter.startsWith("b") || afterLetter.startsWith("♭") -> Spelling(emptyMap(), true)
            afterLetter.startsWith("#") || afterLetter.startsWith("♯") -> Spelling(emptyMap(), false)
            else -> null
        }
        selectedChord = chord
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(searchInput.windowToken, 0)
        searchInput.clearFocus()
        refresh()
    }

    // ---------- Rendering ----------

    private fun refresh() {
        val key = tracker.key
        val sp = key?.spelling ?: Spelling.NEUTRAL
        val scale = key?.scale()?.toSet() ?: emptySet()

        // Key
        if (key == null) {
            keyText.text = "—"
            keySub.text = "השמע מוזיקה, או בחר סולם ידנית"
            scaleNotesText.text = ""
            quarterNote.visibility = View.GONE
        } else {
            keyText.text = key.name()
            val desc = if (key.type.description.isNotEmpty()) key.type.description else ""
            keySub.text = if (tracker.lockedKey != null) {
                listOf(desc, "נבחר ידנית").filter { it.isNotEmpty() }.joinToString("   ·   ")
            } else {
                val r = tracker.keyRanking
                val fit = (r[0].score * 100).toInt().coerceIn(0, 100)
                val alt = r.drop(1).firstOrNull { it.key != r[0].key }?.key?.name()
                listOf(desc, "התאמה $fit%", if (alt != null) "חלופה: $alt" else "")
                    .filter { it.isNotEmpty() }.joinToString("   ·   ")
            }
            scaleNotesText.text = "תווי הסולם:  " + key.scaleText()
            quarterNote.visibility = if (key.type.quarterFlat.isEmpty()) View.GONE else View.VISIBLE
        }
        scalePiano.spelling = sp
        scalePiano.scalePcs = scale
        chromaView.spelling = sp
        chromaView.scalePcs = scale

        // Current chord
        val current = tracker.currentChord?.chord
        if (current == null) {
            chordText.text = "—"
            chordSub.text = if (capture.isRunning) "מחכה לאקורד ברור" else "האקורד שנשמע יופיע כאן"
        } else {
            chordText.text = current.name(sp)
            val numeral = key?.numeralOf(current)
            chordSub.text = current.quality.hebrew + if (numeral != null) "   ·   דרגה $numeral בסולם" else "   ·   מחוץ לסולם"
        }
        historyText.text = tracker.history.takeLast(8).joinToString("  ›  ") { it.name(sp) }
        historyText.visibility = if (tracker.history.isEmpty()) View.GONE else View.VISIBLE

        // Chips
        triadsButton.background = pill(if (!showSevenths) Palette.TEAL else Palette.SURFACE_2)
        triadsButton.setTextColor(if (!showSevenths) Palette.BG else Palette.TEXT)
        seventhsButton.background = pill(if (showSevenths) Palette.TEAL else Palette.SURFACE_2)
        seventhsButton.setTextColor(if (showSevenths) Palette.BG else Palette.TEXT)
        if (key != chipsKey || showSevenths != chipsSevenths || selectedChord != chipsSelected) buildChips(key)

        // Detail: the picked chord, or follow the live chord
        val detail = selectedChord ?: current
        if (detail != shownChord || moreVoicings != shownMore || key != shownSpellingKey) {
            showDetail(detail, key)
        }
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
                setPadding(dp(2), dp(10), dp(2), dp(10))
                text = "${chord.name(key.spelling)}\n$numeral"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                setTextColor(if (isSel) Palette.BG else Palette.TEXT)
                typeface = Typeface.DEFAULT_BOLD
                background = rounded(if (isSel) Palette.AMBER else Palette.SURFACE_2, 12f)
                setOnClickListener {
                    if (selectedChord == chord) {
                        selectedChord = null
                    } else {
                        selectedChord = chord
                        selectedSpelling = null
                    }
                    refresh()
                }
            }
            row!!.addView(chip, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply {
                marginStart = dp(4); marginEnd = dp(4)
            })
        }
        val remainder = items.size % 4
        if (remainder != 0) repeat(4 - remainder) {
            row!!.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f).apply { marginStart = dp(4); marginEnd = dp(4) })
        }
    }

    private fun showDetail(chord: Chord?, key: Key?) {
        shownChord = chord
        shownMore = moreVoicings
        shownSpellingKey = key
        guitarGrid.removeAllViews()
        if (chord == null) {
            detailTitle.text = "בחר אקורד"
            detailSub.text = "חפש אקורד למעלה, הקש על אקורד ברשימה, או השמע מוזיקה והאקורד שנשמע יוצג כאן"
            detailPiano.chordNotes = emptyList()
            detailPiano.visibility = View.GONE
            guitarLabel.visibility = View.GONE
            moreButton.visibility = View.GONE
            return
        }
        val sp = if (chord == selectedChord && selectedSpelling != null) selectedSpelling!!
                 else key?.spelling ?: Spelling.NEUTRAL
        detailPiano.visibility = View.VISIBLE
        guitarLabel.visibility = View.VISIBLE
        detailTitle.text = chord.name(sp)
        val notes = chord.pitchClasses().joinToString("  ") { sp.name(it) }
        val numeral = key?.numeralOf(chord)
        val follow = if (selectedChord == null) "   ·   עוקב אחרי ההאזנה" else ""
        detailSub.text = "${chord.quality.hebrew}   ·   $notes" +
            (if (numeral != null) "   ·   $numeral" else "") + follow

        detailPiano.spelling = sp
        detailPiano.rootPc = chord.root
        detailPiano.chordNotes = chord.quality.intervals.map { chord.root + it }.map { if (it > 23) it - 12 else it }

        val all = GuitarChords.voicings(chord, 6)
        val shown = if (moreVoicings) all else all.take(3)
        var row: LinearLayout? = null
        shown.forEachIndexed { i, v ->
            if (i % 3 == 0) {
                row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutDirection = View.LAYOUT_DIRECTION_LTR
                }
                guitarGrid.addView(row, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            }
            val g = GuitarView(this).apply {
                spelling = sp
                rootPc = chord.root
                voicing = v
            }
            row!!.addView(g, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        }
        val rem = shown.size % 3
        if (rem != 0) repeat(3 - rem) { row!!.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f)) }
        moreButton.visibility = if (all.size > 3) View.VISIBLE else View.GONE
        moreButton.text = if (moreVoicings) "פחות אצבועים" else "עוד אצבועים"
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

        // Stability selector
        val stabRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        stabRow.addView(text("החלפת אקורד:", 13f, Palette.MUTED), LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginEnd = dp(8) })
        for (s in Stability.values()) {
            val b = smallButton(s.hebrew) { setStability(s) }
            stabilityButtons[s] = b
            stabRow.addView(b, LinearLayout.LayoutParams(0, dp(36), 1f).apply { marginStart = dp(3); marginEnd = dp(3) })
        }
        root.addView(stabRow, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(10) })
        setStability(tracker.stability)

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
        quarterNote = text("↓ = התו נמוך ברבע טון. בגיטרה ובפסנתר מנגנים את התו הקרוב, והאקורדים בנויים לפיו.", 13f, Palette.MUTED)
        keyPanel.addView(quarterNote, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(4) })
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

        // Chord search + detail
        val detail = panel()
        detail.addView(text("חיפוש אקורד", 13f, Palette.MUTED))
        val searchRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        searchInput = EditText(this).apply {
            hint = "למשל Am7 או F#"
            setHintTextColor(Palette.MUTED)
            setTextColor(Palette.TEXT)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            textDirection = View.TEXT_DIRECTION_LTR
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            setSingleLine(true)
            background = rounded(Palette.SURFACE_2, 12f)
            setPadding(dp(14), dp(10), dp(14), dp(10))
            setOnEditorActionListener { _, actionId, event ->
                if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                    (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)) {
                    searchChord(); true
                } else false
            }
        }
        searchRow.addView(searchInput, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(8) })
        val go = smallButton("הצג") { searchChord() }.apply {
            background = pill(Palette.AMBER)
            setTextColor(Palette.BG)
            typeface = Typeface.DEFAULT_BOLD
        }
        searchRow.addView(go, LinearLayout.LayoutParams(dp(80), dp(48)))
        detail.addView(searchRow, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(8) })
        searchError = text("", 13f, Palette.AMBER).apply { visibility = View.GONE }
        detail.addView(searchError, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(6) })

        detailTitle = text("", 34f, Palette.TEXT).apply {
            typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
            textDirection = View.TEXT_DIRECTION_LTR
            gravity = Gravity.START
        }
        detail.addView(detailTitle, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(14) })
        detailSub = text("", 14f, Palette.MUTED)
        detail.addView(detailSub)
        detailPiano = PianoView(this)
        detail.addView(detailPiano, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(14) })
        guitarLabel = text("גיטרה", 13f, Palette.MUTED)
        detail.addView(guitarLabel, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(16) })
        guitarGrid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        detail.addView(guitarGrid, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(6) })
        moreButton = smallButton("עוד אצבועים") { moreVoicings = !moreVoicings; refresh() }
        detail.addView(moreButton, LinearLayout.LayoutParams(MATCH_PARENT, dp(40)).apply { topMargin = dp(8) })
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
        setPadding(dp(4), 0, dp(4), 0)
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
