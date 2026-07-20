package com.futureclock.app.ui.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import com.futureclock.app.util.CountdownFormat
import androidx.core.graphics.ColorUtils

class CircularTimerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var totalMs: Long = 60_000L
        set(v) { field = v.coerceAtLeast(1L); invalidate() }
    var remainingMs: Long = totalMs
        set(v) { field = v.coerceAtLeast(0L); invalidate() }

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1A1A3A")
        style = Paint.Style.STROKE
        strokeWidth = 16f
        strokeCap = Paint.Cap.ROUND
    }
    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00E5FF")
        style = Paint.Style.STROKE
        strokeWidth = 16f
        strokeCap = Paint.Cap.ROUND
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-thin", Typeface.NORMAL)
        textSize = 84f
    }
    private val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#B0B0D0")
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        textSize = 24f
    }

    private val arcRect = RectF()

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val pad = 24f
        val size = minOf(width, height).toFloat() - pad * 2
        val cx = width / 2f
        val cy = height / 2f
        arcRect.set(cx - size / 2, cy - size / 2, cx + size / 2, cy + size / 2)

        canvas.drawArc(arcRect, 0f, 360f, false, trackPaint)

        val ratio = if (totalMs <= 0) 0f else (1f - remainingMs.toFloat() / totalMs.toFloat()).coerceIn(0f, 1f)
        val sweep = 360f * ratio
        // Color shifts from cyan to magenta as time runs out
        progressPaint.color = blendColors(
            Color.parseColor("#00E5FF"),
            Color.parseColor("#FF00E5"),
            ratio
        )
        canvas.drawArc(arcRect, -90f, sweep, false, progressPaint)

        // Time label
        val text = CountdownFormat.format(remainingMs)
        val fm = labelPaint.fontMetrics
        val textY = cy - (fm.ascent + fm.descent) / 2f
        canvas.drawText(text, cx, textY, labelPaint)

        val sub = if (remainingMs <= 0) "DONE" else "${(ratio * 100).toInt()}%"
        canvas.drawText(sub, cx, textY + 50f, subPaint)
    }

    private fun blendColors(c1: Int, c2: Int, t: Float): Int =
        ColorUtils.blendARGB(c1, c2, t)
}
