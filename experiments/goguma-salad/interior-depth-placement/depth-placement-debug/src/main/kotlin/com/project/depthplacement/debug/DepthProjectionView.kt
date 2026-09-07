package com.project.depthplacement.debug

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.project.depthplacement.PointCloudSnapshot
import java.util.Arrays
import kotlin.math.hypot

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
    private val measurementPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.YELLOW
        strokeWidth = 3f * resources.displayMetrics.density
        style = Paint.Style.STROKE
    }
    private val projectilePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(58, 64, 72); style = Paint.Style.FILL }
    private val projectileMidPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(126, 136, 148); style = Paint.Style.FILL }
    private val projectileHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(226, 232, 238); style = Paint.Style.FILL }
    private val projectileOutlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(22, 26, 31); strokeWidth = 2.5f * resources.displayMetrics.density; style = Paint.Style.STROKE }
    private val slingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(120, 72, 38); strokeWidth = 5f * resources.displayMetrics.density; style = Paint.Style.STROKE }
    private val placedShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xB0000000.toInt(); strokeWidth = 8f * resources.displayMetrics.density; style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val placedObjectPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 4f * resources.displayMetrics.density; style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
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
    private var measurementPoints: FloatArray? = null
    private data class PlacedObjectOverlay(val segments: FloatArray, val labelU: Float, val labelV: Float, val label: String, val wallMounted: Boolean)
    private var placedObject: PlacedObjectOverlay? = null
    private data class ProjectileOverlay(val u: Float, val v: Float, val radiusDepthPx: Float, val ballLabel: String, val hitU: Float?, val hitV: Float?, val hitLabel: String)
    private var projectile: ProjectileOverlay? = null
    private var slingshotEnabled = false
    private var slingDragging = false
    private var slingAnchorX = 0f
    private var slingAnchorY = 0f
    private var slingBallX = 0f
    private var slingBallY = 0f
    var onDepthTap: ((uPx: Float, vPx: Float) -> Unit)? = null
    var onSlingshotRelease: ((yawOffsetDegrees: Float, pitchOffsetDegrees: Float, power: Float) -> Unit)? = null

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
    fun showMeasurement(startU: Float, startV: Float, endU: Float? = null, endV: Float? = null) {
        measurementPoints = if (endU == null || endV == null) floatArrayOf(startU, startV) else floatArrayOf(startU, startV, endU, endV)
        invalidate()
    }
    fun clearMeasurement() { measurementPoints = null; invalidate() }
    fun showPlacedObject(segments: FloatArray, labelU: Float, labelV: Float, label: String, wallMounted: Boolean) {
        placedObject = PlacedObjectOverlay(segments.copyOf(), labelU, labelV, label, wallMounted)
        postInvalidateOnAnimation()
    }
    fun clearPlacedObject() { placedObject = null; invalidate() }
    fun setSlingshotEnabled(value: Boolean) { slingshotEnabled = value; slingDragging = false; invalidate() }
    fun showProjectile(u: Float, v: Float, radiusDepthPx: Float, ballLabel: String = "", hitU: Float? = null, hitV: Float? = null, hitLabel: String = "HIT") {
        projectile = ProjectileOverlay(u, v, radiusDepthPx, ballLabel, hitU, hitV, hitLabel)
        postInvalidateOnAnimation()
    }
    fun clearProjectile() { projectile = null; invalidate() }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.rgb(5, 9, 18))
        val bitmap = cameraBitmap
        val transform = bitmap?.let { centerCropTransform(it.width, it.height) }
        if (bitmap != null && transform != null) canvas.drawBitmap(bitmap, null, transform.rect, imagePaint)
        else canvas.drawText("카메라 영상을 기다리는 중…", 24f, height / 2f, messagePaint)

        val data = projection
        if (data == null) { drawSlingshot(canvas); return }
        val bitmapWidth = bitmap?.width?.toFloat() ?: width.toFloat()
        val bitmapHeight = bitmap?.height?.toFloat() ?: height.toFloat()
        val scale = transform?.scale ?: 1f
        val left = transform?.rect?.left ?: 0f
        val top = transform?.rect?.top ?: 0f
        if (overlayEnabled) {
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
        drawPlacedObject(canvas, data, bitmapWidth, bitmapHeight, scale, left, top)
        drawMeasurement(canvas, data, bitmapWidth, bitmapHeight, scale, left, top)
        drawProjectile(canvas, data, bitmapWidth, bitmapHeight, scale, left, top)
        drawSlingshot(canvas)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (slingshotEnabled) return handleSlingshot(event)
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

    private fun handleSlingshot(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                slingDragging = true
                slingAnchorX = event.x
                slingAnchorY = event.y
                slingBallX = event.x
                slingBallY = event.y
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> if (slingDragging) {
                val maxPull = minOf(width, height) * 0.28f
                val dx = event.x - slingAnchorX
                val dy = event.y - slingAnchorY
                val distance = hypot(dx, dy)
                val scale = if (distance > maxPull) maxPull / distance else 1f
                slingBallX = slingAnchorX + dx * scale
                slingBallY = slingAnchorY + dy * scale
                invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> if (slingDragging) {
                val launchX = slingAnchorX - slingBallX
                val launchY = slingAnchorY - slingBallY
                val maxPull = minOf(width, height) * 0.28f
                val power = (hypot(launchX, launchY) / maxPull).coerceIn(0f, 1f)
                slingDragging = false
                invalidate()
                if (event.actionMasked == MotionEvent.ACTION_UP && power >= 0.08f) {
                    val yaw = launchX / maxPull * 28f
                    val pitch = -launchY / maxPull * 22f
                    onSlingshotRelease?.invoke(yaw, pitch, power)
                }
            }
        }
        return true
    }

    private fun drawSlingshot(canvas: Canvas) {
        if (!slingshotEnabled || !slingDragging) return
        val fork = 18f * resources.displayMetrics.density
        canvas.drawLine(slingAnchorX - fork, slingAnchorY, slingBallX, slingBallY, slingPaint)
        canvas.drawLine(slingAnchorX + fork, slingAnchorY, slingBallX, slingBallY, slingPaint)
        canvas.drawCircle(slingBallX, slingBallY, 15f * resources.displayMetrics.density, projectilePaint)
        canvas.drawCircle(slingBallX - 4f * resources.displayMetrics.density, slingBallY - 4f * resources.displayMetrics.density, 6f * resources.displayMetrics.density, projectileMidPaint)
        canvas.drawCircle(slingBallX - 6f * resources.displayMetrics.density, slingBallY - 6f * resources.displayMetrics.density, 2f * resources.displayMetrics.density, projectileHighlightPaint)
        canvas.drawCircle(slingBallX, slingBallY, 15f * resources.displayMetrics.density, projectileOutlinePaint)
    }

    private fun drawProjectile(canvas: Canvas, data: Projection, bitmapWidth: Float, bitmapHeight: Float, scale: Float, left: Float, top: Float) {
        val ball = projectile ?: return
        ball.hitU?.let { hitU -> ball.hitV?.let { hitV ->
            val hit = depthToView(hitU, hitV, data, bitmapWidth, bitmapHeight, scale, left, top)
            measurementPaint.color = Color.MAGENTA
            canvas.drawCircle(hit.x, hit.y, 12f * resources.displayMetrics.density, measurementPaint)
            canvas.drawText(ball.hitLabel, hit.x + 14f * resources.displayMetrics.density, hit.y, messagePaint)
            measurementPaint.color = Color.YELLOW
        } }
        val center = depthToView(ball.u, ball.v, data, bitmapWidth, bitmapHeight, scale, left, top)
        val depthToViewScale = bitmapHeight / data.sourceWidth.coerceAtLeast(1) * scale
        val radius = (ball.radiusDepthPx * depthToViewScale).coerceIn(5f * resources.displayMetrics.density, 44f * resources.displayMetrics.density)
        canvas.drawCircle(center.x, center.y, radius, projectilePaint)
        canvas.drawCircle(center.x - radius * 0.22f, center.y - radius * 0.22f, radius * 0.55f, projectileMidPaint)
        canvas.drawCircle(center.x - radius * 0.38f, center.y - radius * 0.38f, radius * 0.17f, projectileHighlightPaint)
        canvas.drawCircle(center.x, center.y, radius, projectileOutlinePaint)
        if (ball.ballLabel.isNotEmpty()) canvas.drawText(ball.ballLabel, center.x + radius + 8f * resources.displayMetrics.density, center.y, messagePaint)
    }

    private fun drawPlacedObject(canvas: Canvas, data: Projection, bitmapWidth: Float, bitmapHeight: Float, scale: Float, left: Float, top: Float) {
        val placed = placedObject ?: return
        placedObjectPaint.color = if (placed.wallMounted) Color.rgb(255, 190, 70) else Color.rgb(70, 225, 255)
        for (index in placed.segments.indices step 4) {
            val start = depthToView(placed.segments[index], placed.segments[index + 1], data, bitmapWidth, bitmapHeight, scale, left, top)
            val end = depthToView(placed.segments[index + 2], placed.segments[index + 3], data, bitmapWidth, bitmapHeight, scale, left, top)
            canvas.drawLine(start.x, start.y, end.x, end.y, placedShadowPaint)
            canvas.drawLine(start.x, start.y, end.x, end.y, placedObjectPaint)
        }
        val label = depthToView(placed.labelU, placed.labelV, data, bitmapWidth, bitmapHeight, scale, left, top)
        canvas.drawCircle(label.x, label.y, 7f * resources.displayMetrics.density, placedObjectPaint)
        canvas.drawText(placed.label, label.x + 10f * resources.displayMetrics.density, label.y - 10f * resources.displayMetrics.density, messagePaint)
    }

    private fun drawMeasurement(canvas: Canvas, data: Projection, bitmapWidth: Float, bitmapHeight: Float, scale: Float, left: Float, top: Float) {
        val points = measurementPoints ?: return
        val start = depthToView(points[0], points[1], data, bitmapWidth, bitmapHeight, scale, left, top)
        canvas.drawCircle(start.x, start.y, 8f * resources.displayMetrics.density, measurementPaint)
        if (points.size == 4) {
            val end = depthToView(points[2], points[3], data, bitmapWidth, bitmapHeight, scale, left, top)
            canvas.drawLine(start.x, start.y, end.x, end.y, measurementPaint)
            canvas.drawCircle(end.x, end.y, 8f * resources.displayMetrics.density, measurementPaint)
        }
    }

    private fun depthToView(u: Float, v: Float, data: Projection, bitmapWidth: Float, bitmapHeight: Float, scale: Float, left: Float, top: Float): PointF = PointF(
        left + (1f - v / (data.sourceHeight - 1).coerceAtLeast(1)) * bitmapWidth * scale,
        top + u / (data.sourceWidth - 1).coerceAtLeast(1) * bitmapHeight * scale,
    )

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
