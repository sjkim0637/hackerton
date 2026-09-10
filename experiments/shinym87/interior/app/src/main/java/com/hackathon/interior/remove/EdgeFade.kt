package com.hackathon.interior.remove

import android.graphics.Bitmap

/**
 * 결과/사물 이미지의 네 가장자리를 투명하게 페이드아웃한다.
 *
 * 결과 quad 가 카메라 화면 위에 사각형으로 딱 잘려 얹히면 경계가 각지게 보이므로,
 * 이미지 가장자리 몇 픽셀의 alpha 를 0 으로 그라데이션 처리해 실제 화면과 자연스럽게
 * 섞이도록 한다.
 *
 * **어두운 경계선 방지 — straight alpha 로 출력한다.**
 * 예전 구현은 `Canvas` + `PorterDuff.DST_IN` 으로 alpha 를 깎았는데, Android 비트맵은
 * 내부적으로 premultiplied 로 저장되므로 페이드 밴드의 RGB 까지 `rgb * α` 로 어두워졌다.
 * 게다가 SceneView 의 `imageTextureMaterial` 은 straight-alpha 텍스처를 받아 셰이더에서
 * 다시 `rgb *= α` 를 한다 → 페이드 구간이 `rgb * α²` 로 이중으로 어두워져 가장자리에
 * 어두운 띠(그림자선)가 생겼다.
 *
 * 지금은 픽셀을 직접 순회하며 **RGB 는 그대로 두고 alpha 만** 램프로 낮춘 뒤,
 * `isPremultiplied = false` 인 비트맵으로 내보낸다. straight-alpha 텍스처를 straight-alpha
 * 로 블렌딩하므로 색이 검게 죽지 않는다. 원본 이미지 범위 밖(검은 패딩 등)은 애초에
 * 넣지 않으므로(호출부 crop 이 경계를 클램프함) 페이드가 실제 픽셀 위에서만 일어난다.
 */
object EdgeFade {

    /** [featherFrac] = 짧은 변 대비 페이드 폭 비율. */
    fun feather(src: Bitmap, featherFrac: Float = 0.08f): Bitmap {
        val w = src.width
        val h = src.height
        if (w < 8 || h < 8) return src

        val f = (minOf(w, h) * featherFrac).toInt().coerceIn(6, minOf(w, h) / 3)
        if (f <= 0) return src

        // getPixels 는 항상 non-premultiplied ARGB 를 준다.
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)

        for (y in 0 until h) {
            val ry = edgeRamp(y, h, f)
            val rowBase = y * w
            for (x in 0 until w) {
                val k = minOf(ry, edgeRamp(x, w, f))
                if (k >= 1f) continue
                val i = rowBase + x
                val c = pixels[i]
                val a = (c ushr 24) and 0xFF
                if (a == 0) continue
                val na = (a * k + 0.5f).toInt().coerceIn(0, 255)
                // RGB(하위 24비트)는 건드리지 않는다 — 어두운 경계선 방지.
                pixels[i] = (na shl 24) or (c and 0x00FFFFFF)
            }
        }

        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setHasAlpha(true)
        // setPixels / GPU 업로드가 premultiply 하지 않도록 alpha 타입을 straight 로 둔다.
        runCatching { out.isPremultiplied = false }
        out.setPixels(pixels, 0, w, 0, 0, w, h)
        return out
    }

    /** 가장 가까운 가장자리에서 [f]px 안쪽까지 0→1 선형 램프, 그 안쪽은 1. */
    private fun edgeRamp(i: Int, size: Int, f: Int): Float {
        val d = minOf(i, size - 1 - i)
        return (d.toFloat() / f).coerceIn(0f, 1f)
    }
}
