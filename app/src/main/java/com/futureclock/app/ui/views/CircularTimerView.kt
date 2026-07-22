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
import com.google.android.material.color.MaterialColors

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
        color = MaterialColors.getColor(this@CircularTimerView, com.google.android.material.R.attr.colorSurfaceVariant, Color.DKGRAY)
        style = Paint.Style.STROKE
        strokeWidth = 10f * resources.displayMetrics.density
        strokeCap = Paint.Cap.ROUND
    }
    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = MaterialColors.getColor(this@CircularTimerView, com.google.android.material.R.attr.colorPrimary, Color.BLUE)
        style = Paint.Style.STROKE
        strokeWidth = 10f * resources.displayMetrics.density
        strokeCap = Paint.Cap.ROUND
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = MaterialColors.getColor(this@CircularTimerView, com.google.android.material.R.attr.colorOnSurface, Color.WHITE)
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        textSize = 42f * resources.displayMetrics.scaledDensity
    }
    private val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = MaterialColors.getColor(this@CircularTimerView, com.google.android.material.R.attr.colorOnSurfaceVariant, Color.LTGRAY)
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        textSize = 13f * resources.displayMetrics.scaledDensity
    }

    private val arcRect = RectF()

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
        // The progress shift is semantic: primary while active, secondary near completion.
        progressPaint.color = blendColors(
            MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimary, Color.BLUE),
            MaterialColors.getColor(this, com.google.android.material.R.attr.colorSecondary, Color.RED),
            ratio
        )
        canvas.drawArc(arcRect, -90f, sweep, false, progressPaint)

        // Time label
        val text = CountdownFormat.format(remainingMs)
        val fm = labelPaint.fontMetrics
        val textY = cy - (fm.ascent + fm.descent) / 2f
        canvas.drawText(text, cx, textY, labelPaint)

        val sub = if (remainingMs <= 0) "DONE" else "${(ratio * 100).toInt()}%"
        canvas.drawText(sub, cx, textY + 28f * resources.displayMetrics.density, subPaint)
    }

    private fun blendColors(c1: Int, c2: Int, t: Float): Int =
        ColorUtils.blendARGB(c1, c2, t)
}
