package com.project.depthplacement.debug

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.project.depthplacement.PointCloudSnapshot

/** Camera image and depth samples share one canvas so pixel alignment can be judged directly. */
class DepthProjectionView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private data class Projection(
        val points: FloatArray,
        val sourceWidth: Int,
        val sourceHeight: Int,
        val minDepth: Float,
        val maxDepth: Float,
    )

    private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val pointPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val messagePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 18f * resources.displayMetrics.density * resources.configuration.fontScale }
    @Volatile private var cameraBitmap: Bitmap? = null
    @Volatile private var projection: Projection? = null
    private var frozen = false
    private var overlayEnabled = true
    private var pointRadiusPx = 3.5f * resources.displayMetrics.density
    private var downX = 0f
    private var downY = 0f
    var onDepthTap: ((uPx: Float, vPx: Float) -> Unit)? = null

    fun submitCamera(bitmap: Bitmap) {
        if (!frozen) { cameraBitmap = bitmap; postInvalidateOnAnimation() }
    }

    fun submit(snapshot: PointCloudSnapshot?) {
        if (frozen || snapshot == null) return
        val points = snapshot.copyImagePoints()
        var minDepth = Float.POSITIVE_INFINITY
        var maxDepth = Float.NEGATIVE_INFINITY
        for (i in 0 until snapshot.pointCount) {
            val depth = points[i * 4 + 2]
            if (depth > 0f) { minDepth = minOf(minDepth, depth); maxDepth = maxOf(maxDepth, depth) }
        }
        if (!minDepth.isFinite()) { minDepth = 0.2f; maxDepth = 5f }
        if (maxDepth - minDepth < 0.1f) maxDepth = minDepth + 0.1f
        projection = Projection(points, snapshot.sourceWidth, snapshot.sourceHeight, minDepth, maxDepth)
        postInvalidateOnAnimation()
    }

    fun setFrozen(value: Boolean) { frozen = value }
    fun isFrozen(): Boolean = frozen
    fun setOverlayEnabled(value: Boolean) { overlayEnabled = value; invalidate() }
    fun isOverlayEnabled(): Boolean = overlayEnabled
    fun setPointSize(value: Float) { pointRadiusPx = value.coerceIn(1f, 12f) * resources.displayMetrics.density / 2f; invalidate() }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.rgb(5, 9, 18))
        val bitmap = cameraBitmap
        val transform = bitmap?.let { centerCropTransform(it.width, it.height) }
        if (bitmap != null && transform != null) canvas.drawBitmap(bitmap, null, transform.rect, imagePaint)
        else canvas.drawText("카메라 영상을 기다리는 중…", 24f, height / 2f, messagePaint)

        val data = projection ?: return
        if (!overlayEnabled) return
        val bitmapWidth = bitmap?.width?.toFloat() ?: width.toFloat()
        val bitmapHeight = bitmap?.height?.toFloat() ?: height.toFloat()
        val scale = transform?.scale ?: 1f
        val left = transform?.rect?.left ?: 0f
        val top = transform?.rect?.top ?: 0f
        val depthRange = data.maxDepth - data.minDepth
        for (index in data.points.indices step 4) {
            val u = data.points[index]
            val v = data.points[index + 1]
            val depth = data.points[index + 2]
            val confidence = data.points[index + 3]
            // The preview is the sensor image rotated 90° clockwise.
            val bitmapX = (1f - v / (data.sourceHeight - 1).coerceAtLeast(1)) * bitmapWidth
            val bitmapY = (u / (data.sourceWidth - 1).coerceAtLeast(1)) * bitmapHeight
            val x = left + bitmapX * scale
            val y = top + bitmapY * scale
            if (x < -pointRadiusPx || x > width + pointRadiusPx || y < -pointRadiusPx || y > height + pointRadiusPx) continue
            val normalized = ((depth - data.minDepth) / depthRange).coerceIn(0f, 1f)
            pointPaint.color = Color.HSVToColor((90 + 130 * confidence).toInt().coerceIn(80, 220), floatArrayOf(240f * normalized, 0.95f, 1f))
            canvas.drawCircle(x, y, pointRadiusPx, pointPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { downX = event.x; downY = event.y; return true }
            MotionEvent.ACTION_UP -> {
                if (kotlin.math.abs(event.x - downX) > 16f || kotlin.math.abs(event.y - downY) > 16f) return true
                val data = projection ?: return true
                val bitmap = cameraBitmap
                val transform = bitmap?.let { centerCropTransform(it.width, it.height) }
                val bitmapWidth = bitmap?.width?.toFloat() ?: width.toFloat()
                val bitmapHeight = bitmap?.height?.toFloat() ?: height.toFloat()
                val scale = transform?.scale ?: 1f
                val left = transform?.rect?.left ?: 0f
                val top = transform?.rect?.top ?: 0f
                val rotatedX = ((event.x - left) / scale / bitmapWidth).coerceIn(0f, 1f)
                val rotatedY = ((event.y - top) / scale / bitmapHeight).coerceIn(0f, 1f)
                onDepthTap?.invoke(rotatedY * data.sourceWidth, (1f - rotatedX) * data.sourceHeight)
                return true
            }
        }
        return true
    }

    private data class CropTransform(val rect: RectF, val scale: Float)

    private fun centerCropTransform(bitmapWidth: Int, bitmapHeight: Int): CropTransform {
        val scale = maxOf(width.toFloat() / bitmapWidth, height.toFloat() / bitmapHeight)
        val drawnWidth = bitmapWidth * scale
        val drawnHeight = bitmapHeight * scale
        val left = (width - drawnWidth) / 2f
        val top = (height - drawnHeight) / 2f
        return CropTransform(RectF(left, top, left + drawnWidth, top + drawnHeight), scale)
    }
}
