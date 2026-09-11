package com.hackathon.interior.remove

import android.graphics.Bitmap
import com.google.android.filament.TextureSampler

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
 * SceneView 의 `imageTextureMaterial` 이 셰이더에서 다시 `rgb *= α` 를 하므로 페이드
 * 구간이 `rgb * α²` 로 이중으로 죽어 어두운 띠가 생겼다. 지금은 픽셀을 직접 순회하며
 * **RGB 는 그대로 두고 alpha 만** 램프로 낮추고 `isPremultiplied = false` 로 내보낸다.
 *
 * **라이브 화면의 얇은 밝은 테두리 방지 — [crispSampler] 를 함께 써야 한다.**
 * SceneView 기본 `TextureSampler2D` 는 `WrapMode.REPEAT` + `LINEAR_MIPMAP_LINEAR` 라,
 * quad 가장자리(UV 0/1)에서 반대쪽 가장자리 텍셀이 wrap-블렌딩되고 밉맵이 페이드 림을
 * 번지게 해 라이브 화면에서만 얇은 밝은 선이 보인다(정지 프리뷰는 일반 ImageView 라
 * 깨끗). 이 오브젝트가 만든 텍스처는 [crispSampler] (CLAMP_TO_EDGE, 밉맵 미사용)로
 * 렌더해야 한다.
 */
object EdgeFade {

    /** [hardRimFrac] 기본값 — 페이드 폭 중 최외곽 이 비율은 완전 투명(색 무관). */
    private const val DEFAULT_HARD_RIM_FRAC = 0.18f

    /**
     * 네 가장자리 alpha 를 안쪽으로 램프해 투명하게 페이드아웃한다.
     *
     * @param featherFrac 짧은 변 대비 페이드 폭 비율. 이 폭 안에서 바깥→안 방향으로
     *   alpha 0 → 원본 으로 올라간다.
     * @param hardRimFrac 페이드 폭 중 **최외곽 이 비율은 alpha 0 으로 강제**한다(선형 램프는
     *   그 안쪽부터). 가장자리 픽셀 색이 밝든(흰 테두리) 어떻든 완전히 사라지게 하고,
     *   CLAMP_TO_EDGE 샘플링이 물어오는 최외곽 텍셀도 항상 투명이 되게 한다.
     */
    fun feather(
        src: Bitmap,
        featherFrac: Float = 0.10f,
        hardRimFrac: Float = DEFAULT_HARD_RIM_FRAC,
    ): Bitmap {
        val w = src.width
        val h = src.height
        if (w < 8 || h < 8) return src

        val f = (minOf(w, h) * featherFrac).toInt().coerceIn(8, minOf(w, h) / 2)
        if (f <= 0) return src
        val hardRim = (f * hardRimFrac).toInt().coerceIn(1, f - 1)

        // getPixels 는 항상 non-premultiplied ARGB 를 준다.
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)

        for (y in 0 until h) {
            val ry = edgeRamp(y, h, f, hardRim)
            val rowBase = y * w
            for (x in 0 until w) {
                val k = minOf(ry, edgeRamp(x, w, f, hardRim))
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

    /**
     * [feather] 로 만든 텍스처를 라이브 quad 에 붙일 때 쓰는 샘플러 —
     * CLAMP_TO_EDGE + 밉맵 미사용. 가장자리 wrap-블렌딩/밉맵 블리딩(얇은 밝은 선)을 막는다.
     */
    fun crispSampler(): TextureSampler = TextureSampler(
        TextureSampler.MinFilter.LINEAR,
        TextureSampler.MagFilter.LINEAR,
        TextureSampler.WrapMode.CLAMP_TO_EDGE,
    )

    /**
     * 가장 가까운 가장자리에서: [hardRim]px 까지 0, 거기서 [f]px 까지 0→1 선형 램프,
     * 그 안쪽은 1.
     */
    private fun edgeRamp(i: Int, size: Int, f: Int, hardRim: Int): Float {
        val d = minOf(i, size - 1 - i)
        if (d <= hardRim) return 0f
        return ((d - hardRim).toFloat() / (f - hardRim)).coerceIn(0f, 1f)
    }
}
