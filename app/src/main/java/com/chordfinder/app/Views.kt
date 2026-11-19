package com.chordfinder.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View

object Palette {
    val BG = Color.parseColor("#14213D")
    val SURFACE = Color.parseColor("#1D2D52")
    val SURFACE_2 = Color.parseColor("#26396A")
    val TEXT = Color.parseColor("#EDEFF5")
    val MUTED = Color.parseColor("#9AA6C4")
    val AMBER = Color.parseColor("#F2A541")
    val TEAL = Color.parseColor("#4CC9B0")
    val IVORY = Color.parseColor("#F6F3EA")
    val EBONY = Color.parseColor("#1A1A22")
}

private fun Context.dp(v: Float) = v * resources.displayMetrics.density

/** Two-octave keyboard (C to B). */
class PianoView(context: Context) : View(context) {
    /** Absolute semitones 0..23 to mark as chord tones. */
    var chordNotes: List<Int> = emptyList()
        set(v) { field = v; invalidate() }
    var rootPc: Int? = null
        set(v) { field = v; invalidate() }
    /** Pitch classes to tint softly (scale tones). */
    var scalePcs: Set<Int> = emptySet()
        set(v) { field = v; invalidate() }
    var spelling: Spelling = Spelling.NEUTRAL
        set(v) { field = v; invalidate() }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    private val whiteSteps = intArrayOf(0, 2, 4, 5, 7, 9, 11)
    private val blackAfterWhite = mapOf(0 to 1, 1 to 3, 3 to 6, 4 to 8, 5 to 10)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(w, (w * 0.34f).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val kw = w / 14f
        val r = context.dp(3f)
        text.textSize = kw * 0.38f

        for (i in 0 until 14) {
            val semi = (i / 7) * 12 + whiteSteps[i % 7]
            val rect = RectF(i * kw + 1, 0f, (i + 1) * kw - 1, h)
            paint.color = fillFor(semi, Palette.IVORY, true)
            canvas.drawRoundRect(rect, r, r, paint)
            label(canvas, semi, i * kw + kw / 2, h - kw * 0.35f, true)
        }
        val bw = kw * 0.62f
        val bh = h * 0.6f
        for (i in 0 until 14) {
            val step = blackAfterWhite[i % 7] ?: continue
            val semi = (i / 7) * 12 + step
            val cx = (i + 1) * kw
            val rect = RectF(cx - bw / 2, 0f, cx + bw / 2, bh)
            paint.color = fillFor(semi, Palette.EBONY, false)
            canvas.drawRoundRect(rect, r, r, paint)
            label(canvas, semi, cx, bh - bw * 0.35f, false)
        }
    }

    private fun fillFor(semi: Int, base: Int, white: Boolean): Int {
        val pc = semi % 12
        return when {
            semi in chordNotes && pc == rootPc -> Palette.AMBER
            semi in chordNotes -> Palette.TEAL
            pc in scalePcs -> if (white) Color.parseColor("#BFE9DF") else Color.parseColor("#2E7F70")
            else -> base
        }
    }

    private fun label(canvas: Canvas, semi: Int, x: Float, y: Float, white: Boolean) {
        val pc = semi % 12
        val marked = semi in chordNotes || (chordNotes.isEmpty() && pc in scalePcs)
        if (!marked) return
        val old = text.textSize
        if (!white) text.textSize = old * 0.8f
        text.color = if (semi in chordNotes || white) Palette.EBONY else Palette.TEXT
        canvas.drawText(spelling.name(pc), x, y, text)
        text.textSize = old
    }
}

/** Standard chord box: low E on the left, five frets. */
class GuitarView(context: Context) : View(context) {
    var voicing: GuitarVoicing? = null
        set(v) { field = v; invalidate() }
    var rootPc: Int? = null
        set(v) { field = v; invalidate() }
    var spelling: Spelling = Spelling.NEUTRAL
        set(v) { field = v; invalidate() }

    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Palette.MUTED; style = Paint.Style.STROKE }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; color = Palette.TEXT }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(w, (w * 1.3f).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        val v = voicing ?: return
        val w = width.toFloat()
        val gridW = w * 0.7f
        val left = w * 0.2f
        val sx = gridW / 5f
        val fy = sx * 1.25f
        val gridH = fy * 5f
        val top = fy * 0.9f
        val fretted = v.frets.filter { it > 0 }
        val maxF = fretted.maxOrNull() ?: 0
        val start = if (maxF <= 5) 1 else fretted.min()

        line.strokeWidth = context.dp(1.5f)
        for (s in 0 until 6) canvas.drawLine(left + s * sx, top, left + s * sx, top + gridH, line)
        for (f in 0..5) canvas.drawLine(left, top + f * fy, left + gridW, top + f * fy, line)
        if (start == 1) {
            fill.color = Palette.TEXT
            canvas.drawRect(left - context.dp(1f), top - context.dp(5f), left + gridW + context.dp(1f), top, fill)
        } else {
            text.textSize = maxOf(fy * 0.5f, context.dp(10f))
            text.textAlign = Paint.Align.RIGHT
            text.color = Palette.MUTED
            canvas.drawText("$start", left - context.dp(5f), top + fy * 0.65f, text)
            text.color = Palette.TEXT
            text.textAlign = Paint.Align.CENTER
        }

        val dotR = minOf(sx, fy) * 0.34f
        if (v.barreFret >= start && v.barreFret > 0) {
            val y = top + (v.barreFret - start + 0.5f) * fy
            fill.color = Palette.TEAL
            canvas.drawRoundRect(
                RectF(left + v.barreFrom * sx - dotR, y - dotR * 0.8f, left + v.barreTo * sx + dotR, y + dotR * 0.8f),
                dotR, dotR, fill
            )
        }

        text.textSize = maxOf(fy * 0.42f, context.dp(9f))
        for (s in 0 until 6) {
            val x = left + s * sx
            val f = v.frets[s]
            val pc = if (f >= 0) (GuitarChords.OPEN[s] + f) % 12 else -1
            when {
                f < 0 -> { text.color = Palette.MUTED; canvas.drawText("✕", x, top - fy * 0.25f, text) }
                f == 0 -> {
                    line.color = if (pc == rootPc) Palette.AMBER else Palette.TEXT
                    canvas.drawCircle(x, top - fy * 0.42f, dotR * 0.55f, line)
                    line.color = Palette.MUTED
                }
                else -> {
                    val y = top + (f - start + 0.5f) * fy
                    fill.color = if (pc == rootPc) Palette.AMBER else Palette.TEAL
                    canvas.drawCircle(x, y, dotR, fill)
                }
            }
            if (pc >= 0) {
                text.color = if (pc == rootPc) Palette.AMBER else Palette.MUTED
                canvas.drawText(spelling.name(pc), x, top + gridH + fy * 0.62f, text)
            }
        }
        text.color = Palette.TEXT
    }
}

/** Live bars for the 12 pitch classes plus an input level line. */
class ChromaView(context: Context) : View(context) {
    private var values = DoubleArray(12)
    private var level = 0f
    var spelling: Spelling = Spelling.NEUTRAL
    var scalePcs: Set<Int> = emptySet()

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; color = Palette.MUTED }

    fun update(chroma: DoubleArray, levelDb: Double) {
        values = chroma.copyOf()
        level = ((levelDb + 70) / 60).toFloat().coerceIn(0f, 1f)
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), context.dp(120f).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val labelH = context.dp(20f)
        val barArea = h - labelH - context.dp(8f)
        val bw = w / 12f
        val max = values.maxOrNull()?.takeIf { it > 0 } ?: 1.0
        text.textSize = context.dp(12f)
        for (i in 0 until 12) {
            val bh = (values[i] / max * barArea).toFloat()
            fill.color = if (i in scalePcs) Palette.TEAL else Palette.SURFACE_2
            val x = i * bw
            canvas.drawRoundRect(RectF(x + bw * 0.15f, barArea - bh, x + bw * 0.85f, barArea), 6f, 6f, fill)
            canvas.drawText(spelling.name(i), x + bw / 2, h - context.dp(4f), text)
        }
        fill.color = Palette.SURFACE_2
        canvas.drawRect(0f, barArea + context.dp(3f), w, barArea + context.dp(5f), fill)
        fill.color = Palette.AMBER
        canvas.drawRect(0f, barArea + context.dp(3f), w * level, barArea + context.dp(5f), fill)
    }
}
