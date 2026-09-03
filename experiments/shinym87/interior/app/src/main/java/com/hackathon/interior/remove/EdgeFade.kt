package com.hackathon.interior.remove

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader

/**
 * 결과 이미지의 네 가장자리를 투명하게 페이드아웃한다.
 *
 * 결과 quad 가 카메라 화면 위에 사각형으로 딱 잘려 얹히면 경계가 각지게 보이므로,
 * 이미지 자체의 가장자리 몇 픽셀 alpha 를 0 으로 그라데이션 처리해 실제 화면과 자연스럽게
 * 섞이도록 한다.
 */
object EdgeFade {

    /** [featherFrac] = 짧은 변 대비 페이드 폭 비율. */
    fun feather(src: Bitmap, featherFrac: Float = 0.08f): Bitmap {
        val w = src.width
        val h = src.height
        if (w < 8 || h < 8) return src

        val f = (minOf(w, h) * featherFrac).toInt().coerceIn(6, minOf(w, h) / 3).toFloat()
        val out = src.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)
        // DST_IN: 결과 alpha = 원본 alpha * 그라데이션 alpha. 겹치는 모서리는 두 램프가 곱해진다.
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        }
        val fw = w.toFloat()
        val fh = h.toFloat()

        paint.shader = LinearGradient(0f, 0f, 0f, f, Color.TRANSPARENT, Color.BLACK, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, fw, f, paint)                    // 위

        paint.shader = LinearGradient(0f, fh, 0f, fh - f, Color.TRANSPARENT, Color.BLACK, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, fh - f, fw, fh, paint)               // 아래

        paint.shader = LinearGradient(0f, 0f, f, 0f, Color.TRANSPARENT, Color.BLACK, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, f, fh, paint)                    // 왼쪽

        paint.shader = LinearGradient(fw, 0f, fw - f, 0f, Color.TRANSPARENT, Color.BLACK, Shader.TileMode.CLAMP)
        canvas.drawRect(fw - f, 0f, fw, fh, paint)               // 오른쪽

        return out
    }
}
