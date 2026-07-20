package com.futureclock.app.ui.views

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import androidx.core.graphics.ColorUtils
import java.util.Calendar
import java.util.Date
import java.util.TimeZone
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Pure rendering logic for an analog clock face. Used both by [AnalogClockView] in-app
 * and by widget providers to rasterize a Bitmap into an ImageView.
 */
object ClockRenderer {

    fun draw(
        canvas: Canvas,
        w: Int,
        h: Int,
        time: Calendar,
        faceColor: Int = Color.parseColor("#0F0F22"),
        strokeColor: Int = Color.parseColor("#00E5FF"),
        hourHandColor: Int = Color.parseColor("#FF00E5"),
        minuteHandColor: Int = Color.parseColor("#00E5FF"),
        secondHandColor: Int = Color.parseColor("#B6FF00"),
        tickColor: Int = Color.parseColor("#9090C0"),
        drawSeconds: Boolean = true
    ) {
        if (w <= 0 || h <= 0) return
        val cx = w / 2f
        val cy = h / 2f
        val r = min(w, h) / 2f

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = faceColor
            style = Paint.Style.FILL
        }
        canvas.drawCircle(cx, cy, r * 0.98f, bgPaint)

        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = strokeColor
            style = Paint.Style.STROKE
            strokeWidth = r * 0.04f
        }
        canvas.drawCircle(cx, cy, r * 0.94f, strokePaint)

        // Faint inner ring
        val innerRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ColorUtils.setAlphaComponent(strokeColor, 60)
            style = Paint.Style.STROKE
            strokeWidth = r * 0.012f
        }
        canvas.drawCircle(cx, cy, r * 0.55f, innerRingPaint)

        // Ticks
        val majorTickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = tickColor
            style = Paint.Style.STROKE
            strokeWidth = r * 0.025f
            strokeCap = Paint.Cap.ROUND
        }
        val minorTickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ColorUtils.setAlphaComponent(tickColor, 120)
            style = Paint.Style.STROKE
            strokeWidth = r * 0.012f
            strokeCap = Paint.Cap.ROUND
        }
        for (i in 0 until 60) {
            val angle = (i * 6 - 90) * Math.PI / 180
            val isMajor = i % 5 == 0
            val outer = r * 0.88f
            val inner = if (isMajor) r * 0.78f else r * 0.83f
            val sx = cx + outer * cos(angle).toFloat()
            val sy = cy + outer * sin(angle).toFloat()
            val ex = cx + inner * cos(angle).toFloat()
            val ey = cy + inner * sin(angle).toFloat()
            canvas.drawLine(sx, sy, ex, ey, if (isMajor) majorTickPaint else minorTickPaint)
        }

        // Hour numbers (12, 3, 6, 9)
        val numberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = tickColor
            textSize = r * 0.18f
            textAlign = Paint.Align.CENTER
            isFakeBoldText = false
        }
        val numberOffset = r * 0.66f
        canvas.drawText("12", cx, cy - numberOffset + numberPaint.textSize / 3f, numberPaint)
        canvas.drawText("3", cx + numberOffset, cy + numberPaint.textSize / 3f, numberPaint)
        canvas.drawText("6", cx, cy + numberOffset + numberPaint.textSize / 3f, numberPaint)
        canvas.drawText("9", cx - numberOffset, cy + numberPaint.textSize / 3f, numberPaint)

        // Hands
        val ms = time.timeInMillis
        val hour = time.get(Calendar.HOUR_OF_DAY) % 12
        val minute = time.get(Calendar.MINUTE)
        val second = time.get(Calendar.SECOND)
        val milli = time.get(Calendar.MILLISECOND)

        val hourAngle = ((hour + minute / 60f) * 30f - 90f) * Math.PI / 180
        val minuteAngle = ((minute + second / 60f) * 6f - 90f) * Math.PI / 180
        val secondAngle = ((second + milli / 1000f) * 6f - 90f) * Math.PI / 180

        // Hour hand
        val hourPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = hourHandColor
            style = Paint.Style.STROKE
            strokeWidth = r * 0.07f
            strokeCap = Paint.Cap.ROUND
        }
        val hourLen = r * 0.50f
        canvas.drawLine(
            cx, cy,
            cx + hourLen * cos(hourAngle).toFloat(),
            cy + hourLen * sin(hourAngle).toFloat(),
            hourPaint
        )

        // Minute hand
        val minutePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = minuteHandColor
            style = Paint.Style.STROKE
            strokeWidth = r * 0.05f
            strokeCap = Paint.Cap.ROUND
        }
        val minuteLen = r * 0.72f
        canvas.drawLine(
            cx, cy,
            cx + minuteLen * cos(minuteAngle).toFloat(),
            cy + minuteLen * sin(minuteAngle).toFloat(),
            minutePaint
        )

        // Second hand (with glow)
        if (drawSeconds) {
            val secondGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = secondHandColor
                style = Paint.Style.STROKE
                strokeWidth = r * 0.025f
                strokeCap = Paint.Cap.ROUND
                maskFilter = BlurMaskFilter(r * 0.05f, BlurMaskFilter.Blur.NORMAL)
                alpha = 200
            }
            val secondPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = secondHandColor
                style = Paint.Style.STROKE
                strokeWidth = r * 0.018f
                strokeCap = Paint.Cap.ROUND
            }
            val secondLen = r * 0.82f
            canvas.drawLine(
                cx, cy,
                cx + secondLen * cos(secondAngle).toFloat(),
                cy + secondLen * sin(secondAngle).toFloat(),
                secondGlowPaint
            )
            canvas.drawLine(
                cx, cy,
                cx + secondLen * cos(secondAngle).toFloat(),
                cy + secondLen * sin(secondAngle).toFloat(),
                secondPaint
            )
        }

        // Center cap
        val centerOuter = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = bgSurface()
            style = Paint.Style.FILL
        }
        canvas.drawCircle(cx, cy, r * 0.06f, centerOuter)
        val centerInner = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = secondHandColor
            style = Paint.Style.FILL
        }
        canvas.drawCircle(cx, cy, r * 0.035f, centerInner)
    }

    private fun bgSurface() = Color.parseColor("#0A0A1A")

    fun renderBitmap(
        widthPx: Int,
        heightPx: Int,
        time: Calendar,
        faceColor: Int = Color.parseColor("#0F0F22"),
        strokeColor: Int = Color.parseColor("#00E5FF")
    ): Bitmap {
        val bmp = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        draw(canvas, widthPx, heightPx, time, faceColor, strokeColor)
        return bmp
    }
}
