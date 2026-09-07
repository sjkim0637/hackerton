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
import java.util.Arrays

/** Camera image and depth samples share one canvas so pixel alignment can be judged directly. */
class DepthProjectionView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private data class Projection(
        val points: FloatArray,
        val sourceWidth: Int,
        val sourceHeight: Int,
        val nearDepth: Float,
        val farDepth: Float,
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
    private var smoothedNearDepth = Float.NaN
    private var smoothedFarDepth = Float.NaN
    var onDepthTap: ((uPx: Float, vPx: Float) -> Unit)? = null

    fun submitCamera(bitmap: Bitmap) {
        if (!frozen) { cameraBitmap = bitmap; postInvalidateOnAnimation() }
    }

    fun submit(snapshot: PointCloudSnapshot?) {
        if (frozen || snapshot == null) return
        val points = snapshot.copyImagePoints()
        val depths = FloatArray(snapshot.imagePointCount) { points[it * 4 + 2] }
        Arrays.sort(depths)
        val near = percentile(depths, 0.05f, 0.2f)
        val far = maxOf(percentile(depths, 0.95f, 5f), near + 0.1f)
        smoothedNearDepth = smoothRange(smoothedNearDepth, near)
        smoothedFarDepth = maxOf(smoothRange(smoothedFarDepth, far), smoothedNearDepth + 0.1f)
        projection = Projection(points, snapshot.sourceWidth, snapshot.sourceHeight, smoothedNearDepth, smoothedFarDepth)
        postInvalidateOnAnimation()
    }

    fun setFrozen(value: Boolean) { frozen = value }
    fun isFrozen(): Boolean = frozen
    fun setOverlayEnabled(value: Boolean) { overlayEnabled = value; invalidate() }
    fun isOverlayEnabled(): Boolean = overlayEnabled
    fun setPointSize(value: Float) { pointRadiusPx = value.coerceIn(1f, 12f) * resources.displayMetrics.density / 2f; invalidate() }
    fun relativeRangeMeters(): Pair<Float, Float>? = projection?.let { it.nearDepth to it.farDepth }

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
        val nearInverse = 1f / data.nearDepth
        val inverseRange = nearInverse - 1f / data.farDepth
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
            // Inverse-depth expands nearby differences; percentile bounds prevent outliers
            // from collapsing most of the visible scene into one color.
            val normalized = ((nearInverse - 1f / depth) / inverseRange).coerceIn(0f, 1f)
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

    private fun percentile(sorted: FloatArray, ratio: Float, fallback: Float): Float {
        if (sorted.isEmpty()) return fallback
        return sorted[((sorted.lastIndex * ratio).toInt()).coerceIn(0, sorted.lastIndex)]
    }

    private fun smoothRange(previous: Float, current: Float): Float =
        if (previous.isFinite()) previous * 0.8f + current * 0.2f else current

    private fun centerCropTransform(bitmapWidth: Int, bitmapHeight: Int): CropTransform {
        val scale = maxOf(width.toFloat() / bitmapWidth, height.toFloat() / bitmapHeight)
        val drawnWidth = bitmapWidth * scale
        val drawnHeight = bitmapHeight * scale
        val left = (width - drawnWidth) / 2f
        val top = (height - drawnHeight) / 2f
        return CropTransform(RectF(left, top, left + drawnWidth, top + drawnHeight), scale)
    }
}
