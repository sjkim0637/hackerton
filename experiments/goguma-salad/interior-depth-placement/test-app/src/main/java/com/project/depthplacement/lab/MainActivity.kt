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
import com.project.depthplacement.PlacementConfig
import com.project.depthplacement.PlacementObjectSize
import com.project.depthplacement.PlacementResult
import com.project.depthplacement.SensitivityPreset
import com.project.depthplacement.applyTo
import com.project.depthplacement.arcore.ArCoreDepthAdapter
import com.project.depthplacement.debug.DebugRenderConfig
import com.project.depthplacement.debug.DepthProjectionView
import com.project.depthplacement.debug.PointCloudView
import java.util.Locale
import java.util.concurrent.Executors

private enum class EasyPreset { STABLE, BALANCED, DETAIL }

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
    private var lastDepthFrame: com.project.depthplacement.arcore.ArCoreDepthFrame? = null
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
            onDepthTap = { u, v ->
                lastResult = engine.evaluatePlacement(u, v, objectSize)
            }
        }
        projectionView = projected
        frame.addView(projected, FrameLayout.LayoutParams(-1, -1))
        val hud = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(12, 12, 12, 12); setBackgroundColor(0x990B1020.toInt()) }
        val metricsText = text("Waiting for depth…")
        val resultText = text("화면을 탭하면 배치를 평가합니다.")
        val rangeText = text("상대 깊이 범위를 계산하는 중…")
        hud.addView(metricsText); hud.addView(text("RGB + 상대 Depth  가까움 ■ 빨강 → 초록 → 파랑 ■ 멀리")); hud.addView(rangeText); hud.addView(resultText)
        val preset = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, listOf("Chair 0.6×0.6×1.0m", "Small 0.2×0.2×0.2m", "Trash Can 0.4×0.4×0.7m", "Custom…"))
            setSelection(0)
            onItemSelectedListener = SimpleItemSelected { position -> objectSize = when (position) {
                1 -> PlacementObjectSize(0.2f, 0.2f, 0.2f)
                2 -> PlacementObjectSize(0.4f, 0.4f, 0.7f)
                3 -> { showCustomObjectDialog(); objectSize }
                else -> PlacementObjectSize(0.6f, 0.6f, 1f)
            } }
        }
        hud.addView(preset)
        hud.addView(buttonRow(
            button("Freeze") { projected.setFrozen(!projected.isFrozen()) },
            button("Points ON/OFF") { projected.setOverlayEnabled(!projected.isOverlayEnabled()) },
        ))
        frame.addView(hud, FrameLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP))
        content.removeAllViews(); content.addView(frame)
        if (!streamRunning) startDepth()
        fun refresh() {
            if (!frame.isAttachedToWindow) return
            val metrics = engine.getMetrics(); val snapshot = engine.getLatestPointCloud(); projected.submit(snapshot)
            metricsText.text = "Depth FPS ${f(metrics.depthFps)}  ·  분석 ${metrics.pointCount} / 투영 ${snapshot?.imagePointCount ?: 0}\nPC ${f(metrics.pointGenerationMillis)} ms  ·  Placement ${f(metrics.placementEvaluationMillis)} ms"
            rangeText.text = projected.relativeRangeMeters()?.let { "화면 기준 5~95%: ${f(it.first.toDouble())}m → ${f(it.second.toDouble())}m · 근거리 강조" } ?: "상대 깊이 범위를 계산하는 중…"
            resultText.text = lastResult?.let { "${if (it.isValid) "VALID" else "NO: ${it.failureReason}"}  confidence=${f(it.confidence.toDouble())}  ${it.surface}\ndepth=${f(it.depthMeters.toDouble())}m  slope=${f(it.slopeDegrees.toDouble())}°  points=${it.validPointCount}" } ?: "화면을 탭하면 배치를 평가합니다."
            handler.postDelayed(::refresh, 100)
        }
        refresh()
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
        advanced.addView(slider("Surface Confidence (%)", 0, 100, (config.minimumSurfaceConfidence * 100).toInt()) { applyConfig(config.copy(minimumSurfaceConfidence = it / 100f)) })
        advanced.addView(slider("Obstacle Threshold (cm)", 1, 30, (config.obstacleHeightThresholdMeters * 100).toInt()) { applyConfig(config.copy(obstacleHeightThresholdMeters = it / 100f)) })
        advanced.addView(slider("Processing FPS", 5, 60, config.processingFpsLimit) { applyConfig(config.copy(processingFpsLimit = it)) })
        advanced.addView(slider("Point Size", 1, 15, renderConfig.pointSize.toInt()) { renderConfig = renderConfig.copy(pointSize = it.toFloat()); projectionView?.setPointSize(it.toFloat()) })
        advanced.addView(settingBlock("Temporal Smoothing", Switch(this).apply { isChecked = config.enableTemporalSmoothing; setOnCheckedChangeListener { _, value -> applyConfig(config.copy(enableTemporalSmoothing = value)) } }))
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
