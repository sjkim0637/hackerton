package com.hackathon.interior.remove

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.max
import kotlin.math.min

/**
 * "TV 선택 모드"에서 화면을 드래그해 제거할 사물의 사각형(bounding box)을 그린다.
 *
 * - [isSelecting] 이 false 면 터치를 소비하지 않는다(평소 탭/드래그 그대로).
 * - 드래그를 마치면 [onRectFinalized] 로 뷰 픽셀 좌표의 [RectF] 를 넘긴다.
 * - 확정된 사각형은 모드를 꺼도 [clear] 전까지 계속 표시한다(선택 확인용).
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

    private val live = RectF()
    private val locked = RectF()
    private var hasLocked = false
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

    fun clear() {
        hasLocked = false
        locked.setEmpty()
        live.setEmpty()
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
                    if (live.width() > 24f && live.height() > 24f) {
                        locked.set(live)
                        hasLocked = true
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
        val rect = when {
            !live.isEmpty -> live
            hasLocked -> locked
            else -> return
        }
        canvas.drawRect(rect, fillPaint)
        canvas.drawRect(rect, strokePaint)
        canvas.drawText("제거할 사물 영역", rect.left, max(rect.top - 12f, 34f), labelPaint)
    }
}
