package com.futureclock.app.ui.views

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.View
import com.google.android.material.color.MaterialColors
import java.util.Calendar
import java.util.TimeZone

/** A self-ticking analog clock View that re-renders every animation frame. */
class AnalogClockView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var timeZone: TimeZone = TimeZone.getDefault()
    var timeOffsetMillis: Long = 0L
    var showSeconds: Boolean = true
    var faceColor: Int = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurface, 0xFF151D2B.toInt())
    var strokeColor: Int = MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimary, 0xFF8EAAFF.toInt())
    var hourHandColor: Int = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSecondary, 0xFFFF8B7B.toInt())
    var minuteHandColor: Int = MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimary, 0xFF8EAAFF.toInt())
    var secondHandColor: Int = MaterialColors.getColor(this, com.google.android.material.R.attr.colorTertiary, 0xFF67D5C4.toInt())
    var tickColor: Int = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant, 0xFFC3CBD8.toInt())

    private val now = Calendar.getInstance()

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        post(redrawRunnable)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(redrawRunnable)
        super.onDetachedFromWindow()
    }

    private val redrawRunnable = object : Runnable {
        override fun run() {
            invalidate()
            postDelayed(this, 33L)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        now.timeInMillis = System.currentTimeMillis() + timeOffsetMillis
        now.timeZone = timeZone
        ClockRenderer.draw(
            canvas = canvas,
            w = width,
            h = height,
            time = now,
            faceColor = faceColor,
            strokeColor = strokeColor,
            hourHandColor = hourHandColor,
            minuteHandColor = minuteHandColor,
            secondHandColor = secondHandColor,
            tickColor = tickColor,
            drawSeconds = showSeconds
        )
    }
}
