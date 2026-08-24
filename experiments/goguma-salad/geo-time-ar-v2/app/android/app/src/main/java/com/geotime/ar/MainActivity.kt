package com.geotime.ar

import android.Manifest
import android.animation.ValueAnimator
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.geotime.ar.ar.GeoTimeArView
import com.geotime.ar.network.GeoTimeApiClient
import com.geotime.ar.spatial.SpatialSourceType
import com.geotime.ar.time.RewindStop
import com.geotime.ar.time.RewindTimeline
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Config
import com.google.ar.core.Session
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs

class MainActivity : Activity() {
    private lateinit var arView: GeoTimeArView
    private lateinit var zoneLabel: TextView
    private lateinit var trackingLabel: TextView
    private lateinit var timeLabel: TextView
    private lateinit var gestureHint: TextView
    private lateinit var nowButton: Button
    private lateinit var previewButton: Button
    private val apiClient = GeoTimeApiClient(BuildConfig.API_BASE_URL)
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy.MM.dd")
        .withZone(ZoneId.systemDefault())
    private var arSession: Session? = null
    private var installRequested = false
    private var currentZoneId: String? = null
    private var demoPreviewEnabled = false
    private var timeline = RewindTimeline.empty()
    private var selectedStopIndex = 0
    private var gestureStartX = 0f
    private var gestureStartY = 0f
    private var contentAlpha = 1f
    private var contentAnimator: ValueAnimator? = null
    private var candidateRequestVersion = 0
    private val hideTimeLabel = Runnable {
        if (selectedStopIndex != 0) {
            timeLabel.animate().alpha(0f).setDuration(260).start()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
    }

    override fun onResume() {
        super.onResume()
        if (!hasCameraPermission()) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA, Manifest.permission.ACCESS_FINE_LOCATION), 10)
            return
        }
        ensureArSession()
        arView.onResume()
        loadZoneAndTimeline()
    }

    override fun onPause() {
        arView.onPause()
        arSession?.pause()
        super.onPause()
    }

    override fun onDestroy() {
        contentAnimator?.cancel()
        arView.detachAllAnchors()
        arSession?.close()
        apiClient.close()
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 10 && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            onResume()
        } else {
            trackingLabel.text = "카메라 권한이 필요합니다"
        }
    }

    private fun ensureArSession() {
        if (arSession != null) {
            arSession?.resume()
            return
        }
        try {
            when (ArCoreApk.getInstance().requestInstall(this, !installRequested)) {
                ArCoreApk.InstallStatus.INSTALL_REQUESTED -> {
                    installRequested = true
                    trackingLabel.text = "Google Play Services for AR 설치 중"
                    return
                }
                ArCoreApk.InstallStatus.INSTALLED -> Unit
            }
            val session = Session(this)
            session.configure(
                Config(session).apply {
                    planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
                    focusMode = Config.FocusMode.AUTO
                }
            )
            session.resume()
            arSession = session
            arView.attachSession(session)
        } catch (error: Exception) {
            trackingLabel.text = "ARCore 시작 실패: ${error.message}"
        }
    }

    private fun loadZoneAndTimeline() {
        val location = lastKnownLocation()
        val latitude = location?.latitude ?: 37.5665
        val longitude = location?.longitude ?: 126.9780
        zoneLabel.text = "GeoZone 조회 중…"
        apiClient.loadNearby(latitude, longitude) { result ->
            runOnUiThread {
                result.onSuccess { zone ->
                    if (zone == null) {
                        zoneLabel.text = "주변 GeoZone 없음"
                        clearTimeline()
                    } else {
                        currentZoneId = zone.id
                        val isInside = zone.distanceM <= zone.radiusM
                        zoneLabel.text = if (isInside) {
                            "현재 장소: ${zone.name} · Zone 내부"
                        } else {
                            "${zone.name}까지 ${zone.distanceM.toInt()}m · ${zone.radiusM.toInt()}m 이내 접근 필요"
                        }
                        if (isInside || demoPreviewEnabled) {
                            loadTimeline(zone.id)
                        } else {
                            clearTimeline()
                            timeLabel.text = "GeoZone 밖"
                            timeLabel.alpha = 1f
                        }
                    }
                }.onFailure {
                    zoneLabel.text = "Backend 연결 실패: ${it.message}"
                    clearTimeline()
                }
            }
        }
    }

    private fun loadTimeline(zoneId: String) {
        timeLabel.removeCallbacks(hideTimeLabel)
        timeLabel.text = "시간 흔적 조회 중…"
        timeLabel.alpha = 1f
        apiClient.loadTimeline(zoneId) { result ->
            runOnUiThread {
                result.onSuccess { moments ->
                    timeline = RewindTimeline.from(moments.filter { it.recordedAt <= Instant.now() })
                    selectedStopIndex = 0
                    showTimeState(RewindStop.Now, transient = false)
                    loadCandidatesForSelectedStop()
                    if (timeline.stops.size > 1) showGestureGuide()
                }.onFailure {
                    clearTimeline()
                    timeLabel.text = "Timeline 조회 실패: ${it.message}"
                    timeLabel.alpha = 1f
                }
            }
        }
    }

    private fun clearTimeline() {
        candidateRequestVersion += 1
        currentZoneId = null
        timeline = RewindTimeline.empty()
        selectedStopIndex = 0
        arView.updateCandidates(emptyList())
        nowButton.visibility = View.GONE
        setContentAlpha(1f)
    }

    private fun loadCandidatesForSelectedStop() {
        val zoneId = currentZoneId ?: return
        val stop = timeline.stopAt(selectedStopIndex)
        val at = when (stop) {
            RewindStop.Now -> Instant.now()
            is RewindStop.Moment -> stop.value.recordedAt
        }
        val requestVersion = ++candidateRequestVersion
        apiClient.loadCandidates(zoneId, at) { result ->
            runOnUiThread {
                if (requestVersion != candidateRequestVersion) return@runOnUiThread
                result.onSuccess { candidates ->
                    val selectedCandidates = when (stop) {
                        RewindStop.Now -> candidates.filter {
                            it.sourceType == SpatialSourceType.CAMPAIGN
                        }
                        is RewindStop.Moment -> candidates.filter { it.id == stop.value.id }
                    }
                    arView.updateCandidates(selectedCandidates)
                    animateContentIn()
                }.onFailure {
                    arView.updateCandidates(emptyList())
                    animateContentIn()
                    timeLabel.removeCallbacks(hideTimeLabel)
                    timeLabel.text = "콘텐츠 조회 실패: ${it.message}"
                    timeLabel.alpha = 1f
                }
            }
        }
    }

    private fun moveToStop(targetIndex: Int) {
        if (targetIndex == selectedStopIndex) {
            animateContentIn()
            showTimeState(timeline.stopAt(selectedStopIndex), transient = false)
            return
        }
        selectedStopIndex = targetIndex
        arView.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        setContentAlpha(0f)
        showTimeState(timeline.stopAt(selectedStopIndex), transient = true)
        loadCandidatesForSelectedStop()
    }

    private fun returnToNow() = moveToStop(0)

    private fun handleRewindTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                gestureStartX = event.x
                gestureStartY = event.y
                contentAnimator?.cancel()
                timeLabel.removeCallbacks(hideTimeLabel)
                showTimeState(timeline.stopAt(selectedStopIndex), transient = false)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - gestureStartX
                val dy = event.y - gestureStartY
                if (abs(dx) > abs(dy) && abs(dx) > dp(12)) {
                    val targetIndex = previewIndex(dx)
                    val progress = (abs(dx) / dp(140).toFloat()).coerceIn(0f, 1f)
                    setContentAlpha(1f - progress * 0.9f)
                    showTimeState(timeline.stopAt(targetIndex), transient = false)
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val dx = event.x - gestureStartX
                val dy = event.y - gestureStartY
                val isHorizontalSwipe = abs(dx) >= dp(72) && abs(dx) > abs(dy) * 1.2f
                if (event.actionMasked == MotionEvent.ACTION_UP && isHorizontalSwipe) {
                    moveToStop(previewIndex(dx))
                } else {
                    showTimeState(timeline.stopAt(selectedStopIndex), transient = false)
                    animateContentIn()
                }
                return true
            }
        }
        return false
    }

    private fun previewIndex(dx: Float): Int = if (dx < 0f) {
        timeline.olderIndex(selectedStopIndex)
    } else {
        timeline.newerIndex(selectedStopIndex)
    }

    private fun showTimeState(stop: RewindStop, transient: Boolean) {
        timeLabel.animate().cancel()
        timeLabel.removeCallbacks(hideTimeLabel)
        timeLabel.text = when (stop) {
            RewindStop.Now -> "NOW"
            is RewindStop.Moment -> "${dateFormatter.format(stop.value.recordedAt)}  ·  ${stop.value.title}"
        }
        timeLabel.alpha = 1f
        nowButton.visibility = if (stop === RewindStop.Now) View.GONE else View.VISIBLE
        if (transient && stop is RewindStop.Moment) {
            timeLabel.postDelayed(hideTimeLabel, 1_500)
        }
    }

    private fun showGestureGuide() {
        gestureHint.animate().cancel()
        gestureHint.text = "← 화면을 밀어 이 장소의 시간을 되감으세요"
        gestureHint.alpha = 1f
        gestureHint.animate()
            .alpha(0f)
            .setStartDelay(3_200)
            .setDuration(500)
            .start()
    }

    private fun animateContentIn() {
        contentAnimator?.cancel()
        contentAnimator = ValueAnimator.ofFloat(contentAlpha, 1f).apply {
            duration = 360
            addUpdateListener { setContentAlpha(it.animatedValue as Float) }
            start()
        }
    }

    private fun setContentAlpha(alpha: Float) {
        contentAlpha = alpha.coerceIn(0f, 1f)
        arView.updateContentAlpha(contentAlpha)
    }

    private fun lastKnownLocation(): Location? {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return null
        }
        val manager = getSystemService(LocationManager::class.java)
        return manager.getProviders(true).mapNotNull { provider ->
            runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
        }.maxByOrNull(Location::getTime)
    }

    private fun buildUi() {
        arView = GeoTimeArView(this).apply {
            onTrackingUpdate = { status -> runOnUiThread { trackingLabel.text = status } }
            setOnTouchListener { _, event -> handleRewindTouch(event) }
        }
        zoneLabel = overlayText("현재 장소 확인 중")
        trackingLabel = overlayText("ARCore 준비 중")
        timeLabel = overlayText("NOW").apply {
            gravity = Gravity.CENTER
            textSize = 22f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(18), dp(10), dp(18), dp(10))
            background = pillBackground(0xB3111827.toInt(), dp(24).toFloat())
        }
        gestureHint = overlayText("").apply {
            gravity = Gravity.CENTER
            textSize = 13f
            alpha = 0f
        }

        val root = FrameLayout(this)
        root.addView(arView, FrameLayout.LayoutParams(-1, -1))

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(12))
            setBackgroundColor(0x73111827)
            addView(zoneLabel)
            addView(trackingLabel)
        }
        root.addView(
            top,
            FrameLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP),
        )

        nowButton = actionButton("NOW로 돌아가기") { returnToNow() }.apply {
            visibility = View.GONE
        }
        previewButton = actionButton("Demo 미리보기 켜기") {
            demoPreviewEnabled = !demoPreviewEnabled
            previewButton.text = if (demoPreviewEnabled) {
                "Demo 미리보기 끄기"
            } else {
                "Demo 미리보기 켜기"
            }
            loadZoneAndTimeline()
        }
        val reloadButton = actionButton("다시 조회") { loadZoneAndTimeline() }
        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            addView(nowButton)
            addView(previewButton)
            addView(reloadButton)
        }
        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(8), dp(12), dp(14))
            addView(
                timeLabel,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
            )
            addView(gestureHint, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(actions)
        }
        root.addView(
            bottom,
            FrameLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM),
        )
        setContentView(root)
    }

    private fun actionButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        textSize = 12f
        minHeight = 0
        minimumHeight = 0
        setPadding(dp(10), dp(6), dp(10), dp(6))
        setOnClickListener { action() }
    }

    private fun overlayText(value: String) = TextView(this).apply {
        text = value
        setTextColor(Color.WHITE)
        textSize = 15f
        setPadding(0, dp(3), 0, dp(3))
    }

    private fun pillBackground(color: Int, radius: Float) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius
        setStroke(dp(1), 0x55FFFFFF)
    }

    private fun hasCameraPermission() =
        checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
