package com.project.depthplacement

import java.util.ArrayDeque
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs
import kotlin.math.max

interface DepthPlacementEngine {
    fun start()
    fun stop()
    fun updateDepthFrame(frame: DepthFrameInput)
    fun evaluatePlacement(screenX: Float, screenY: Float, objectSize: PlacementObjectSize): PlacementResult
    fun evaluatePlacementAsync(screenX: Float, screenY: Float, objectSize: PlacementObjectSize, callback: (PlacementResult) -> Unit)
    fun getLatestPointCloud(): PointCloudSnapshot?
    fun getMetrics(): ProcessingMetrics
    fun updateConfig(config: PlacementConfig)
    fun release()
}

object DepthPlacementEngineFactory {
    fun create(config: PlacementConfig = PlacementConfig.default()): DepthPlacementEngine = DefaultDepthPlacementEngine(config)
}

private data class FrameState(
    val input: DepthFrameInput,
    val points: List<PointSample>,
    val snapshot: PointCloudSnapshot,
)

private class DefaultDepthPlacementEngine(initialConfig: PlacementConfig) : DepthPlacementEngine {
    private val config = AtomicReference(initialConfig)
    private val latest = AtomicReference<FrameState?>()
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val metricWindow = MetricWindow()
    private var previousDepth: FloatArray? = null
    @Volatile private var running = false
    @Volatile private var released = false
    @Volatile private var lastProcessedNanos = 0L

    override fun start() { check(!released); running = true }
    override fun stop() { running = false }

    override fun updateDepthFrame(frame: DepthFrameInput) {
        if (!running || released) return
        val cfg = config.get()
        val minimumInterval = 1_000_000_000L / cfg.processingFpsLimit
        if (lastProcessedNanos != 0L && frame.timestampNanos - lastProcessedNanos in 0 until minimumInterval) return
        val started = System.nanoTime()
        val points = generatePoints(frame, cfg)
        val packed = FloatArray(points.size * 4)
        points.forEachIndexed { i, point ->
            packed[i * 4] = point.position.x
            packed[i * 4 + 1] = point.position.y
            packed[i * 4 + 2] = point.position.z
            packed[i * 4 + 3] = point.confidence
        }
        val imagePoints = generateProjectionSamples(frame, cfg)
        val snapshot = PointCloudSnapshot(packed, imagePoints, points.size, frame.timestampNanos, sourceWidth = frame.width, sourceHeight = frame.height)
        latest.set(FrameState(frame, points, snapshot))
        lastProcessedNanos = frame.timestampNanos
        metricWindow.addFrame(frame.timestampNanos, (System.nanoTime() - started) / 1e6, points.size, points.size.toDouble() / (frame.width * frame.height))
    }

    private fun generatePoints(frame: DepthFrameInput, cfg: PlacementConfig): List<PointSample> {
        val prior = previousDepth?.takeIf { it.size == frame.depthMillimeters.size }
        val smoothed = FloatArray(frame.depthMillimeters.size)
        val capacity = minOf(cfg.maxPointCount, (frame.width / cfg.globalStride + 1) * (frame.height / cfg.globalStride + 1))
        val result = ArrayList<PointSample>(capacity)
        for (v in 0 until frame.height step cfg.globalStride) {
            for (u in 0 until frame.width step cfg.globalStride) {
                if (result.size >= cfg.maxPointCount) break
                val i = v * frame.width + u
                val raw = (frame.depthMillimeters[i].toInt() and 0xffff) / 1000f
                val confidence = frame.confidence?.get(i) ?: if (raw > 0f) 1f else 0f
                if (cfg.enableInvalidDepthFilter && (raw <= 0f || raw !in cfg.minDepthMeters..cfg.maxDepthMeters || confidence < cfg.depthConfidenceThreshold)) continue
                var depth = raw
                if (cfg.enableDepthJumpFilter && u >= cfg.globalStride) {
                    val neighbor = (frame.depthMillimeters[i - cfg.globalStride].toInt() and 0xffff) / 1000f
                    if (neighbor > 0f && abs(depth - neighbor) > cfg.depthJumpThresholdMeters) continue
                }
                if (cfg.enableTemporalSmoothing && prior != null && prior[i] > 0f) {
                    depth = cfg.temporalSmoothingAlpha * depth + (1f - cfg.temporalSmoothingAlpha) * prior[i]
                }
                smoothed[i] = depth
                val cameraX = (u - frame.intrinsics.cx) * depth / frame.intrinsics.fx
                val cameraY = (frame.intrinsics.cy - v) * depth / frame.intrinsics.fy
                val world = frame.cameraPose.transform(cameraX, cameraY, -depth)
                result += PointSample(world, u, v, confidence, depth)
            }
        }
        previousDepth = smoothed
        return result
    }

    /**
     * Projection samples are intentionally denser than the world-space analysis cloud.
     * This keeps plane fitting bounded while making object boundaries easier to inspect.
     */
    private fun generateProjectionSamples(frame: DepthFrameInput, cfg: PlacementConfig): FloatArray {
        val stride = ((cfg.globalStride + 1) / 2).coerceAtLeast(1)
        val capacity = minOf(30_000, ((frame.width + stride - 1) / stride) * ((frame.height + stride - 1) / stride))
        val samples = FloatArray(capacity * 4)
        var count = 0
        loop@ for (v in 0 until frame.height step stride) {
            for (u in 0 until frame.width step stride) {
                if (count >= capacity) break@loop
                val index = v * frame.width + u
                val depth = (frame.depthMillimeters[index].toInt() and 0xffff) / 1000f
                val confidence = frame.confidence?.get(index) ?: if (depth > 0f) 1f else 0f
                if (depth <= 0f || depth !in cfg.minDepthMeters..cfg.maxDepthMeters || confidence < cfg.depthConfidenceThreshold) continue
                val offset = count * 4
                samples[offset] = u.toFloat()
                samples[offset + 1] = v.toFloat()
                samples[offset + 2] = depth
                samples[offset + 3] = confidence
                count++
            }
        }
        return samples.copyOf(count * 4)
    }

    override fun evaluatePlacement(screenX: Float, screenY: Float, objectSize: PlacementObjectSize): PlacementResult {
        val started = System.nanoTime()
        val state = latest.get() ?: return failed(PlacementFailureReason.NO_DEPTH_FRAME, started)
        if (screenX < 0f || screenX >= state.input.width || screenY < 0f || screenY >= state.input.height) {
            return failed(PlacementFailureReason.OUTSIDE_DEPTH_IMAGE, started)
        }
        val cfg = config.get()
        val radius = cfg.roiSizePixels / 2
        val roi = state.points.filter { abs(it.u - screenX) <= radius && abs(it.v - screenY) <= radius && (it.u % cfg.roiStride == 0) && (it.v % cfg.roiStride == 0) }
        if (roi.size < cfg.minValidPointCount) return failed(PlacementFailureReason.INSUFFICIENT_POINTS, started, points = roi.size)
        val fit = LocalSurfaceEstimator.fit(roi) ?: return failed(PlacementFailureReason.INSUFFICIENT_POINTS, started, points = roi.size)
        val slope = slopeDegrees(fit.normal)
        val surface = when {
            slope <= 8f && fit.center.y < 0.35f -> SurfaceType.FLOOR
            slope <= cfg.maxSurfaceSlopeDegrees -> SurfaceType.HORIZONTAL_SURFACE
            slope >= 70f -> SurfaceType.WALL
            else -> SurfaceType.UNKNOWN
        }
        if (slope > cfg.maxSurfaceSlopeDegrees) return failed(PlacementFailureReason.SURFACE_TOO_STEEP, started, fit, surface, roi.size, slope)

        val axisU = Vec3(1f, 0f, 0f).let { (it - fit.normal * it.dot(fit.normal)).normalized() }
        val axisV = fit.normal.cross(axisU).normalized()
        var surfacePoints = 0
        var obstacles = 0
        var confidenceSum = 0f
        for (point in state.points) {
            val d = point.position - fit.center
            val x = abs(d.dot(axisU)); val z = abs(d.dot(axisV)); val height = d.dot(fit.normal)
            if (x <= objectSize.widthMeters / 2f && z <= objectSize.depthMeters / 2f) {
                if (abs(height) <= cfg.planeDistanceThresholdMeters) {
                    surfacePoints++; confidenceSum += point.confidence
                } else if (height > cfg.obstacleHeightThresholdMeters && height <= objectSize.heightMeters) obstacles++
            }
        }
        if (surfacePoints < cfg.minValidPointCount) return failed(PlacementFailureReason.INSUFFICIENT_SURFACE, started, fit, surface, surfacePoints, slope, obstacles)
        if (obstacles > max(2, surfacePoints / 100)) return failed(PlacementFailureReason.OBSTACLE_DETECTED, started, fit, surface, surfacePoints, slope, obstacles)
        val density = (surfacePoints.toFloat() / max(cfg.minValidPointCount, 1)).coerceAtMost(1f)
        val averageConfidence = confidenceSum / max(surfacePoints, 1)
        val flatness = (1f - fit.meanError / max(cfg.planeDistanceThresholdMeters, 0.001f)).coerceIn(0f, 1f)
        val confidence = (0.4f * density + 0.35f * averageConfidence + 0.25f * flatness).coerceIn(0f, 1f)
        if (confidence < cfg.minimumSurfaceConfidence) return failed(PlacementFailureReason.INSUFFICIENT_SURFACE, started, fit, surface, surfacePoints, slope, obstacles, confidence)
        val elapsed = (System.nanoTime() - started) / 1e6
        metricWindow.addPlacement(elapsed)
        return PlacementResult(true, confidence, surface, PlacementPose(fit.center, rotationFromUp(fit.normal), fit.normal), null, -fit.center.z, slope, surfacePoints, obstacles, elapsed)
    }

    override fun evaluatePlacementAsync(screenX: Float, screenY: Float, objectSize: PlacementObjectSize, callback: (PlacementResult) -> Unit) {
        executor.execute { callback(evaluatePlacement(screenX, screenY, objectSize)) }
    }

    private fun failed(reason: PlacementFailureReason, started: Long, fit: PlaneFit? = null, surface: SurfaceType = SurfaceType.UNKNOWN, points: Int = 0, slope: Float = 0f, obstacles: Int = 0, confidence: Float = 0f): PlacementResult {
        val elapsed = (System.nanoTime() - started) / 1e6
        metricWindow.addPlacement(elapsed)
        return PlacementResult(false, confidence, surface, fit?.let { PlacementPose(it.center, rotationFromUp(it.normal), it.normal) }, reason, fit?.center?.z?.let { -it } ?: 0f, slope, points, obstacles, elapsed)
    }

    override fun getLatestPointCloud(): PointCloudSnapshot? = latest.get()?.snapshot
    override fun getMetrics(): ProcessingMetrics = metricWindow.snapshot()
    override fun updateConfig(config: PlacementConfig) { this.config.set(config); previousDepth = null }
    override fun release() { running = false; released = true; executor.shutdownNow(); latest.set(null); previousDepth = null }
}

private class MetricWindow {
    private data class FrameMetric(val time: Long, val generationMs: Double, val count: Int, val ratio: Double)
    private val frames = ArrayDeque<FrameMetric>()
    private val placements = ArrayDeque<Pair<Long, Double>>()
    @Synchronized fun addFrame(time: Long, ms: Double, count: Int, ratio: Double) {
        frames += FrameMetric(time, ms, count, ratio); trim(time)
    }
    @Synchronized fun addPlacement(ms: Double) {
        val now = System.nanoTime(); placements += now to ms; trim(now)
    }
    private fun trim(now: Long) {
        while (frames.isNotEmpty() && now - frames.first.time > 5_000_000_000L) frames.removeFirst()
        while (placements.isNotEmpty() && now - placements.first.first > 5_000_000_000L) placements.removeFirst()
    }
    @Synchronized fun snapshot(): ProcessingMetrics {
        if (frames.isEmpty()) return ProcessingMetrics()
        val duration = ((frames.last.time - frames.first.time) / 1e9).coerceAtLeast(1.0 / 30.0)
        val ratio = frames.map { it.ratio }.average()
        return ProcessingMetrics(
            depthFps = if (frames.size < 2) 0.0 else (frames.size - 1) / duration,
            pointCloudFps = if (frames.size < 2) 0.0 else (frames.size - 1) / duration,
            pointGenerationMillis = frames.map { it.generationMs }.average(),
            placementEvaluationMillis = placements.map { it.second }.average().takeUnless { it.isNaN() } ?: 0.0,
            pointCount = frames.last.count,
            validPointRatio = ratio,
            invalidPointRatio = 1.0 - ratio,
        )
    }
}
