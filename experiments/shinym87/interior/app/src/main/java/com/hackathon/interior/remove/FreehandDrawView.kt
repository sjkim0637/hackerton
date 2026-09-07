package com.hackathon.interior.remove

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View

/**
 * D9: 손가락으로 지울 사물의 외곽선을 자유형으로 그리는 오버레이.
 *
 * 탭 한 점 + MobileSAM 자동 인식(D5~D8)은 배경과 사물 색이 비슷한 인테리어 사진에서
 * 자주 엉뚱한 범위를 잡아 사용자가 다루기 어려워했다. 이 뷰는 그 대신 사용자가 직접
 * 그린 폴리곤을 그대로 마스크로 써서(서버 세그멘테이션 없이) 항상 원하는 대로 지울 수
 * 있게 한다. [RemovalController]가 sceneView 제스처 좌표를 그대로 넘겨 그리기만 하고,
 * 실제 마스크 래스터화는 [RemovalController.rasterizeMaskPng] 가 같은 [Path] 로 한다.
 */
class FreehandDrawView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val path = Path()
    private var hasPoints = false

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.rgb(255, 64, 48)
        strokeWidth = 7f
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(90, 255, 64, 48)
    }

    fun begin(x: Float, y: Float) {
        path.reset()
        path.moveTo(x, y)
        hasPoints = true
        invalidate()
    }

    fun extend(x: Float, y: Float) {
        if (!hasPoints) return
        path.lineTo(x, y)
        invalidate()
    }

    fun clearPath() {
        path.reset()
        hasPoints = false
        invalidate()
    }

    /** 지금까지 그린 선을 시작점으로 되돌려 닫은 폴리곤 사본. 원본 [path] 는 그대로 둔다. */
    fun closedPathCopy(): Path = Path(path).apply { close() }

    override fun onDraw(canvas: Canvas) {
        if (!hasPoints) return
        canvas.drawPath(path, fillPaint)
        canvas.drawPath(path, strokePaint)
    }
}
