package com.futureclock.app.ui.views

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.View
import java.util.Calendar
import java.util.TimeZone

/** A self-ticking analog clock View that re-renders every animation frame. */
class AnalogClockView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var timeZone: TimeZone = TimeZone.getDefault()
    var showSeconds: Boolean = true
    var faceColor: Int = 0xFF0F0F22.toInt()
    var strokeColor: Int = 0xFF00E5FF.toInt()
    var hourHandColor: Int = 0xFFFF00E5.toInt()
    var minuteHandColor: Int = 0xFF00E5FF.toInt()
    var secondHandColor: Int = 0xFFB6FF00.toInt()
    var tickColor: Int = 0xFF9090C0.toInt()

    private val now = Calendar.getInstance()

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null) // required for BlurMaskFilter
    }

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
        now.timeInMillis = System.currentTimeMillis()
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
