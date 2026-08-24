package com.geotime.ar

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.location.GnssMeasurement
import android.location.GnssMeasurementsEvent
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.geotime.ar.ar.GeoTimeArView
import com.geotime.ar.interaction.HeadGestureRecognizer
import com.geotime.ar.interaction.HeadMotionAxis
import com.geotime.ar.interaction.HeadPose
import com.geotime.ar.network.GeoTimeApiClient
import com.geotime.ar.time.MomentStack
import com.geotime.ar.time.TimelineMoment
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Config
import com.google.ar.core.Session
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs

private enum class ExperienceState {
    WORLD_SCAN,
    PREVIEW,
    CONFIRM,
    FULLSCREEN,
}

private enum class ExperienceMode {
    PHONE,
    GLASS_DEMO,
}

class MainActivity : Activity() {
    private lateinit var root: FrameLayout
    private lateinit var arView: GeoTimeArView
    private lateinit var zoneLabel: TextView
    private lateinit var trackingLabel: TextView
    private lateinit var markerHint: TextView
    private lateinit var coachHint: TextView
    private lateinit var demoButton: Button
    private lateinit var modeButton: Button
    private lateinit var playerOverlay: FrameLayout
    private lateinit var playerView: PlayerView
    private lateinit var promptPanel: LinearLayout
    private lateinit var promptText: TextView
    private lateinit var promptButtonRow: LinearLayout
    private lateinit var playbackDate: TextView
    private lateinit var playbackHint: TextView
    private lateinit var player: ExoPlayer

    private val apiClient = GeoTimeApiClient(BuildConfig.API_BASE_URL)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy.MM.dd")
        .withZone(ZoneId.systemDefault())
    private val stacksByMarkerId = mutableMapOf<String, MomentStack>()
    private var arSession: Session? = null
    private var installRequested = false
    private var demoPreviewEnabled = false
    private var experienceMode = ExperienceMode.PHONE
    private var experienceState = ExperienceState.WORLD_SCAN
    private var selectedStack: MomentStack? = null
    private var selectedMomentIndex = 0
    private var touchStartX = 0f
    private var touchStartY = 0f
    private val headGestureRecognizer = HeadGestureRecognizer()
    private var lastHeadPose: HeadPose? = null
    private var glassFocusedMarkerId: String? = null
    private var glassFocusStartedAtMs = 0L
    private var lastDwellSecond = -1
    private var gnssDiagnosticDialog: AlertDialog? = null
    private var gnssDiagnosticText: TextView? = null
    private var gnssEpochCount = 0
    private var gnssValidAdrCount = 0
    private var gnssResetCount = 0
    private var gnssCycleSlipCount = 0
    private val gnssMeasurementsCallback = object : GnssMeasurementsEvent.Callback() {
        override fun onGnssMeasurementsReceived(eventArgs: GnssMeasurementsEvent) {
            updateGnssDiagnostics(eventArgs.measurements)
        }

        override fun onStatusChanged(status: Int) {
            if (status == STATUS_NOT_SUPPORTED) {
                runOnUiThread {
                    gnssDiagnosticText?.text =
                        "이 기기는 Android Raw GNSS 측정을 지원하지 않습니다."
                }
            }
        }
    }
    private val finishPreview = Runnable { showPlaybackConfirmation() }
    private val hidePlaybackDate = Runnable {
        playbackDate.animate().alpha(0f).setDuration(220).start()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        clearLegacySavedLocation()
        buildUi()
        player = ExoPlayer.Builder(this).build().also {
            playerView.player = it
            it.repeatMode = Player.REPEAT_MODE_ONE
            it.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (
                        playbackState == Player.STATE_ENDED &&
                        experienceMode == ExperienceMode.GLASS_DEMO &&
                        experienceState == ExperienceState.FULLSCREEN
                    ) {
                        exitContent()
                    }
                }
            })
        }
    }

    override fun onResume() {
        super.onResume()
        if (!hasCameraPermission()) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA, Manifest.permission.ACCESS_FINE_LOCATION), 10)
            return
        }
        ensureArSession()
        arView.onResume()
        loadZoneAndMoments()
    }

    override fun onPause() {
        stopGnssDiagnostics()
        player.pause()
        arView.onPause()
        arSession?.pause()
        super.onPause()
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        player.release()
        arView.detachAllAnchors()
        arSession?.close()
        apiClient.close()
        super.onDestroy()
    }

    @Deprecated("Android 시스템 뒤로가기 호환")
    override fun onBackPressed() {
        if (experienceState != ExperienceState.WORLD_SCAN) {
            exitContent()
        } else {
            super.onBackPressed()
        }
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

    private fun loadZoneAndMoments() {
        val location = lastKnownLocation()
        if (location == null) {
            zoneLabel.text = "저장된 위치 기록이 없습니다"
            clearMomentStacks()
            markerHint.text = "위치 권한을 허용하고 GPS를 켠 뒤 다시 시도하세요"
            markerHint.visibility = View.VISIBLE
            coachHint.visibility = View.GONE
            return
        }
        zoneLabel.text = "Android 최근 GPS 기록 기준 GeoZone 조회 중…"
        apiClient.loadNearby(location.latitude, location.longitude) { result ->
            runOnUiThread {
                result.onSuccess { zone ->
                    if (zone == null) {
                        zoneLabel.text = "주변 GeoZone 없음"
                        clearMomentStacks()
                    } else {
                        val isInside = zone.distanceM <= zone.radiusM
                        zoneLabel.text = if (isInside) {
                            "현재 장소: ${zone.name} · Zone 내부"
                        } else {
                            "${zone.name}까지 ${zone.distanceM.toInt()}m · 접근 필요"
                        }
                        if (isInside || demoPreviewEnabled) {
                            loadMomentStacks(zone.id)
                        } else {
                            clearMomentStacks()
                            markerHint.text = "현재 장소에 가까이 가면 시간 기록이 나타납니다"
                            markerHint.visibility = View.VISIBLE
                            coachHint.visibility = View.GONE
                        }
                    }
                }.onFailure {
                    zoneLabel.text = "Backend 연결 실패: ${it.message}"
                    clearMomentStacks()
                }
            }
        }
    }

    private fun loadMomentStacks(zoneId: String) {
        markerHint.text = "이 장소의 시간 기록 조회 중…"
        markerHint.visibility = View.VISIBLE
        coachHint.visibility = View.GONE
        apiClient.loadTimeline(zoneId) { result ->
            runOnUiThread {
                result.onSuccess { moments ->
                    val stacks = MomentStack.group(moments)
                    stacksByMarkerId.clear()
                    stacks.associateByTo(stacksByMarkerId, MomentStack::id)
                    arView.updateCandidates(stacks.map(MomentStack::asSpatialCandidate))
                    if (stacks.isEmpty()) {
                        markerHint.text = "이 장소에는 아직 시간 기록이 없습니다"
                        markerHint.visibility = View.VISIBLE
                        coachHint.visibility = View.GONE
                    } else {
                        markerHint.visibility = View.GONE
                        showCoach(worldGuideText())
                    }
                }.onFailure {
                    clearMomentStacks()
                    markerHint.text = "시간 기록 조회 실패: ${it.message}"
                    markerHint.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun clearMomentStacks() {
        resetGlassDwell()
        stacksByMarkerId.clear()
        arView.updateCandidates(emptyList())
    }

    private fun handleWorldTouch(event: MotionEvent): Boolean {
        if (experienceMode == ExperienceMode.GLASS_DEMO) return true
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchStartX = event.x
                touchStartY = event.y
                return true
            }
            MotionEvent.ACTION_UP -> {
                val moved = abs(event.x - touchStartX) + abs(event.y - touchStartY)
                if (moved < dp(24)) {
                    val stack = arView.focusedCandidateId()?.let(stacksByMarkerId::get)
                    if (stack == null) {
                        showCoach("마커를 화면 중앙에 맞춘 뒤 터치해 주세요")
                    } else {
                        beginPreview(stack)
                    }
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> return true
        }
        return true
    }

    private fun beginPreview(stack: MomentStack) {
        resetGlassDwell()
        coachHint.visibility = View.GONE
        selectedStack = stack
        selectedMomentIndex = 0
        experienceState = ExperienceState.PREVIEW
        playerOverlay.visibility = View.VISIBLE
        playerOverlay.setBackgroundColor(0x66000000)
        promptPanel.visibility = View.GONE
        playbackDate.visibility = View.GONE
        playbackHint.text = if (experienceMode == ExperienceMode.GLASS_DEMO) {
            "GLASS · 5초 미리보기 · 소리 없음"
        } else {
            "5초 미리보기 · 소리 없음"
        }
        playbackHint.visibility = View.VISIBLE
        playerView.useController = false
        player.repeatMode = Player.REPEAT_MODE_ONE
        playerView.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(240),
            Gravity.CENTER,
        ).apply {
            marginStart = dp(18)
            marginEnd = dp(18)
        }
        playMoment(stack.momentAt(selectedMomentIndex), muted = true, restart = true)
        mainHandler.removeCallbacks(finishPreview)
        mainHandler.postDelayed(finishPreview, PREVIEW_DURATION_MS)
    }

    private fun showPlaybackConfirmation() {
        if (experienceState != ExperienceState.PREVIEW) return
        experienceState = ExperienceState.CONFIRM
        player.pause()
        playbackHint.visibility = View.GONE
        if (experienceMode == ExperienceMode.GLASS_DEMO) {
            promptText.text = "해당 영상을 재생할까요?\n\n상하로 끄덕임  예\n좌우로 흔들기  아니오"
            promptButtonRow.visibility = View.GONE
            headGestureRecognizer.reset(lastHeadPose)
        } else {
            promptText.text = "미리보기를 봤어요\n전체 영상을 재생할까요?"
            promptButtonRow.visibility = View.VISIBLE
        }
        promptPanel.visibility = View.VISIBLE
        promptPanel.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
    }

    private fun enterFullscreenPlayback() {
        val stack = selectedStack ?: return
        experienceState = ExperienceState.FULLSCREEN
        promptPanel.visibility = View.GONE
        playerOverlay.setBackgroundColor(0x80111827.toInt())
        playerView.layoutParams = FrameLayout.LayoutParams(-1, -1)
        playerView.useController = false
        player.repeatMode = Player.REPEAT_MODE_OFF
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )
        playMoment(stack.momentAt(selectedMomentIndex), muted = false, restart = true)
        showMomentDate()
        if (experienceMode == ExperienceMode.GLASS_DEMO) {
            headGestureRecognizer.reset(lastHeadPose)
        }
        playbackHint.animate().cancel()
        playbackHint.alpha = 1f
        playbackHint.text = if (experienceMode == ExperienceMode.GLASS_DEMO) {
            glassContentGuideText()
        } else {
            "← 최근 · 좌우로 넘기기 · 과거 →  ·  아래로 내려 AR 복귀"
        }
        val showGuides = preferences().getBoolean(PREF_SHOW_GUIDES, true)
        playbackHint.visibility = if (showGuides) View.VISIBLE else View.GONE
        if (showGuides && experienceMode == ExperienceMode.PHONE) {
            playbackHint.animate().alpha(0f).setStartDelay(2_500).setDuration(350).start()
        }
    }

    private fun handleContentTouch(event: MotionEvent): Boolean {
        if (experienceMode == ExperienceMode.GLASS_DEMO) return true
        if (experienceState != ExperienceState.FULLSCREEN) return true
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchStartX = event.x
                touchStartY = event.y
                return true
            }
            MotionEvent.ACTION_UP -> {
                val dx = event.x - touchStartX
                val dy = event.y - touchStartY
                when {
                    dy > dp(90) && abs(dy) > abs(dx) * 1.2f -> exitContent()
                    abs(dx) >= dp(72) && abs(dx) > abs(dy) * 1.2f -> moveContent(dx)
                    abs(dx) + abs(dy) < dp(24) -> {
                        if (player.isPlaying) player.pause() else player.play()
                    }
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> return true
        }
        return true
    }

    private fun moveContent(deltaX: Float) {
        val stack = selectedStack ?: return
        val nextIndex = stack.indexAfterHorizontalDrag(selectedMomentIndex, deltaX)
        if (nextIndex == selectedMomentIndex) {
            playerView.performHapticFeedback(HapticFeedbackConstants.REJECT)
            return
        }
        selectedMomentIndex = nextIndex
        playerView.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        playMoment(stack.momentAt(selectedMomentIndex), muted = false, restart = true)
        showMomentDate()
    }

    private fun playMoment(moment: TimelineMoment, muted: Boolean, restart: Boolean) {
        val source = if (moment.mimeType?.startsWith("video/") == true && moment.mediaUrl != null) {
            Uri.parse(moment.mediaUrl)
        } else {
            Uri.parse(DEMO_VIDEO_URL)
        }
        player.volume = if (muted) 0f else 1f
        if (restart) {
            player.setMediaItem(MediaItem.fromUri(source))
            player.prepare()
        }
        player.play()
    }

    private fun showMomentDate() {
        val moment = selectedStack?.momentAt(selectedMomentIndex) ?: return
        playbackDate.animate().cancel()
        playbackDate.removeCallbacks(hidePlaybackDate)
        playbackDate.text = "${dateFormatter.format(moment.recordedAt)}  ·  ${moment.title}"
        playbackDate.visibility = View.VISIBLE
        playbackDate.alpha = 1f
        playbackDate.postDelayed(hidePlaybackDate, 1_800)
    }

    private fun exitContent() {
        mainHandler.removeCallbacks(finishPreview)
        playbackDate.removeCallbacks(hidePlaybackDate)
        player.stop()
        playerOverlay.visibility = View.GONE
        playbackHint.alpha = 1f
        selectedStack = null
        selectedMomentIndex = 0
        experienceState = ExperienceState.WORLD_SCAN
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
        resetGlassInteraction()
        showCoach(worldGuideText())
    }

    private fun processGlassFrame(markerId: String?, pose: HeadPose, timestampMs: Long) {
        lastHeadPose = pose
        if (experienceMode != ExperienceMode.GLASS_DEMO) return
        when (experienceState) {
            ExperienceState.WORLD_SCAN -> processGlassDwell(markerId, timestampMs)
            ExperienceState.PREVIEW -> Unit
            ExperienceState.CONFIRM -> {
                val motion = headGestureRecognizer.update(pose, timestampMs) ?: return
                when (motion.axis) {
                    HeadMotionAxis.PITCH -> enterFullscreenPlayback()
                    HeadMotionAxis.YAW -> exitContent()
                }
            }
            ExperienceState.FULLSCREEN -> processGlassContentGesture(pose, timestampMs)
        }
    }

    private fun processGlassDwell(markerId: String?, timestampMs: Long) {
        if (markerId == null || markerId !in stacksByMarkerId) {
            resetGlassDwell()
            showCoach("GLASS · 마커를 화면 중앙에 맞춰 주세요")
            return
        }
        if (markerId != glassFocusedMarkerId) {
            glassFocusedMarkerId = markerId
            glassFocusStartedAtMs = timestampMs
            lastDwellSecond = -1
        }
        val elapsedMs = timestampMs - glassFocusStartedAtMs
        val remainingSeconds = ((GLASS_DWELL_MS - elapsedMs).coerceAtLeast(0L) + 999L) / 1_000L
        if (remainingSeconds.toInt() != lastDwellSecond) {
            lastDwellSecond = remainingSeconds.toInt()
            showCoach("GLASS · 응시 유지 ${remainingSeconds}초")
        }
        if (elapsedMs >= GLASS_DWELL_MS) {
            stacksByMarkerId[markerId]?.let(::beginPreview)
        }
    }

    private fun processGlassContentGesture(pose: HeadPose, timestampMs: Long) {
        headGestureRecognizer.update(pose, timestampMs)?.let { motion ->
            if (motion.axis == HeadMotionAxis.YAW) {
                moveContent(if (motion.direction > 0) 1f else -1f)
            }
        }
    }

    private fun toggleExperienceMode() {
        if (experienceState != ExperienceState.WORLD_SCAN) exitContent()
        experienceMode = if (experienceMode == ExperienceMode.PHONE) {
            ExperienceMode.GLASS_DEMO
        } else {
            ExperienceMode.PHONE
        }
        resetGlassInteraction()
        modeButton.text = if (experienceMode == ExperienceMode.GLASS_DEMO) {
            "Glass 데모 → Phone"
        } else {
            "Phone → Glass 데모"
        }
        showCoach(worldGuideText())
    }

    private fun showSettings() {
        val guideSwitch = Switch(this).apply {
            text = "동작별 조작 안내 표시"
            textSize = 16f
            isChecked = preferences().getBoolean(PREF_SHOW_GUIDES, true)
            setPadding(dp(8), dp(10), dp(8), dp(10))
            setOnCheckedChangeListener { _, enabled ->
                preferences().edit().putBoolean(PREF_SHOW_GUIDES, enabled).apply()
                refreshGuideVisibility()
            }
        }
        val note = TextView(this).apply {
            text = "끄면 현재 동작에 맞춰 표시되는 조작 안내를 숨깁니다. 조회 상태와 오류 메시지는 계속 표시됩니다."
            setTextColor(0xFF4B5563.toInt())
            textSize = 13f
            setPadding(dp(8), dp(4), dp(8), dp(8))
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(4), dp(16), dp(4))
            addView(guideSwitch, LinearLayout.LayoutParams(-1, -2))
            addView(note, LinearLayout.LayoutParams(-1, -2))
        }
        AlertDialog.Builder(this)
            .setTitle("Geo-Time AR 설정")
            .setView(content)
            .setPositiveButton("완료", null)
            .show()
    }

    private fun showCoach(message: String) {
        if (!preferences().getBoolean(PREF_SHOW_GUIDES, true)) {
            coachHint.visibility = View.GONE
            return
        }
        coachHint.animate().cancel()
        coachHint.text = message
        coachHint.alpha = 1f
        coachHint.visibility = View.VISIBLE
    }

    private fun refreshGuideVisibility() {
        if (!preferences().getBoolean(PREF_SHOW_GUIDES, true)) {
            coachHint.visibility = View.GONE
            if (experienceState == ExperienceState.FULLSCREEN) playbackHint.visibility = View.GONE
            return
        }
        when (experienceState) {
            ExperienceState.WORLD_SCAN -> showCoach(worldGuideText())
            ExperienceState.FULLSCREEN -> {
                playbackHint.text = if (experienceMode == ExperienceMode.GLASS_DEMO) {
                    glassContentGuideText()
                } else {
                    "← 최근 · 좌우로 넘기기 · 과거 →  ·  아래로 내려 AR 복귀"
                }
                playbackHint.alpha = 1f
                playbackHint.visibility = View.VISIBLE
            }
            ExperienceState.PREVIEW, ExperienceState.CONFIRM -> Unit
        }
    }

    private fun preferences() = getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE)

    private fun clearLegacySavedLocation() {
        preferences().edit()
            .remove("last_latitude_bits")
            .remove("last_longitude_bits")
            .remove("last_location_time")
            .remove("last_location_accuracy")
            .apply()
    }

    private fun resetGlassDwell() {
        glassFocusedMarkerId = null
        glassFocusStartedAtMs = 0L
        lastDwellSecond = -1
    }

    private fun resetGlassInteraction() {
        resetGlassDwell()
        headGestureRecognizer.reset(lastHeadPose)
    }

    private fun worldGuideText(): String = if (experienceMode == ExperienceMode.GLASS_DEMO) {
        "GLASS · 마커를 화면 중앙에서 5초간 응시하세요"
    } else {
        "시간 기록 마커를 터치해 미리보세요"
    }

    private fun glassContentGuideText() =
        "GLASS · 3DoF 정면 스크린 · 빠른 좌우 왕복: 기록 이동"

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
            onTrackingUpdate = { status ->
                runOnUiThread {
                    trackingLabel.text = status
                    if (!status.startsWith("6DoF")) resetGlassDwell()
                }
            }
            onSpatialFrame = { markerId, pose, timestampMs ->
                runOnUiThread { processGlassFrame(markerId, pose, timestampMs) }
            }
            setOnTouchListener { _, event -> handleWorldTouch(event) }
        }
        zoneLabel = overlayText("현재 장소 확인 중")
        trackingLabel = overlayText("ARCore 준비 중")
        markerHint = overlayText("시간 기록을 불러오는 중…").apply {
            gravity = Gravity.CENTER
            textSize = 14f
            setPadding(dp(14), dp(9), dp(14), dp(9))
            background = pillBackground(0xB3111827.toInt(), dp(20).toFloat())
        }
        coachHint = overlayText("").apply {
            gravity = Gravity.CENTER
            textSize = 14f
            setPadding(dp(14), dp(9), dp(14), dp(9))
            background = pillBackground(0xD9A16207.toInt(), dp(20).toFloat())
            visibility = View.GONE
        }

        root = FrameLayout(this)
        root.addView(arView, FrameLayout.LayoutParams(-1, -1))

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(12))
            setBackgroundColor(0x73111827)
            addView(zoneLabel)
            addView(trackingLabel)
        }
        root.addView(top, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))

        demoButton = actionButton("Demo 미리보기 켜기") {
            demoPreviewEnabled = !demoPreviewEnabled
            demoButton.text = if (demoPreviewEnabled) "Demo 미리보기 끄기" else "Demo 미리보기 켜기"
            loadZoneAndMoments()
        }
        val reloadButton = actionButton("다시 조회") { loadZoneAndMoments() }
        modeButton = actionButton("Phone → Glass 데모") { toggleExperienceMode() }
        val gnssButton = actionButton("GNSS 진단") { showGnssDiagnostics() }
        val settingsButton = actionButton("설정") { showSettings() }
        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(8), dp(12), dp(14))
            addView(markerHint)
            addView(coachHint)
            addView(LinearLayout(this@MainActivity).apply {
                gravity = Gravity.CENTER
                addView(modeButton)
                addView(demoButton)
            })
            addView(LinearLayout(this@MainActivity).apply {
                gravity = Gravity.CENTER
                addView(reloadButton)
                addView(gnssButton)
                addView(settingsButton)
            })
        }
        root.addView(bottom, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))

        buildPlayerOverlay()
        root.addView(playerOverlay, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)
    }

    private fun showGnssDiagnostics() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            AlertDialog.Builder(this)
                .setTitle("GNSS 진단")
                .setMessage("정밀 위치 권한을 허용해야 위성 원시 신호를 확인할 수 있습니다.")
                .setPositiveButton("확인", null)
                .show()
            return
        }

        stopGnssDiagnostics()
        gnssEpochCount = 0
        gnssValidAdrCount = 0
        gnssResetCount = 0
        gnssCycleSlipCount = 0
        gnssDiagnosticText = TextView(this).apply {
            text = "위성 신호를 기다리는 중…\n\n가능하면 야외에서 하늘이 잘 보이도록 기기를 유지하세요."
            setTextColor(0xFF111827.toInt())
            textSize = 14f
            setPadding(dp(20), dp(16), dp(20), dp(16))
            typeface = Typeface.MONOSPACE
        }
        val scroll = ScrollView(this).apply { addView(gnssDiagnosticText) }
        gnssDiagnosticDialog = AlertDialog.Builder(this)
            .setTitle("내장 GNSS · Carrier Phase 진단")
            .setView(scroll)
            .setNegativeButton("닫기", null)
            .create()
            .also { dialog ->
                dialog.setOnDismissListener { stopGnssDiagnostics() }
                dialog.show()
            }

        val manager = getSystemService(LocationManager::class.java)
        val registered = runCatching {
            manager.registerGnssMeasurementsCallback(gnssMeasurementsCallback, mainHandler)
        }.getOrDefault(false)
        if (!registered) {
            gnssDiagnosticText?.text = "GNSS 원시 측정을 시작하지 못했습니다.\nGPS 설정과 기기 지원 여부를 확인하세요."
        }
    }

    private fun updateGnssDiagnostics(measurements: Collection<GnssMeasurement>) {
        val l1Signals = measurements.count { it.hasCarrierFrequencyHz() && it.carrierFrequencyHz in 1.55e9f..1.61e9f }
        val l5Signals = measurements.count { it.hasCarrierFrequencyHz() && it.carrierFrequencyHz in 1.16e9f..1.19e9f }
        val validAdr = measurements.count { it.accumulatedDeltaRangeState and GnssMeasurement.ADR_STATE_VALID != 0 }
        val reset = measurements.count { it.accumulatedDeltaRangeState and GnssMeasurement.ADR_STATE_RESET != 0 }
        val cycleSlip = measurements.count { it.accumulatedDeltaRangeState and GnssMeasurement.ADR_STATE_CYCLE_SLIP != 0 }
        val halfCycleResolved = measurements.count {
            it.accumulatedDeltaRangeState and GnssMeasurement.ADR_STATE_HALF_CYCLE_RESOLVED != 0
        }
        gnssEpochCount += 1
        gnssValidAdrCount += validAdr
        gnssResetCount += reset
        gnssCycleSlipCount += cycleSlip

        val judgment = when {
            validAdr == 0 -> "ADR 없음 · 내장 Carrier Phase RTK 곤란"
            reset > 0 || cycleSlip > 0 -> "ADR 감지 · 아직 불안정"
            gnssEpochCount < 10 -> "ADR 감지 · 더 수집해야 판단 가능"
            else -> "Carrier Phase 검증 후보"
        }
        runOnUiThread {
            gnssDiagnosticText?.text = buildString {
                appendLine("초기 판정  $judgment")
                appendLine()
                appendLine("현재 수신 신호  ${measurements.size}개")
                appendLine("L1/E1 대역      ${l1Signals}개")
                appendLine("L5/E5a 대역     ${l5Signals}개")
                appendLine("유효 ADR         ${validAdr}개")
                appendLine("Half-cycle 해결  ${halfCycleResolved}개")
                appendLine("Reset            ${reset}개")
                appendLine("Cycle Slip       ${cycleSlip}개")
                appendLine()
                appendLine("누적 Epoch       ${gnssEpochCount}회")
                appendLine("누적 유효 ADR    ${gnssValidAdrCount}개")
                appendLine("누적 Reset       ${gnssResetCount}개")
                appendLine("누적 Cycle Slip  ${gnssCycleSlipCount}개")
                appendLine()
                append("이 화면은 진단값을 저장하거나 서버로 전송하지 않습니다.")
            }
        }
    }

    private fun stopGnssDiagnostics() {
        runCatching {
            getSystemService(LocationManager::class.java)
                .unregisterGnssMeasurementsCallback(gnssMeasurementsCallback)
        }
        gnssDiagnosticDialog = null
        gnssDiagnosticText = null
    }

    private fun buildPlayerOverlay() {
        playerOverlay = FrameLayout(this).apply {
            visibility = View.GONE
            isClickable = true
        }
        playerView = PlayerView(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            setShutterBackgroundColor(Color.TRANSPARENT)
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            useController = false
            setOnTouchListener { _, event -> handleContentTouch(event) }
        }
        playerOverlay.addView(playerView, FrameLayout.LayoutParams(-1, dp(240), Gravity.CENTER))

        playbackDate = overlayText("").apply {
            textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(14), dp(9), dp(14), dp(9))
            background = pillBackground(0xB3111827.toInt(), dp(20).toFloat())
            visibility = View.GONE
        }
        playerOverlay.addView(
            playbackDate,
            FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
                topMargin = dp(36)
            },
        )

        playbackHint = overlayText("").apply {
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = pillBackground(0xB3111827.toInt(), dp(18).toFloat())
        }
        playerOverlay.addView(
            playbackHint,
            FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
                bottomMargin = dp(28)
            },
        )

        promptText = overlayText("미리보기를 봤어요\n전체 영상을 재생할까요?").apply {
            gravity = Gravity.CENTER
            textSize = 17f
            setTypeface(typeface, Typeface.BOLD)
        }
        val yesButton = actionButton("전체 영상 보기") { enterFullscreenPlayback() }
        val noButton = actionButton("닫기") { exitContent() }
        promptPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(22), dp(18), dp(22), dp(16))
            background = pillBackground(0xF0111827.toInt(), dp(20).toFloat())
            addView(promptText, LinearLayout.LayoutParams(-1, -2))
            promptButtonRow = LinearLayout(this@MainActivity).apply {
                gravity = Gravity.CENTER
                addView(noButton)
                addView(yesButton)
            }
            addView(promptButtonRow)
            visibility = View.GONE
        }
        playerOverlay.addView(
            promptPanel,
            FrameLayout.LayoutParams(-2, -2, Gravity.CENTER).apply { topMargin = dp(300) },
        )
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

    companion object {
        private const val PREVIEW_DURATION_MS = 5_000L
        private const val GLASS_DWELL_MS = 5_000L
        private const val PREFERENCES_NAME = "geo_time_ar_settings"
        private const val PREF_SHOW_GUIDES = "show_guides"
        private const val DEMO_VIDEO_URL =
            "https://storage.googleapis.com/exoplayer-test-media-0/BigBuckBunny_320x180.mp4"
    }
}
