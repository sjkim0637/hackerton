package com.geotime.ar.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.TypedValue
import android.view.View
import com.geotime.ar.interaction.HeadGestureRecognizer
import java.util.Locale
import kotlin.math.floor
import kotlin.math.roundToInt

class FlightHudView(context: Context) : View(context) {
    private val density = resources.displayMetrics.density
    private val cyan = Color.rgb(190, 244, 255)
    private val amber = Color.rgb(255, 190, 82)
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = cyan
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.SQUARE
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = cyan
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
    }
    private val backdropPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(118, 3, 10, 17)
        style = Paint.Style.FILL
    }
    private val pointerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = cyan
        style = Paint.Style.FILL
    }

    private var headingDegrees = 0f
    private var pitchDegrees = 0f
    private var rollDegrees = 0f
    private var exitBaselinePitch: Float? = null

    fun setPose(heading: Float, pitch: Float, roll: Float, exitBaselinePitch: Float?) {
        headingDegrees = normalizeHeading(heading)
        pitchDegrees = pitch.coerceIn(-45f, 45f)
        rollDegrees = roll.coerceIn(-60f, 60f)
        this.exitBaselinePitch = exitBaselinePitch
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawCompassTape(canvas)
        drawArtificialHorizon(canvas)
    }

    private fun drawCompassTape(canvas: Canvas) {
        val centerX = width / 2f
        val top = dp(112f)
        val left = dp(10f)
        val right = width - dp(10f)
        val tapeBottom = top + dp(72f)
        canvas.drawRoundRect(RectF(left, top, right, tapeBottom), dp(8f), dp(8f), backdropPaint)

        val tapeY = top + dp(30f)
        val pixelsPerDegree = (right - left - dp(24f)) / 90f
        val firstTick = floor((headingDegrees - 50f) / 5f).toInt() * 5
        linePaint.color = Color.argb(225, 190, 244, 255)
        textPaint.color = Color.argb(245, 215, 250, 255)
        textPaint.textSize = sp(15f)
        linePaint.strokeWidth = dp(1.5f)

        for (rawDegrees in firstTick..(firstTick + 100) step 5) {
            val normalized = normalizeHeading(rawDegrees.toFloat())
            val delta = HeadGestureRecognizer.angleDelta(headingDegrees, normalized)
            val x = centerX + delta * pixelsPerDegree
            if (x !in (left + dp(6f))..(right - dp(6f))) continue
            val isLabel = rawDegrees % 15 == 0
            val tickHeight = if (isLabel) dp(10f) else dp(5f)
            canvas.drawLine(x, tapeY, x, tapeY + tickHeight, linePaint)
            if (isLabel) {
                canvas.drawText(headingLabel(rawDegrees), x, tapeY - dp(7f), textPaint)
            }
        }

        val triangle = Path().apply {
            moveTo(centerX, tapeY + dp(12f))
            lineTo(centerX - dp(6f), tapeY + dp(21f))
            lineTo(centerX + dp(6f), tapeY + dp(21f))
            close()
        }
        canvas.drawPath(triangle, pointerPaint)
        textPaint.textSize = sp(16f)
        canvas.drawText(String.format(Locale.US, "%03d°", headingDegrees.roundToInt() % 360), centerX, tapeBottom - dp(8f), textPaint)
    }

    private fun drawArtificialHorizon(canvas: Canvas) {
        val centerX = width * 0.28f
        val centerY = height * 0.73f
        val pixelsPerDegree = dp(3.4f)
        val clipTop = centerY - dp(115f)
        val clipBottom = centerY + dp(115f)

        canvas.save()
        canvas.clipRect(dp(6f), clipTop, width - dp(6f), clipBottom)
        canvas.translate(centerX, centerY + pitchDegrees * pixelsPerDegree)
        canvas.rotate(-rollDegrees)

        for (angle in -25..25 step 5) {
            val y = -angle * pixelsPerDegree
            val major = angle % 10 == 0
            val horizon = angle == 0
            val halfWidth = when {
                horizon -> dp(82f)
                major -> dp(54f)
                else -> dp(36f)
            }
            val centerGap = if (horizon) dp(26f) else dp(21f)
            linePaint.color = when {
                horizon -> Color.argb(255, 210, 252, 255)
                else -> Color.argb(220, 190, 244, 255)
            }
            linePaint.strokeWidth = when {
                horizon -> dp(2.5f)
                major -> dp(1.9f)
                else -> dp(1.4f)
            }
            canvas.drawLine(-halfWidth, y, -centerGap, y, linePaint)
            canvas.drawLine(centerGap, y, halfWidth, y, linePaint)

            if (!horizon) {
                textPaint.color = linePaint.color
                textPaint.textSize = sp(10.5f)
                val label = kotlin.math.abs(angle).toString()
                canvas.drawText(label, -halfWidth - dp(17f), y + dp(3.5f), textPaint)
                canvas.drawText(label, halfWidth + dp(17f), y + dp(3.5f), textPaint)
            }
        }
        exitBaselinePitch?.let { baseline ->
            drawExitLine(canvas, baseline + 15f, pixelsPerDegree)
            drawExitLine(canvas, baseline - 15f, pixelsPerDegree)
        }
        canvas.restore()

        drawFixedAircraftSymbol(canvas, centerX, centerY)
        textPaint.color = Color.argb(245, 215, 250, 255)
        textPaint.textSize = sp(11f)
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText(String.format(Locale.US, "PITCH %+.1f°", pitchDegrees), centerX, centerY + dp(108f), textPaint)
        textPaint.textAlign = Paint.Align.CENTER
    }

    private fun drawExitLine(canvas: Canvas, angle: Float, pixelsPerDegree: Float) {
        val y = -angle * pixelsPerDegree
        val halfWidth = dp(78f)
        val centerGap = dp(25f)
        linePaint.color = Color.argb(245, 255, 190, 82)
        linePaint.strokeWidth = dp(2f)
        canvas.drawLine(-halfWidth, y, -centerGap, y, linePaint)
        canvas.drawLine(centerGap, y, halfWidth, y, linePaint)
        textPaint.color = amber
        textPaint.textSize = sp(11f)
        canvas.drawText("EXIT", -halfWidth - dp(22f), y + dp(3.5f), textPaint)
        canvas.drawText("EXIT", halfWidth + dp(22f), y + dp(3.5f), textPaint)
    }

    private fun drawFixedAircraftSymbol(canvas: Canvas, centerX: Float, centerY: Float) {
        linePaint.color = Color.argb(255, 225, 253, 255)
        linePaint.strokeWidth = dp(2.5f)
        val wing = dp(48f)
        val gap = dp(10f)
        canvas.drawLine(centerX - wing, centerY, centerX - gap, centerY, linePaint)
        canvas.drawLine(centerX + gap, centerY, centerX + wing, centerY, linePaint)
        canvas.drawLine(centerX - wing, centerY, centerX - wing, centerY + dp(7f), linePaint)
        canvas.drawLine(centerX + wing, centerY, centerX + wing, centerY + dp(7f), linePaint)
        canvas.drawCircle(centerX, centerY, dp(3.2f), pointerPaint)
    }

    private fun headingLabel(degrees: Int): String = when (normalizeHeading(degrees.toFloat()).roundToInt() % 360) {
        0 -> "N"
        90 -> "E"
        180 -> "S"
        270 -> "W"
        else -> String.format(Locale.US, "%03d", (normalizeHeading(degrees.toFloat()).roundToInt() % 360))
    }

    private fun normalizeHeading(value: Float): Float = ((value % 360f) + 360f) % 360f

    private fun dp(value: Float): Float = value * density

    private fun sp(value: Float): Float = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_SP,
        value,
        resources.displayMetrics,
    )
}
