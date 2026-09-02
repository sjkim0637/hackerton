package com.hackathon.interior.furniture

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface

/** 가구 이름을 둥근 사각형 배경의 텍스트 비트맵으로 그린다. (이름표 빌보드용) */
object LabelRenderer {

    fun make(text: String): Bitmap {
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.DEFAULT_BOLD
            textSize = 72f
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
        }
        val padX = 40f
        val padY = 28f
        val fm = textPaint.fontMetrics
        val textWidth = textPaint.measureText(text)
        val width = (textWidth + padX * 2).toInt().coerceAtLeast(8)
        val height = (fm.bottom - fm.top + padY * 2).toInt().coerceAtLeast(8)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(190, 0, 0, 0) }
        canvas.drawRoundRect(
            RectF(0f, 0f, width.toFloat(), height.toFloat()), 24f, 24f, bgPaint,
        )
        canvas.drawText(text, width / 2f, height / 2f - (fm.ascent + fm.descent) / 2f, textPaint)
        return bitmap
    }
}
