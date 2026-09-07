package com.hackathon.interior.remove

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * "TV 선택 모드"에서 화면 제스처로 제거할 사물 영역을 지정한다.
 *
 * 두 가지 입력을 모두 받는다:
 * - **드래그** (이동 거리 > [TAP_SLOP_PX]): 기존처럼 사각형(bounding box)을 그려
 *   [onRectFinalized] 로 뷰 픽셀 좌표의 [RectF] 를 넘긴다.
 * - **탭/누르고 있기** (거의 안 움직이고 뗌, 길게 눌러도 됨): D5(MobileSAM 점 프롬프트)용으로
 *   [onPointSelected] 에 탭 지점의 뷰 픽셀 좌표를 넘긴다. 사각형을 정확히 그릴 필요가 없어
 *   기존 드래그보다 선택이 훨씬 쉽다.
 *
 * - [isSelecting] 이 false 면 터치를 소비하지 않는다(평소 탭/드래그 그대로).
 * - 확정된 사각형/점은 모드를 꺼도 [clear] 전까지 계속 표시한다(선택 확인용).
 */
class BboxSelectionView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    var isSelecting: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    var onRectFinalized: ((RectF) -> Unit)? = null

    /** 탭/누르고 있기로 사물을 지정했을 때 뷰 픽셀 좌표의 지점을 넘긴다 (D5: MobileSAM 점 프롬프트). */
    var onPointSelected: ((PointF) -> Unit)? = null

    /** 확정된 사각형/점 근처에 보여줄 측정 문구. 여러 줄(`\n`) 허용. null 이면 안 그림. */
    var measurementText: String? = null
        set(value) {
            field = value
            invalidate()
        }

    private val live = RectF()
    private val locked = RectF()
    private var hasLocked = false
    private var lockedPoint: PointF? = null
    private var dragging = false
    private var startX = 0f
    private var startY = 0f

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(51, 77, 208, 225)
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
        color = Color.rgb(77, 208, 225)
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 34f
        setShadowLayer(6f, 0f, 0f, Color.BLACK)
    }
    private val measurePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(200, 245, 250)
        textSize = 32f
        setShadowLayer(6f, 0f, 0f, Color.BLACK)
    }
    private val pointFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(140, 77, 208, 225)
    }
    private val pointStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 5f
        color = Color.rgb(77, 208, 225)
    }

    fun clear() {
        hasLocked = false
        locked.setEmpty()
        live.setEmpty()
        lockedPoint = null
        measurementText = null
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isSelecting) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startX = event.x
                startY = event.y
                dragging = true
                live.set(startX, startY, startX, startY)
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> if (dragging) {
                live.set(
                    min(startX, event.x), min(startY, event.y),
                    max(startX, event.x), max(startY, event.y),
                )
                invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragging) {
                    dragging = false
                    val moved = max(abs(event.x - startX), abs(event.y - startY))
                    if (event.actionMasked == MotionEvent.ACTION_UP && moved <= TAP_SLOP_PX) {
                        // 거의 안 움직이고 뗌 = 탭/누르고 있기 → 점 하나로 지정 (D5).
                        hasLocked = false
                        locked.setEmpty()
                        val point = PointF(startX, startY)
                        lockedPoint = point
                        onPointSelected?.invoke(PointF(point.x, point.y))
                    } else if (live.width() > 24f && live.height() > 24f) {
                        locked.set(live)
                        hasLocked = true
                        lockedPoint = null
                        onRectFinalized?.invoke(RectF(locked))
                    }
                    live.setEmpty()
                    invalidate()
                }
            }
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        val point = lockedPoint
        if (live.isEmpty && !hasLocked && point != null) {
            canvas.drawCircle(point.x, point.y, POINT_RADIUS_PX, pointFillPaint)
            canvas.drawCircle(point.x, point.y, POINT_RADIUS_PX, pointStrokePaint)
            canvas.drawText(
                "제거할 사물 지점", point.x, max(point.y - POINT_RADIUS_PX - 12f, 34f), labelPaint,
            )
            drawMeasurementText(
                canvas, x = point.x,
                top = point.y - POINT_RADIUS_PX, bottom = point.y + POINT_RADIUS_PX,
            )
            return
        }

        val rect = when {
            !live.isEmpty -> live
            hasLocked -> locked
            else -> return
        }
        canvas.drawRect(rect, fillPaint)
        canvas.drawRect(rect, strokePaint)
        canvas.drawText("제거할 사물 영역", rect.left, max(rect.top - 12f, 34f), labelPaint)

        // 확정된 사각형 아래(공간이 없으면 위)에 측정 문구.
        if (live.isEmpty && hasLocked) {
            drawMeasurementText(canvas, x = rect.left, top = rect.top, bottom = rect.bottom)
        }
    }

    /** [top]/[bottom] 아래쪽에 여백이 없으면 위쪽에, [x] 기준 왼쪽 정렬로 [measurementText] 를 그린다. */
    private fun drawMeasurementText(canvas: Canvas, x: Float, top: Float, bottom: Float) {
        val text = measurementText ?: return
        if (text.isEmpty()) return
        val lines = text.split("\n")
        val lineGap = 40f
        var y = bottom + lineGap
        if (y + (lines.size - 1) * lineGap > height - 8f) {
            y = top - 12f - lines.size * lineGap
        }
        y = max(y, 34f)
        for (line in lines) {
            canvas.drawText(line, x, y, measurePaint)
            y += lineGap
        }
    }

    private companion object {
        /** 이보다 적게 움직이고 손을 떼면 드래그가 아니라 탭/누르고 있기로 본다. */
        const val TAP_SLOP_PX = 20f
        const val POINT_RADIUS_PX = 28f
    }
}
