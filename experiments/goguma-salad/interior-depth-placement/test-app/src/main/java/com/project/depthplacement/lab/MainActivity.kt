package com.project.depthplacement.lab

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Config
import com.google.ar.core.Frame
import com.google.ar.core.Session
import com.project.depthplacement.DepthPlacementEngine
import com.project.depthplacement.DepthPlacementEngineFactory
import com.project.depthplacement.DepthProjectileSimulator
import com.project.depthplacement.MeasuredDepthPoint
import com.project.depthplacement.PlacementConfig
import com.project.depthplacement.PlacementObjectSize
import com.project.depthplacement.PlacementPose
import com.project.depthplacement.PlacementResult
import com.project.depthplacement.PlacementTarget
import com.project.depthplacement.SensitivityPreset
import com.project.depthplacement.Vec3
import com.project.depthplacement.applyTo
import com.project.depthplacement.arcore.ArCoreDepthAdapter
import com.project.depthplacement.debug.DebugRenderConfig
import com.project.depthplacement.debug.DepthProjectionView
import com.project.depthplacement.debug.PointCloudView
import java.util.Locale
import java.util.concurrent.Executors

private enum class EasyPreset { STABLE, BALANCED, DETAIL }
private enum class DemoPlacementKind(
    val displayName: String,
    val target: PlacementTarget,
    val size: PlacementObjectSize,
    val wallMounted: Boolean,
) {
    FLOOR_CHAIR("바닥 · Chair 0.48×0.48×0.90m", PlacementTarget.HORIZONTAL, PlacementObjectSize(0.48f, 0.48f, 0.90f), false),
    WALL_FRAME("벽 · Picture Frame 0.55×0.38m", PlacementTarget.WALL, PlacementObjectSize(0.55f, 0.38f, 0.05f), true),
}
private data class WorldSegment(val start: Vec3, val end: Vec3)
private data class PlacedDemoObject(val kind: DemoPlacementKind, val labelPoint: Vec3, val segments: List<WorldSegment>)

class MainActivity : AppCompatActivity() {
    private lateinit var root: LinearLayout
    private lateinit var content: FrameLayout
    private lateinit var depthSurface: PointCloudView
    private lateinit var store: ConfigStore
    private lateinit var engine: DepthPlacementEngine
    private var config = PlacementConfig.default()
    private var renderConfig = DebugRenderConfig()
    private var session: Session? = null
    private var latestFrame: Frame? = null
    @Volatile private var lastDepthFrame: com.project.depthplacement.arcore.ArCoreDepthFrame? = null
    private var textureId: Int? = null
    private var textureConfiguredForSession = false
    private var depthSupported = false
    private var streamRunning = false
    private var wantsDepth = false
    private var installRequested = false
    private var lastResult: PlacementResult? = null
    @Volatile private var projectionView: DepthProjectionView? = null
    private var lastPreviewTimestampNanos = 0L
    private var lastDiagnosticTimestampNanos = 0L
    private var displayGeometryKey = ""
    private var objectSize = PlacementObjectSize(0.6f, 0.6f, 1f)
    private val handler = Handler(Looper.getMainLooper())
    private val processingExecutor = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = ConfigStore(this)
        config = store.load()
        engine = DepthPlacementEngineFactory.create(config)
        buildShell()
        if (intent.getBooleanExtra("open_point_cloud", false)) showPointCloud() else showMain()
    }

    private fun buildShell() {
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.rgb(11, 16, 32)) }
        depthSurface = PointCloudView(this).apply {
            alpha = 0f
            onCameraTextureReady = { id -> textureId = id }
            onRenderFrame = { if (streamRunning) acquireDepthFrame() }
        }
        root.addView(depthSurface, LinearLayout.LayoutParams(1, 1))
        content = FrameLayout(this)
        root.addView(content, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        val nav = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(8, 8, 8, 8) }
        listOf("Main" to ::showMain, "Point Cloud Test" to ::showPointCloud, "Settings" to ::showSettings).forEach { (label, action) ->
            nav.addView(Button(this).apply { text = label; setOnClickListener { action() } }, LinearLayout.LayoutParams(0, dp(52), 1f))
        }
        root.addView(nav)
        setContentView(root)
    }

    private fun showMain() {
        projectionView = null
        val page = verticalScroll()
        page.addView(title("Depth Placement Lab"))
        page.addView(text("온디바이스 ARCore Depth → Point Cloud → Placement"))
        val status = text("")
        page.addView(card("Depth Status", status))
        val points = text("")
        page.addView(card("Point Cloud & Placement", points))
        page.addView(buttonRow(
            button("Start Depth") { startDepth() },
            button("Stop Depth") { stopDepth() },
        ))
        page.addView(button("Open Point Cloud Test") { showPointCloud() })
        page.addView(button("Reset Config") { applyConfig(store.reset()); showMain() })
        content.removeAllViews(); content.addView(ScrollView(this).apply { addView(page) })
        fun refresh() {
            if (!page.isAttachedToWindow) return
            val snapshot = engine.getLatestPointCloud(); val metrics = engine.getMetrics()
            status.text = "Sensor        : ${if (depthSupported) "Available" else "Unknown / Unsupported"}\n" +
                "Depth Stream  : ${if (streamRunning) "Running" else "Stopped"}\n" +
                "Resolution    : ${snapshot?.let { "${it.sourceWidth} × ${it.sourceHeight}" } ?: "—"}\n" +
                "Depth FPS     : ${f(metrics.depthFps)}\nDepth Format  : DEPTH16 mm\n" +
                "Intrinsics    : ${lastDepthFrame?.input?.intrinsics?.let { "fx=${f(it.fx.toDouble())} fy=${f(it.fy.toDouble())}" } ?: "—"}\n" +
                "Timestamp Δ   : ${lastDepthFrame?.let { f(it.timestampDeltaNanos / 1e6) } ?: "—"} ms\nDevice        : ${android.os.Build.MODEL}\nAndroid       : ${android.os.Build.VERSION.RELEASE}"
            points.text = "Points        : ${metrics.pointCount}\nPoint Cloud FPS: ${f(metrics.pointCloudFps)}\nProcessing    : ${f(metrics.pointGenerationMillis)} ms\n" +
                "Valid Ratio   : ${f(metrics.validPointRatio * 100)}%\nLast Result   : ${lastResult?.let { if (it.isValid) "VALID" else it.failureReason } ?: "—"}\n" +
                "Confidence    : ${lastResult?.let { f(it.confidence.toDouble()) } ?: "—"}"
            handler.postDelayed(::refresh, 500)
        }
        refresh()
    }

    private fun showPointCloud() {
        val frame = FrameLayout(this)
        val projected = DepthProjectionView(this).apply {
            setPointSize(renderConfig.pointSize)
        }
        projectionView = projected
        frame.addView(projected, FrameLayout.LayoutParams(-1, -1))
        val hud = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(12, 12, 12, 12); setBackgroundColor(0x990B1020.toInt()) }
        val metricsText = text("Waiting for depth…")
        val resultText = text("화면을 탭하면 배치를 평가합니다.")
        val rangeText = text("상대 깊이 범위를 계산하는 중…")
        val measurementText = text("길이 측정: 대기")
        val throwText = text("공 테스트: 대기")
        hud.addView(metricsText); hud.addView(text("RGB + 상대 Depth  가까움 ■ 빨강 → 초록 → 파랑 ■ 멀리")); hud.addView(rangeText); hud.addView(resultText); hud.addView(measurementText); hud.addView(throwText)
        var selectedKind = DemoPlacementKind.FLOOR_CHAIR
        var placedObject: PlacedDemoObject? = null
        var placementPending = false
        val preset = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, DemoPlacementKind.entries.map { it.displayName })
            setSelection(0)
            onItemSelectedListener = SimpleItemSelected { position ->
                selectedKind = DemoPlacementKind.entries[position]
                objectSize = selectedKind.size
                resultText.text = if (selectedKind.wallMounted) "벽면을 탭하면 액자를 놓습니다." else "바닥을 탭하면 의자를 놓습니다."
            }
        }
        hud.addView(preset)
        var measuring = false
        var throwing = false
        var measurementStart: MeasuredDepthPoint? = null
        val projectileSimulator = DepthProjectileSimulator()
        val measureButton = button("길이 측정") { }
        val throwButton = button("공 던지기") { }
        val clearPlacementButton = button("배치 제거") {
            placedObject = null
            lastResult = null
            projected.clearPlacedObject()
            resultText.text = if (selectedKind.wallMounted) "벽면을 탭하면 액자를 놓습니다." else "바닥을 탭하면 의자를 놓습니다."
        }
        projected.onSlingshotRelease = { yawOffset, pitchOffset, power ->
            val depthFrame = lastDepthFrame?.input
            if (depthFrame == null) {
                throwText.text = "공 테스트 실패: Depth frame이 없습니다."
            } else {
                projectileSimulator.updateGeometry(engine.getLatestPointCloud())
                val launched = projectileSimulator.launch(depthFrame.cameraPose, yawOffset, pitchOffset, power)
                val target = launched.targetDepthMeters?.let { "${f(it.toDouble())}m" } ?: "없음"
                throwText.text = "쇠공 발사 · IR target=$target · yaw=${f(launched.launchYawDegrees.toDouble())}° pitch=${f(launched.launchPitchDegrees.toDouble())}° power=${f(power.toDouble() * 100.0)}%"
            }
        }
        projected.onDepthTap = { u, v ->
            if (!measuring) {
                val requestedKind = selectedKind
                val cameraPose = lastDepthFrame?.input?.cameraPose
                placementPending = true
                resultText.text = "배치 표면을 분석하는 중…"
                engine.evaluatePlacementAsync(u, v, requestedKind.size, requestedKind.target) { result ->
                    runOnUiThread {
                        if (!frame.isAttachedToWindow) return@runOnUiThread
                        placementPending = false
                        lastResult = result
                        val pose = result.pose
                        if (result.isValid && pose != null && cameraPose != null) {
                            placedObject = buildPlacedDemoObject(requestedKind, pose, cameraPose)
                        }
                    }
                }
            } else {
                val start = measurementStart
                if (start == null) {
                    val point = engine.samplePoint(u, v)
                    if (point == null) {
                        measurementText.text = "길이 측정 실패: 선택 위치에 유효 Depth가 없습니다."
                    } else {
                        measurementStart = point
                        projected.showMeasurement(point.imageX, point.imageY)
                        measurementText.text = "길이 측정: 끝점을 탭하세요."
                    }
                } else {
                    val end = engine.samplePoint(u, v)
                    measurementText.text = if (end != null) {
                        val measurement = engine.measureLength(start, end)
                        projected.showMeasurement(start.imageX, start.imageY, end.imageX, end.imageY)
                        "길이 ${f(measurement.lengthMeters.toDouble() * 100.0)}cm (${f(measurement.lengthMeters.toDouble())}m) · Z ${f(measurement.startDepthMeters.toDouble())}m → ${f(measurement.endDepthMeters.toDouble())}m"
                    } else {
                        "길이 측정 실패: 끝점에 유효 Depth가 없습니다."
                    }
                    measurementStart = null
                }
            }
        }
        measureButton.setOnClickListener {
            measuring = !measuring
            throwing = false
            projected.setSlingshotEnabled(false)
            throwButton.text = "공 던지기"
            projectileSimulator.clear()
            projected.clearProjectile()
            throwText.text = "공 테스트: 대기"
            measurementStart = null
            projected.clearMeasurement()
            measureButton.text = if (measuring) "측정 종료" else "길이 측정"
            measurementText.text = if (measuring) "길이 측정: 첫 지점을 탭하세요." else "길이 측정: 대기"
        }
        throwButton.setOnClickListener {
            throwing = !throwing
            measuring = false
            measurementStart = null
            measureButton.text = "길이 측정"
            measurementText.text = "길이 측정: 대기"
            projected.clearMeasurement()
            projected.setSlingshotEnabled(throwing)
            if (throwing) {
                projectileSimulator.clear()
                projected.clearProjectile()
                throwButton.text = "공 테스트 종료"
                throwText.text = "앵그리버드처럼 화면을 뒤로 당겼다가 놓으세요."
            } else {
                throwButton.text = "공 던지기"
                throwText.text = "공 테스트: 대기"
                projectileSimulator.clear()
                projected.clearProjectile()
            }
        }
        hud.addView(buttonRow(
            button("Freeze") { projected.setFrozen(!projected.isFrozen()) },
            button("Points ON/OFF") { projected.setOverlayEnabled(!projected.isOverlayEnabled()) },
            measureButton,
        ))
        hud.addView(buttonRow(throwButton, clearPlacementButton))
        frame.addView(hud, FrameLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP))
        content.removeAllViews(); content.addView(frame)
        if (!streamRunning) startDepth()
        var lastProjectionSubmitNanos = 0L
        fun refresh() {
            if (!frame.isAttachedToWindow) return
            val metrics = engine.getMetrics(); val snapshot = engine.getLatestPointCloud()
            if (snapshot != null && snapshot.timestampNanos - lastProjectionSubmitNanos >= 100_000_000L) {
                projected.submit(snapshot)
                lastProjectionSubmitNanos = snapshot.timestampNanos
            }
            val depthFrame = lastDepthFrame?.input
            placedObject?.let { renderPlacedDemoObject(projected, it, depthFrame) }
            projectileSimulator.updateGeometry(snapshot)
            projectileSimulator.step()?.let { projectile ->
                val ball = depthFrame?.projectWorldPoint(projectile.position)
                val hit = projectile.lastCollisionPoint?.let { depthFrame?.projectWorldPoint(it) }
                val visible = ball ?: hit
                if (visible != null && depthFrame != null) {
                    val radiusDepthPx = depthFrame.intrinsics.fx * projectile.radiusMeters / visible.depthMeters.coerceAtLeast(0.05f)
                    val kind = if ((projectile.lastCollisionNormal?.y ?: 0f) > 0.65f) "GROUND" else "SURFACE"
                    val target = projectile.targetDepthMeters?.let { f(it.toDouble()) } ?: "?"
                    projected.showProjectile(
                        u = visible.x,
                        v = visible.y,
                        radiusDepthPx = radiusDepthPx,
                        ballLabel = "${f(projectile.distanceTraveledMeters.toDouble())}/$target m",
                        hitU = hit?.x,
                        hitV = hit?.y,
                        hitLabel = "$kind HIT ${projectile.bounceCount}/3",
                    )
                } else projected.clearProjectile()
                val hitWorld = projectile.lastCollisionPoint
                val target = projectile.targetDepthMeters?.let { f(it.toDouble()) } ?: "?"
                throwText.text = "쇠공 ${if (projectile.active) "비행" else "정지"} · 이동=${f(projectile.distanceTraveledMeters.toDouble())}/$target m · bounce=${projectile.bounceCount}/3\nworld=(${f(projectile.position.x.toDouble())}, ${f(projectile.position.y.toDouble())}, ${f(projectile.position.z.toDouble())})" +
                    if (hitWorld != null) "\n충돌=(${f(hitWorld.x.toDouble())}, ${f(hitWorld.y.toDouble())}, ${f(hitWorld.z.toDouble())})" else ""
            }
            metricsText.text = "Depth FPS ${f(metrics.depthFps)}  ·  분석 ${metrics.pointCount} / 투영 ${snapshot?.imagePointCount ?: 0}\nPC ${f(metrics.pointGenerationMillis)} ms  ·  Placement ${f(metrics.placementEvaluationMillis)} ms"
            rangeText.text = projected.relativeRangeMeters()?.let { "화면 기준 5~95%: ${f(it.first.toDouble())}m → ${f(it.second.toDouble())}m · 근거리 강조" } ?: "상대 깊이 범위를 계산하는 중…"
            resultText.text = if (placementPending) "배치 표면을 분석하는 중…" else lastResult?.let {
                val status = when {
                    it.isValid -> if (selectedKind.wallMounted) "PLACED · 벽 액자" else "PLACED · 바닥 의자"
                    it.failureReason == com.project.depthplacement.PlacementFailureReason.WRONG_SURFACE -> if (selectedKind.wallMounted) "NO · 벽면을 선택하세요" else "NO · 바닥을 선택하세요"
                    else -> "NO · ${it.failureReason}"
                }
                "$status  confidence=${f(it.confidence.toDouble())}  ${it.surface}\ndepth=${f(it.depthMeters.toDouble())}m  slope=${f(it.slopeDegrees.toDouble())}°  points=${it.validPointCount}"
            } ?: if (selectedKind.wallMounted) "벽면을 탭하면 액자를 놓습니다." else "바닥을 탭하면 의자를 놓습니다."
            handler.postDelayed(::refresh, if (projectileSimulator.currentState()?.active == true) 33 else 100)
        }
        refresh()
    }

    private fun buildPlacedDemoObject(kind: DemoPlacementKind, pose: PlacementPose, cameraPose: com.project.depthplacement.CameraPose): PlacedDemoObject {
        val segments = ArrayList<WorldSegment>()
        fun segment(start: Vec3, end: Vec3) { segments += WorldSegment(start, end) }
        return if (!kind.wallMounted) {
            val up = pose.surfaceNormal.let { if (it.y < 0f) it * -1f else it }.normalized()
            val towardCamera = cameraPose.position() - pose.position
            val planarForward = towardCamera - up * towardCamera.dot(up)
            val forward = if (planarForward.length() > 0.001f) planarForward.normalized() else Vec3(0f, 0f, 1f)
            val right = forward.cross(up).normalized()
            val halfWidth = kind.size.widthMeters / 2f
            val halfDepth = kind.size.depthMeters / 2f
            val seatHeight = 0.44f
            fun point(x: Float, z: Float, height: Float) = pose.position + right * x + forward * z + up * height
            val feet = listOf(
                point(-halfWidth, -halfDepth, 0.01f), point(halfWidth, -halfDepth, 0.01f),
                point(halfWidth, halfDepth, 0.01f), point(-halfWidth, halfDepth, 0.01f),
            )
            val seat = listOf(
                point(-halfWidth, -halfDepth, seatHeight), point(halfWidth, -halfDepth, seatHeight),
                point(halfWidth, halfDepth, seatHeight), point(-halfWidth, halfDepth, seatHeight),
            )
            for (index in 0..3) {
                segment(feet[index], seat[index])
                segment(seat[index], seat[(index + 1) % 4])
            }
            val backLeft = seat[0] + up * 0.46f
            val backRight = seat[1] + up * 0.46f
            segment(seat[0], backLeft)
            segment(seat[1], backRight)
            segment(backLeft, backRight)
            segment(seat[0] + up * 0.23f, seat[1] + up * 0.23f)
            PlacedDemoObject(kind, (backLeft + backRight) * 0.5f + up * 0.06f, segments)
        } else {
            var normal = pose.surfaceNormal.normalized()
            val towardCamera = cameraPose.position() - pose.position
            if (normal.dot(towardCamera) < 0f) normal = normal * -1f
            val projectedUp = Vec3.UP - normal * Vec3.UP.dot(normal)
            val up = if (projectedUp.length() > 0.001f) projectedUp.normalized() else cameraPose.up()
            val right = up.cross(normal).normalized()
            val center = pose.position + normal * 0.025f
            val halfWidth = kind.size.widthMeters / 2f
            val halfHeight = kind.size.depthMeters / 2f
            fun point(x: Float, y: Float) = center + right * x + up * y
            val corners = listOf(
                point(-halfWidth, -halfHeight), point(halfWidth, -halfHeight),
                point(halfWidth, halfHeight), point(-halfWidth, halfHeight),
            )
            for (index in 0..3) segment(corners[index], corners[(index + 1) % 4])
            segment(corners[0], corners[2])
            segment(corners[1], corners[3])
            PlacedDemoObject(kind, point(0f, halfHeight + 0.07f), segments)
        }
    }

    private fun renderPlacedDemoObject(view: DepthProjectionView, placed: PlacedDemoObject, frame: com.project.depthplacement.DepthFrameInput?) {
        if (frame == null) return
        val projectedSegments = ArrayList<Float>(placed.segments.size * 4)
        for (segment in placed.segments) {
            val start = frame.projectWorldPoint(segment.start) ?: continue
            val end = frame.projectWorldPoint(segment.end) ?: continue
            projectedSegments += start.x; projectedSegments += start.y
            projectedSegments += end.x; projectedSegments += end.y
        }
        val label = frame.projectWorldPoint(placed.labelPoint)
        if (projectedSegments.isEmpty() || label == null) {
            view.clearPlacedObject()
            return
        }
        view.showPlacedObject(
            segments = projectedSegments.toFloatArray(),
            labelU = label.x,
            labelV = label.y,
            label = if (placed.kind.wallMounted) "FRAME · WALL" else "CHAIR · FLOOR",
            wallMounted = placed.kind.wallMounted,
        )
    }

    private fun showSettings() {
        projectionView = null
        val page = verticalScroll()
        page.addView(title("쉬운 설정"))
        page.addView(text("아래 3개 중 하나만 고르면 됩니다. 처음에는 ‘균형’을 권장합니다."))
        page.addView(buttonRow(
            button("안정") { applyEasyPreset(EasyPreset.STABLE); showSettings() },
            button("균형") { applyEasyPreset(EasyPreset.BALANCED); showSettings() },
            button("디테일") { applyEasyPreset(EasyPreset.DETAIL); showSettings() },
        ))
        page.addView(card("현재 표시 품질", text(
            "간격 ${config.globalStride} · 최대 ${config.maxPointCount} points · ${config.processingFpsLimit} FPS\n" +
                "점 크기 ${renderConfig.pointSize.toInt()} · ${if (config.enableTemporalSmoothing) "흔들림 완화 ON" else "흔들림 완화 OFF"}"
        )))
        page.addView(text("안정: 느리거나 노이즈가 많은 기기\n균형: 일반적인 확인용\n디테일: 윤곽을 더 촘촘히 표시(성능 사용량 증가)").apply { setPadding(0, dp(12), 0, dp(12)) })

        val advanced = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
        advanced.addView(slider("Sampling Stride", 1, 8, config.globalStride) { applyConfig(config.copy(globalStride = it)) })
        advanced.addView(slider("Max Point Count", 2_000, 20_000, config.maxPointCount, 1_000) { applyConfig(config.copy(maxPointCount = it)) })
        advanced.addView(slider("Minimum Depth (cm)", 10, 100, (config.minDepthMeters * 100).toInt()) { if (it / 100f < config.maxDepthMeters) applyConfig(config.copy(minDepthMeters = it / 100f)) })
        advanced.addView(slider("Maximum Depth (cm)", 100, 800, (config.maxDepthMeters * 100).toInt(), 10) { if (it / 100f > config.minDepthMeters) applyConfig(config.copy(maxDepthMeters = it / 100f)) })
        advanced.addView(slider("Confidence (%)", 0, 100, (config.depthConfidenceThreshold * 100).toInt()) { applyConfig(config.copy(depthConfidenceThreshold = it / 100f)) })
        advanced.addView(slider("Temporal Smoothing (%)", 0, 100, (config.temporalSmoothingAlpha * 100).toInt()) { applyConfig(config.copy(temporalSmoothingAlpha = it / 100f)) })
        advanced.addView(slider("ROI Size (px)", 5, 61, config.roiSizePixels, 2) { applyConfig(config.copy(roiSizePixels = it)) })
        advanced.addView(slider("ROI Stride", 1, 4, config.roiStride) { applyConfig(config.copy(roiStride = it)) })
        advanced.addView(slider("Minimum Surface Points", 3, 100, config.minValidPointCount) { applyConfig(config.copy(minValidPointCount = it)) })
        advanced.addView(slider("Maximum Slope (°)", 1, 45, config.maxSurfaceSlopeDegrees.toInt()) { applyConfig(config.copy(maxSurfaceSlopeDegrees = it.toFloat())) })
        advanced.addView(slider("Plane Distance (mm)", 5, 80, (config.planeDistanceThresholdMeters * 1000).toInt()) { applyConfig(config.copy(planeDistanceThresholdMeters = it / 1000f)) })
        advanced.addView(slider("Surface Depth Continuity (cm)", 3, 30, (config.placementDepthContinuityMeters * 100).toInt()) { applyConfig(config.copy(placementDepthContinuityMeters = it / 100f)) })
        advanced.addView(slider("Surface Confidence (%)", 0, 100, (config.minimumSurfaceConfidence * 100).toInt()) { applyConfig(config.copy(minimumSurfaceConfidence = it / 100f)) })
        advanced.addView(slider("Obstacle Threshold (cm)", 1, 30, (config.obstacleHeightThresholdMeters * 100).toInt()) { applyConfig(config.copy(obstacleHeightThresholdMeters = it / 100f)) })
        advanced.addView(slider("Processing FPS", 5, 60, config.processingFpsLimit) { applyConfig(config.copy(processingFpsLimit = it)) })
        advanced.addView(slider("Point Size", 1, 15, renderConfig.pointSize.toInt()) { renderConfig = renderConfig.copy(pointSize = it.toFloat()); projectionView?.setPointSize(it.toFloat()) })
        advanced.addView(settingBlock("Invalid Depth Filter", Switch(this).apply { isChecked = config.enableInvalidDepthFilter; setOnCheckedChangeListener { _, value -> applyConfig(config.copy(enableInvalidDepthFilter = value)) } }))
        advanced.addView(settingBlock("Depth Jump Filter", Switch(this).apply { isChecked = config.enableDepthJumpFilter; setOnCheckedChangeListener { _, value -> applyConfig(config.copy(enableDepthJumpFilter = value)) } }))
        advanced.addView(settingBlock("Temporal Smoothing", Switch(this).apply { isChecked = config.enableTemporalSmoothing; setOnCheckedChangeListener { _, value -> applyConfig(config.copy(enableTemporalSmoothing = value)) } }))
        advanced.addView(settingBlock("Plane Fitting", Switch(this).apply { isChecked = config.enablePlaneFitting; setOnCheckedChangeListener { _, value -> applyConfig(config.copy(enablePlaneFitting = value)) } }))
        advanced.addView(settingBlock("RANSAC Outlier Filter", Switch(this).apply { isChecked = config.enableRansac; setOnCheckedChangeListener { _, value -> applyConfig(config.copy(enableRansac = value)) } }))
        val advancedButton = button("세부 설정 펼치기") { }
        advancedButton.setOnClickListener {
            advanced.visibility = if (advanced.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            advancedButton.text = if (advanced.visibility == View.VISIBLE) "세부 설정 접기" else "세부 설정 펼치기"
        }
        page.addView(advancedButton)
        page.addView(advanced)
        page.addView(button("기본값으로 되돌리기") { applyConfig(store.reset()); renderConfig = DebugRenderConfig(); showSettings() })
        content.removeAllViews(); content.addView(ScrollView(this).apply { addView(page) })
    }

    private fun applyEasyPreset(preset: EasyPreset) {
        when (preset) {
            EasyPreset.STABLE -> {
                applyConfig(SensitivityPreset.LOW.applyTo(config).copy(globalStride = 6, maxPointCount = 5_000, processingFpsLimit = 20))
                renderConfig = renderConfig.copy(pointSize = 8f)
            }
            EasyPreset.BALANCED -> {
                applyConfig(PlacementConfig.default().copy(maxPointCount = 10_000, processingFpsLimit = 24))
                renderConfig = DebugRenderConfig(pointSize = 6f)
            }
            EasyPreset.DETAIL -> {
                applyConfig(SensitivityPreset.HIGH.applyTo(config).copy(globalStride = 2, maxPointCount = 20_000, processingFpsLimit = 30))
                renderConfig = renderConfig.copy(pointSize = 4f)
            }
        }
        projectionView?.setPointSize(renderConfig.pointSize)
    }

    private fun showCustomObjectDialog() {
        val fields = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), 0, dp(20), 0) }
        fun field(hint: String, value: Float) = EditText(this).apply { this.hint = hint; inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL; setText(value.toString()); fields.addView(this) }
        val width = field("Width meters", objectSize.widthMeters)
        val depth = field("Depth meters", objectSize.depthMeters)
        val height = field("Height meters", objectSize.heightMeters)
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Custom Object Size")
            .setView(fields)
            .setPositiveButton("Apply") { _, _ ->
                val values = listOf(width, depth, height).map { it.text.toString().toFloatOrNull() }
                if (values.all { it != null && it > 0f }) objectSize = PlacementObjectSize(values[0]!!, values[1]!!, values[2]!!)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun startDepth() {
        wantsDepth = true
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), CAMERA_REQUEST); return
        }
        ensureSession()
        if (depthSupported) { streamRunning = true; engine.start() }
    }

    private fun ensureSession() {
        if (session != null) return
        try {
            when (ArCoreApk.getInstance().requestInstall(this, !installRequested)) {
                ArCoreApk.InstallStatus.INSTALL_REQUESTED -> { installRequested = true; return }
                else -> Unit
            }
            session = Session(this).also { arSession ->
                depthSupported = ArCoreDepthAdapter.isDepthSupported(arSession)
                if (depthSupported) ArCoreDepthAdapter.configure(arSession, Config(arSession))
                textureConfiguredForSession = false
                displayGeometryKey = ""
                arSession.resume()
            }
        } catch (error: Exception) {
            depthSupported = false
            android.widget.Toast.makeText(this, "ARCore 시작 실패: ${error.message}", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    private fun acquireDepthFrame() {
        val arSession = session ?: return
        try {
            if (!textureConfiguredForSession) {
                val id = textureId ?: return
                arSession.setCameraTextureName(id)
                textureConfiguredForSession = true
            }
            val displayWidth = content.width
            val displayHeight = content.height
            @Suppress("DEPRECATION") val rotation = windowManager.defaultDisplay.rotation
            val geometryKey = "$rotation:$displayWidth:$displayHeight"
            if (displayWidth > 0 && displayHeight > 0 && geometryKey != displayGeometryKey) {
                arSession.setDisplayGeometry(rotation, displayWidth, displayHeight)
                displayGeometryKey = geometryKey
            }
            val frame = arSession.update(); latestFrame = frame
            ArCoreDepthAdapter.convert(frame)?.let { converted ->
                lastDepthFrame = converted
                processingExecutor.execute {
                    engine.updateDepthFrame(converted.input)
                    if (converted.input.timestampNanos - lastDiagnosticTimestampNanos >= 1_000_000_000L) {
                        lastDiagnosticTimestampNanos = converted.input.timestampNanos
                        val metrics = engine.getMetrics()
                        Log.i(TAG, "depth=${converted.input.width}x${converted.input.height} points=${metrics.pointCount} depthFps=${f(metrics.depthFps)} pcMs=${f(metrics.pointGenerationMillis)}")
                    }
                }
            }
            if (projectionView != null && frame.timestamp - lastPreviewTimestampNanos >= 100_000_000L) {
                ArCoreDepthAdapter.acquireCameraPreview(frame)?.let { preview ->
                    lastPreviewTimestampNanos = frame.timestamp
                    val bitmap = Bitmap.createBitmap(preview.argb, preview.width, preview.height, Bitmap.Config.ARGB_8888)
                    val target = projectionView
                    runOnUiThread { if (projectionView === target) target?.submitCamera(bitmap) }
                }
            }
        } catch (_: Exception) { /* Tracking/depth availability is transient. */ }
    }

    private fun stopDepth() { wantsDepth = false; streamRunning = false; engine.stop() }
    private fun applyConfig(value: PlacementConfig) { config = value; store.save(value); engine.updateConfig(value) }

    override fun onResume() {
        super.onResume()
        depthSurface.onResume()
        session?.runCatching { resume() }
        if (wantsDepth && !streamRunning) handler.post { startDepth() }
    }
    override fun onPause() { session?.pause(); depthSurface.onPause(); super.onPause() }
    override fun onDestroy() { handler.removeCallbacksAndMessages(null); processingExecutor.shutdownNow(); engine.release(); session?.close(); super.onDestroy() }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_REQUEST && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) startDepth()
    }

    private fun verticalScroll() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(18), dp(18), dp(18)) }
    private fun title(value: String) = text(value).apply { textSize = 25f; setTextColor(Color.rgb(73, 214, 255)); setPadding(0, 0, 0, dp(12)) }
    private fun text(value: String) = TextView(this).apply { text = value; textSize = 15f; setTextColor(Color.WHITE); setLineSpacing(0f, 1.25f) }
    private fun card(label: String, body: View) = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(14), dp(16), dp(14)); setBackgroundColor(Color.rgb(25, 36, 59)); addView(text(label).apply { textSize = 19f; setTextColor(Color.rgb(73, 214, 255)) }); addView(body); (body.layoutParams as? LinearLayout.LayoutParams)?.topMargin = dp(8) }
    private fun button(label: String, action: () -> Unit) = Button(this).apply { text = label; setOnClickListener { action() } }
    private fun buttonRow(vararg children: View) = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; children.forEach { addView(it, LinearLayout.LayoutParams(0, dp(52), 1f)) } }
    private fun settingBlock(label: String, control: View) = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(8), 0, dp(8)); addView(text(label)); addView(control) }
    private fun slider(label: String, min: Int, max: Int, initial: Int, step: Int = 1, changed: (Int) -> Unit): View {
        val value = text("")
        val seek = SeekBar(this).apply {
            this.max = (max - min) / step; progress = ((initial - min) / step).coerceIn(0, this.max)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) { val actual = min + progress * step; value.text = "$label: $actual"; if (fromUser) changed(actual) }
                override fun onStartTrackingTouch(bar: SeekBar?) = Unit
                override fun onStopTrackingTouch(bar: SeekBar?) = Unit
            })
        }
        value.text = "$label: ${min + seek.progress * step}"
        return LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; addView(value); addView(seek) }
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun f(value: Double) = String.format(Locale.US, "%.1f", value)

    companion object {
        private const val CAMERA_REQUEST = 41
        private const val TAG = "DepthPlacementLab"
    }
}
