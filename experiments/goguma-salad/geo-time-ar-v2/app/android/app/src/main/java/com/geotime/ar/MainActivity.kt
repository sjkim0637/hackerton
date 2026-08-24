package com.geotime.ar

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
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
    private var contentCenterPose: HeadPose? = null
    private var lookAwayStartedAtMs = 0L
    private val finishPreview = Runnable { showPlaybackConfirmation() }
    private val hidePlaybackDate = Runnable {
        playbackDate.animate().alpha(0f).setDuration(220).start()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        buildUi()
        player = ExoPlayer.Builder(this).build().also {
            playerView.player = it
            it.repeatMode = Player.REPEAT_MODE_ONE
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
        val latitude = location?.latitude ?: 37.5665
        val longitude = location?.longitude ?: 126.9780
        zoneLabel.text = "GeoZone 조회 중…"
        apiClient.loadNearby(latitude, longitude) { result ->
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
        apiClient.loadTimeline(zoneId) { result ->
            runOnUiThread {
                result.onSuccess { moments ->
                    val stacks = MomentStack.group(moments)
                    stacksByMarkerId.clear()
                    stacks.associateByTo(stacksByMarkerId, MomentStack::id)
                    arView.updateCandidates(stacks.map(MomentStack::asSpatialCandidate))
                    markerHint.text = if (stacks.isEmpty()) {
                        "이 장소에는 아직 시간 기록이 없습니다"
                    } else {
                        worldGuideText()
                    }
                }.onFailure {
                    clearMomentStacks()
                    markerHint.text = "시간 기록 조회 실패: ${it.message}"
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
                        markerHint.text = "마커가 화면 중앙에 보일 때 터치해 주세요"
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
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )
        playMoment(stack.momentAt(selectedMomentIndex), muted = false, restart = true)
        showMomentDate()
        if (experienceMode == ExperienceMode.GLASS_DEMO) {
            contentCenterPose = lastHeadPose
            lookAwayStartedAtMs = 0L
            headGestureRecognizer.reset(lastHeadPose)
        }
        playbackHint.animate().cancel()
        playbackHint.alpha = 1f
        playbackHint.text = if (experienceMode == ExperienceMode.GLASS_DEMO) {
            glassContentGuideText()
        } else {
            "← 최근 · 좌우로 넘기기 · 과거 →  ·  아래로 내려 AR 복귀"
        }
        playbackHint.visibility = View.VISIBLE
        if (experienceMode == ExperienceMode.PHONE) {
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
        markerHint.text = worldGuideText()
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
            markerHint.text = "GLASS · 마커를 화면 중앙에 맞춰 주세요"
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
            markerHint.text = "GLASS · 응시 유지 ${remainingSeconds}초"
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

        val center = contentCenterPose ?: pose.also { contentCenterPose = it }
        val yawOffset = abs(HeadGestureRecognizer.angleDelta(center.yawDegrees, pose.yawDegrees))
        val pitchOffset = abs(pose.pitchDegrees - center.pitchDegrees)
        if (maxOf(yawOffset, pitchOffset) >= GLASS_LOOK_AWAY_DEGREES) {
            if (lookAwayStartedAtMs == 0L) {
                lookAwayStartedAtMs = timestampMs
                player.pause()
            }
            val elapsedMs = timestampMs - lookAwayStartedAtMs
            val remainingMs = (GLASS_LOOK_AWAY_MS - elapsedMs).coerceAtLeast(0L)
            playbackHint.text = "GLASS · 시선 이탈 · AR 복귀 ${(remainingMs + 999L) / 1_000L}초"
            playbackHint.alpha = 1f
            if (elapsedMs >= GLASS_LOOK_AWAY_MS) exitContent()
        } else {
            if (lookAwayStartedAtMs != 0L) {
                player.play()
                playbackHint.text = glassContentGuideText()
                playbackHint.alpha = 1f
            }
            lookAwayStartedAtMs = 0L
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
        markerHint.text = worldGuideText()
    }

    private fun resetGlassDwell() {
        glassFocusedMarkerId = null
        glassFocusStartedAtMs = 0L
        lastDwellSecond = -1
    }

    private fun resetGlassInteraction() {
        resetGlassDwell()
        headGestureRecognizer.reset(lastHeadPose)
        contentCenterPose = null
        lookAwayStartedAtMs = 0L
    }

    private fun worldGuideText(): String = if (experienceMode == ExperienceMode.GLASS_DEMO) {
        "GLASS · 마커를 화면 중앙에서 5초간 응시하세요"
    } else {
        "시간 기록 마커를 터치해 미리보세요"
    }

    private fun glassContentGuideText() =
        "GLASS · 빠른 좌우 왕복: 기록 이동 · 고개를 멀리 돌려 나가기"

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
        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(8), dp(12), dp(14))
            addView(markerHint)
            addView(LinearLayout(this@MainActivity).apply {
                gravity = Gravity.CENTER
                addView(modeButton)
                addView(demoButton)
                addView(reloadButton)
            })
        }
        root.addView(bottom, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))

        buildPlayerOverlay()
        root.addView(playerOverlay, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)
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
        private const val GLASS_LOOK_AWAY_MS = 1_500L
        private const val GLASS_LOOK_AWAY_DEGREES = 38f
        private const val DEMO_VIDEO_URL =
            "https://storage.googleapis.com/exoplayer-test-media-0/BigBuckBunny_320x180.mp4"
    }
}
