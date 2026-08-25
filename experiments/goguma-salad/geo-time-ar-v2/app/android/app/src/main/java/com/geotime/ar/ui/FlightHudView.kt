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
    private val skyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(70, 42, 154, 190)
        style = Paint.Style.FILL
    }
    private val groundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(78, 150, 92, 35)
        style = Paint.Style.FILL
    }

    private var headingDegrees = 0f
    private var pitchDegrees = 0f
    private var rollDegrees = 0f
    private var showRollExitCue = false

    fun setPose(heading: Float, pitch: Float, roll: Float, showRollExitCue: Boolean) {
        headingDegrees = normalizeHeading(heading)
        pitchDegrees = pitch.coerceIn(-45f, 45f)
        rollDegrees = roll.coerceIn(-60f, 60f)
        this.showRollExitCue = showRollExitCue
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
        val centerX = dp(76f)
        val centerY = height - dp(154f)
        val radius = dp(54f)
        val pixelsPerDegree = dp(2.1f)

        canvas.drawCircle(centerX, centerY, radius + dp(4f), backdropPaint)
        val circularClip = Path().apply { addCircle(centerX, centerY, radius, Path.Direction.CW) }

        canvas.save()
        canvas.clipPath(circularClip)
        canvas.translate(centerX, centerY + pitchDegrees * pixelsPerDegree)
        canvas.rotate(-rollDegrees)
        canvas.drawRect(-radius * 2f, -radius * 2f, radius * 2f, 0f, skyPaint)
        canvas.drawRect(-radius * 2f, 0f, radius * 2f, radius * 2f, groundPaint)

        for (angle in -10..10 step 10) {
            val y = -angle * pixelsPerDegree
            val horizon = angle == 0
            val halfWidth = if (horizon) radius * 0.9f else radius * 0.48f
            val centerGap = if (horizon) dp(10f) else dp(8f)
            linePaint.color = if (horizon) Color.WHITE else Color.argb(225, 210, 250, 255)
            linePaint.strokeWidth = if (horizon) dp(2f) else dp(1.2f)
            canvas.drawLine(-halfWidth, y, -centerGap, y, linePaint)
            canvas.drawLine(centerGap, y, halfWidth, y, linePaint)

            if (!horizon) {
                textPaint.color = linePaint.color
                textPaint.textSize = sp(8.5f)
                canvas.drawText(kotlin.math.abs(angle).toString(), halfWidth + dp(9f), y + dp(3f), textPaint)
            }
        }
        canvas.restore()

        linePaint.color = Color.argb(235, 210, 250, 255)
        linePaint.strokeWidth = dp(1.5f)
        canvas.drawCircle(centerX, centerY, radius, linePaint)
        listOf(-30f, -15f, 0f, 15f, 30f).forEach { angle ->
            drawRollTick(canvas, centerX, centerY, radius, angle, highlighted = false)
        }
        if (showRollExitCue) {
            drawRollTick(canvas, centerX, centerY, radius, -8f, highlighted = true)
            drawRollTick(canvas, centerX, centerY, radius, 8f, highlighted = true)
        }

        drawFixedAircraftSymbol(canvas, centerX, centerY)
        textPaint.color = Color.argb(245, 215, 250, 255)
        textPaint.textSize = sp(9.5f)
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText(
            String.format(Locale.US, "P %+.0f°  R %+.0f°", pitchDegrees, rollDegrees),
            centerX,
            centerY + radius + dp(15f),
            textPaint,
        )
        if (showRollExitCue) {
            textPaint.color = amber
            textPaint.textSize = sp(8.5f)
            canvas.drawText("ROLL ±8° EXIT", centerX, centerY + radius + dp(28f), textPaint)
        }
    }

    private fun drawRollTick(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        radius: Float,
        angleDegrees: Float,
        highlighted: Boolean,
    ) {
        val radians = Math.toRadians(angleDegrees.toDouble())
        val innerRadius = radius - dp(if (highlighted) 9f else 6f)
        val outerRadius = radius - dp(1f)
        val sin = kotlin.math.sin(radians).toFloat()
        val cos = kotlin.math.cos(radians).toFloat()
        linePaint.color = if (highlighted) amber else Color.argb(220, 210, 250, 255)
        linePaint.strokeWidth = dp(if (highlighted) 2.5f else 1.3f)
        canvas.drawLine(
            centerX + sin * innerRadius,
            centerY - cos * innerRadius,
            centerX + sin * outerRadius,
            centerY - cos * outerRadius,
            linePaint,
        )
    }

    private fun drawFixedAircraftSymbol(canvas: Canvas, centerX: Float, centerY: Float) {
        linePaint.color = Color.argb(255, 225, 253, 255)
        linePaint.strokeWidth = dp(2f)
        val wing = dp(31f)
        val gap = dp(7f)
        canvas.drawLine(centerX - wing, centerY, centerX - gap, centerY, linePaint)
        canvas.drawLine(centerX + gap, centerY, centerX + wing, centerY, linePaint)
        canvas.drawLine(centerX - wing, centerY, centerX - wing, centerY + dp(5f), linePaint)
        canvas.drawLine(centerX + wing, centerY, centerX + wing, centerY + dp(5f), linePaint)
        canvas.drawCircle(centerX, centerY, dp(2.5f), pointerPaint)
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
