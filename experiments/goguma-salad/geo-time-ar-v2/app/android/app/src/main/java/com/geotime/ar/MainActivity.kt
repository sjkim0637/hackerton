package com.geotime.ar

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.location.GnssMeasurement
import android.location.GnssMeasurementRequest
import android.location.GnssMeasurementsEvent
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.window.OnBackInvokedDispatcher
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.RadioGroup
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
import com.geotime.ar.network.ServerConnectionTester
import com.geotime.ar.network.ServerProfile
import com.geotime.ar.network.ServerSettings
import com.geotime.ar.network.ServerSettingsStore
import com.geotime.ar.time.MomentStack
import com.geotime.ar.time.TimelineMoment
import com.geotime.ar.ui.FlightHudView
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Config
import com.google.ar.core.Session
import java.time.Instant
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

private enum class AppScreen {
    START,
    VIEWER,
    CREATOR,
}

private enum class ViewerUiState {
    LOADING,
    EMPTY,
    OFFLINE,
    PERMISSION,
    SERVER_ERROR,
}

class MainActivity : Activity() {
    private lateinit var root: FrameLayout
    private lateinit var viewerRoot: FrameLayout
    private lateinit var startScreen: View
    private lateinit var creatorScreen: View
    private lateinit var arView: GeoTimeArView
    private lateinit var zoneLabel: TextView
    private lateinit var trackingLabel: TextView
    private lateinit var markerHint: TextView
    private lateinit var coachHint: TextView
    private lateinit var playerOverlay: FrameLayout
    private lateinit var playerView: PlayerView
    private lateinit var promptPanel: LinearLayout
    private lateinit var promptText: TextView
    private lateinit var promptButtonRow: LinearLayout
    private lateinit var playbackDate: TextView
    private lateinit var playbackHint: TextView
    private lateinit var flightHud: FlightHudView
    private lateinit var viewerStatePanel: LinearLayout
    private lateinit var viewerStateImage: ImageView
    private lateinit var viewerStateTitle: TextView
    private lateinit var viewerStateMessage: TextView
    private lateinit var viewerStateProgress: ProgressBar
    private lateinit var viewerStateAction: Button
    private lateinit var player: ExoPlayer

    private lateinit var apiClient: GeoTimeApiClient
    private lateinit var serverSettingsStore: ServerSettingsStore
    private val serverConnectionTester = ServerConnectionTester()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy.MM.dd")
        .withZone(ZoneId.systemDefault())
    private val stacksByMarkerId = mutableMapOf<String, MomentStack>()
    private var arSession: Session? = null
    private var viewerSurfaceResumed = false
    private var installRequested = false
    private var demoPreviewEnabled = true
    private var appScreen = AppScreen.START
    private var experienceMode = ExperienceMode.PHONE
    private var experienceState = ExperienceState.WORLD_SCAN
    private var selectedStack: MomentStack? = null
    private var selectedMomentIndex = 0
    private var touchStartX = 0f
    private var touchStartY = 0f
    private val headGestureRecognizer = HeadGestureRecognizer()
    private var lastHeadPose: HeadPose? = null
    private var hudRollBaselineDegrees: Float? = null
    private var glassContentBaselinePose: HeadPose? = null
    private var glassFocusedMarkerId: String? = null
    private var glassFocusStartedAtMs = 0L
    private var lastDwellSecond = -1
    private var gnssDiagnosticDialog: AlertDialog? = null
    private var gnssDiagnosticText: TextView? = null
    private var gnssEpochCount = 0
    private var gnssValidAdrCount = 0
    private var gnssResetCount = 0
    private var gnssCycleSlipCount = 0
    private val gnssLocationListener = LocationListener { _ ->
        // Raw GNSS 측정을 활성화하기 위한 요청이며 위치 좌표는 사용하거나 저장하지 않는다.
    }
    private val gnssNoSignalTimeout = Runnable {
        if (gnssEpochCount == 0 && gnssDiagnosticText != null) {
            gnssDiagnosticText?.text =
                "GPS는 활성화됐지만 Raw GNSS 신호가 아직 없습니다.\n\n" +
                "창가보다 하늘이 열린 야외에서 30초 정도 기다려 주세요."
        }
    }
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
        serverSettingsStore = ServerSettingsStore(preferences())
        apiClient = GeoTimeApiClient(serverSettingsStore.load().apiBaseUrl)
        buildUi()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT,
            ) { handleSystemBack() }
        }
        player = ExoPlayer.Builder(this).build().also {
            playerView.player = it
            it.repeatMode = Player.REPEAT_MODE_ONE
            it.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (
                        playbackState == Player.STATE_ENDED &&
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
        if (appScreen == AppScreen.VIEWER) resumeViewer()
    }

    override fun onPause() {
        stopGnssDiagnostics()
        player.pause()
        if (viewerSurfaceResumed) {
            arView.onPause()
            arSession?.pause()
            viewerSurfaceResumed = false
        }
        super.onPause()
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        player.release()
        if (arSession != null) arView.detachAllAnchors()
        arSession?.close()
        apiClient.close()
        serverConnectionTester.close()
        super.onDestroy()
    }

    @Deprecated("Android 시스템 뒤로가기 호환")
    override fun onBackPressed() {
        handleSystemBack()
    }

    private fun handleSystemBack() {
        when {
            appScreen == AppScreen.VIEWER && experienceState != ExperienceState.WORLD_SCAN -> exitContent()
            appScreen != AppScreen.START -> showStartScreen()
            else -> finishAfterTransition()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != VIEWER_PERMISSION_REQUEST) return
        if (hasViewerPermissions()) {
            resumeViewer()
        } else {
            showViewerState(
                ViewerUiState.PERMISSION,
                "Camera와 위치 권한이 필요합니다",
                "현실 공간의 Moment를 찾고 표시하려면 Camera와 정밀 위치 권한을 허용해 주세요.",
                "앱 설정 열기",
            ) { openAppSettings() }
        }
    }

    private fun resumeViewer() {
        if (!hasViewerPermissions()) {
            showViewerState(
                ViewerUiState.PERMISSION,
                "공간 접근 권한을 확인해 주세요",
                "Geo-Time AR은 Camera 영상 위에 현재 장소의 시간 기록을 배치합니다.",
                "권한 허용",
            ) { requestViewerPermissions() }
            return
        }
        ensureArSession()
        if (!viewerSurfaceResumed) {
            arView.onResume()
            viewerSurfaceResumed = true
        }
        loadZoneAndMoments()
    }

    private fun requestViewerPermissions() {
        requestPermissions(
            arrayOf(Manifest.permission.CAMERA, Manifest.permission.ACCESS_FINE_LOCATION),
            VIEWER_PERMISSION_REQUEST,
        )
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
                    showViewerState(
                        ViewerUiState.LOADING,
                        "AR 환경을 준비하고 있습니다",
                        "Google Play Services for AR 설치가 끝나면 자동으로 계속됩니다.",
                    )
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
            showViewerState(
                ViewerUiState.SERVER_ERROR,
                "AR을 시작하지 못했습니다",
                error.message ?: "기기의 ARCore 지원 상태를 확인해 주세요.",
                "다시 시도",
            ) { resumeViewer() }
        }
    }

    private fun loadZoneAndMoments() {
        if (!serverSettingsStore.load().profile.usesNetwork) {
            activateLocalDemo()
            return
        }
        showViewerState(
            ViewerUiState.LOADING,
            "현재 장소의 시간을 찾는 중",
            "GPS와 공간 정보를 안전하게 확인하고 있습니다.",
        )
        val location = if (demoPreviewEnabled) null else lastKnownLocation()
        if (!demoPreviewEnabled && location == null) {
            zoneLabel.text = "저장된 위치 기록이 없습니다"
            clearMomentStacks()
            markerHint.text = "위치 권한을 허용하고 GPS를 켠 뒤 다시 시도하세요"
            markerHint.visibility = View.VISIBLE
            coachHint.visibility = View.GONE
            showViewerState(
                ViewerUiState.PERMISSION,
                "현재 위치를 확인할 수 없습니다",
                "GPS를 켜고 잠시 이동한 뒤 다시 시도해 주세요. 임의 좌표는 사용하지 않습니다.",
                "다시 확인",
            ) { loadZoneAndMoments() }
            return
        }
        val latitude = if (demoPreviewEnabled) DEMO_ZONE_LATITUDE else location!!.latitude
        val longitude = if (demoPreviewEnabled) DEMO_ZONE_LONGITUDE else location!!.longitude
        zoneLabel.text = if (demoPreviewEnabled) {
            "Demo Zone: 을지로 타워 107 조회 중…"
        } else {
            "Android 최근 GPS 기록 기준 GeoZone 조회 중…"
        }
        apiClient.loadNearby(latitude, longitude) { result ->
            runOnUiThread {
                result.onSuccess { zone ->
                    if (zone == null) {
                        zoneLabel.text = "주변 GeoZone 없음"
                        clearMomentStacks()
                        showViewerState(
                            ViewerUiState.EMPTY,
                            "주변에 열린 시간 기록이 없습니다",
                            "다른 장소로 이동하거나 Demo 미리보기를 켜서 흐름을 확인해 보세요.",
                            "다시 조회",
                        ) { loadZoneAndMoments() }
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
                            showViewerState(
                                ViewerUiState.EMPTY,
                                "GeoZone에 조금 더 가까이 가세요",
                                "${zone.name}까지 ${zone.distanceM.toInt()}m 남았습니다.",
                                "다시 조회",
                            ) { loadZoneAndMoments() }
                        }
                    }
                }.onFailure {
                    activateLocalDemo(it)
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
                        showViewerState(
                            ViewerUiState.EMPTY,
                            "아직 남겨진 Moment가 없습니다",
                            "이 공간의 첫 번째 시간 기록은 Creator에서 만들 수 있습니다.",
                            "Creator 열기",
                        ) { showCreatorScreen() }
                    } else {
                        markerHint.visibility = View.GONE
                        hideViewerState()
                        showCoach(worldGuideText())
                    }
                }.onFailure {
                    activateLocalDemo(it)
                }
            }
        }
    }

    private fun activateLocalDemo(error: Throwable? = null) {
        val moments = listOf(
            TimelineMoment(
                id = "local-demo-2024",
                title = "을지로의 여름",
                recordedAt = Instant.parse("2024-08-25T06:00:00Z"),
                poiId = "tower-107-demo",
            ),
            TimelineMoment(
                id = "local-demo-2020",
                title = "타워 107의 기억",
                recordedAt = Instant.parse("2020-08-25T06:00:00Z"),
                poiId = "tower-107-demo",
            ),
        )
        val stacks = MomentStack.group(moments)
        stacksByMarkerId.clear()
        stacks.associateByTo(stacksByMarkerId, MomentStack::id)
        arView.updateCandidates(stacks.map(MomentStack::asSpatialCandidate))
        zoneLabel.text = "현재 장소: 을지로 타워 107 · Local Demo"
        markerHint.visibility = View.GONE
        hideViewerState()
        showCoach("DEMO · Server 연결 없이 로컬 시간 기록을 표시합니다")
        trackingLabel.text = error?.let { "Local Demo · ${it.javaClass.simpleName}" } ?: "Local Demo 준비 완료"
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
            glassContentBaselinePose = lastHeadPose
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
            Uri.parse(serverSettingsStore.load().resolveMediaUrl(moment.mediaUrl))
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
        updateViewerHud(pose)
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
        val baseline = glassContentBaselinePose ?: pose.also {
            glassContentBaselinePose = it
        }
        if (HeadGestureRecognizer.hasReachedRollTilt(baseline, pose, GLASS_EXIT_ROLL_DEGREES)) {
            exitContent()
            return
        }
        headGestureRecognizer.update(pose, timestampMs)?.let { motion ->
            if (motion.axis == HeadMotionAxis.YAW) {
                moveContent(if (motion.direction > 0) 1f else -1f)
            }
        }
    }

    private fun updateViewerHud(pose: HeadPose) {
        val baseline = glassContentBaselinePose
        val isGlassFullscreen = experienceMode == ExperienceMode.GLASS_DEMO &&
            experienceState == ExperienceState.FULLSCREEN && baseline != null
        val rollBaseline = hudRollBaselineDegrees ?: pose.rollDegrees.also {
            hudRollBaselineDegrees = it
        }
        runOnUiThread {
            flightHud.setPose(
                heading = pose.yawDegrees,
                pitch = pose.pitchDegrees,
                roll = HeadGestureRecognizer.angleDelta(rollBaseline, pose.rollDegrees),
                showRollExitCue = isGlassFullscreen,
            )
            flightHud.visibility = View.VISIBLE
        }
    }

    private fun toggleExperienceMode() {
        if (experienceState != ExperienceState.WORLD_SCAN) exitContent()
        experienceMode = if (experienceMode == ExperienceMode.PHONE) {
            ExperienceMode.GLASS_DEMO
        } else {
            ExperienceMode.PHONE
        }
        flightHud.visibility = View.VISIBLE
        resetGlassInteraction()
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
        val toolsTitle = TextView(this).apply {
            text = "Viewer 도구"
            setTextColor(COLOR_CYAN)
            textSize = 14f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(8), dp(14), dp(8), dp(6))
        }
        val serverSettings = serverSettingsStore.load()
        val serverTitle = TextView(this).apply {
            text = "Server 연결"
            setTextColor(COLOR_CYAN)
            textSize = 14f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(8), dp(14), dp(8), dp(6))
        }
        val serverSummary = TextView(this).apply {
            text = if (serverSettings.profile.usesNetwork) {
                "${serverSettings.profile.label} · ${serverSettings.apiBaseUrl}"
            } else {
                "Demo · Local Moment · 네트워크 불필요"
            }
            setTextColor(0xFFB7C9D8.toInt())
            textSize = 13f
            setPadding(dp(8), dp(2), dp(8), dp(8))
        }
        val serverControl = actionButton("Profile · 주소 · 연결 테스트") {
            showServerSettingsDialog()
        }
        lateinit var modeControl: Button
        modeControl = actionButton(
            if (experienceMode == ExperienceMode.GLASS_DEMO) "현재 Glass Demo · Phone으로 변경" else "현재 Phone Viewer · Glass Demo로 변경",
        ) {
            toggleExperienceMode()
            modeControl.text = if (experienceMode == ExperienceMode.GLASS_DEMO) {
                "현재 Glass Demo · Phone으로 변경"
            } else {
                "현재 Phone Viewer · Glass Demo로 변경"
            }
        }
        lateinit var demoControl: Button
        demoControl = actionButton(
            if (demoPreviewEnabled) "Demo 미리보기 끄기" else "Demo 미리보기 켜기",
        ) {
            demoPreviewEnabled = !demoPreviewEnabled
            if (appScreen == AppScreen.VIEWER) loadZoneAndMoments()
            demoControl.text = if (demoPreviewEnabled) "Demo 미리보기 끄기" else "Demo 미리보기 켜기"
        }
        val reloadControl = actionButton("현재 장소 다시 조회") {
            if (appScreen == AppScreen.VIEWER) loadZoneAndMoments()
        }
        val gnssControl = actionButton("GNSS 진단 열기") { showGnssDiagnostics() }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(4), dp(16), dp(4))
            addView(guideSwitch, LinearLayout.LayoutParams(-1, -2))
            addView(note, LinearLayout.LayoutParams(-1, -2))
            addView(serverTitle, LinearLayout.LayoutParams(-1, -2))
            addView(serverSummary, LinearLayout.LayoutParams(-1, -2))
            addView(serverControl, LinearLayout.LayoutParams(-1, -2))
            addView(toolsTitle, LinearLayout.LayoutParams(-1, -2))
            addView(modeControl, LinearLayout.LayoutParams(-1, -2))
            addView(demoControl, LinearLayout.LayoutParams(-1, -2))
            addView(reloadControl, LinearLayout.LayoutParams(-1, -2))
            addView(gnssControl, LinearLayout.LayoutParams(-1, -2))
        }
        AlertDialog.Builder(this)
            .setTitle("Geo-Time AR 설정")
            .setView(content)
            .setPositiveButton("완료", null)
            .show()
    }

    private fun showServerSettingsDialog() {
        var selectedSettings = serverSettingsStore.load()
        val profileGroup = RadioGroup(this).apply { orientation = RadioGroup.VERTICAL }
        val profileButtons = ServerProfile.entries.associateWith { profile ->
            RadioButton(this).apply {
                id = View.generateViewId()
                text = "${profile.label}  ·  ${profile.description}"
                textSize = 14f
                isChecked = profile == selectedSettings.profile
                setPadding(dp(4), dp(7), dp(4), dp(7))
                profileGroup.addView(this, RadioGroup.LayoutParams(-1, -2))
            }
        }
        val apiInput = serverUrlInput("API Server 주소")
        val mediaInput = serverUrlInput("Media Server 주소")
        val usbNote = TextView(this).apply {
            text = "USB Profile 의존성\nPC에서 Backend를 실행한 뒤 아래 두 Reverse가 필요합니다.\n" +
                "adb reverse tcp:8000 tcp:8000\n" +
                "adb reverse tcp:9000 tcp:9000"
            setTextColor(COLOR_AMBER)
            textSize = 12f
            setTypeface(Typeface.MONOSPACE)
            setPadding(dp(10), dp(10), dp(10), dp(10))
            background = glassBackground(0xDD241A0D.toInt(), COLOR_AMBER)
        }
        val status = TextView(this).apply {
            text = "저장 전에 연결 테스트를 실행할 수 있습니다."
            setTextColor(0xFFB7C9D8.toInt())
            textSize = 13f
            setPadding(dp(8), dp(12), dp(8), dp(10))
        }

        fun showProfile(settings: ServerSettings) {
            selectedSettings = settings
            apiInput.setText(settings.apiBaseUrl)
            mediaInput.setText(settings.mediaBaseUrl)
            apiInput.isEnabled = settings.profile.usesNetwork
            mediaInput.isEnabled = settings.profile.usesNetwork
            usbNote.visibility = if (settings.profile == ServerProfile.USB) View.VISIBLE else View.GONE
            status.text = if (settings.profile == ServerProfile.DEMO) {
                "Local Demo는 API·Media Server에 연결하지 않습니다."
            } else {
                "${settings.profile.label} Profile · 저장 전 연결 테스트 권장"
            }
            status.setTextColor(0xFFB7C9D8.toInt())
        }

        showProfile(selectedSettings)
        profileGroup.setOnCheckedChangeListener { _, checkedId ->
            val profile = profileButtons.entries.firstOrNull { it.value.id == checkedId }?.key
                ?: return@setOnCheckedChangeListener
            showProfile(serverSettingsStore.load(profile))
        }

        fun currentInput(): ServerSettings = ServerSettings(
            profile = selectedSettings.profile,
            apiBaseUrl = apiInput.text.toString(),
            mediaBaseUrl = mediaInput.text.toString(),
        ).normalized()

        val testButton = cyberButton("연결 테스트", COLOR_CYAN) {
            val candidate = currentInput()
            val error = candidate.validationError()
            if (error != null) {
                status.setTextColor(0xFFFF6B6B.toInt())
                status.text = error
            } else {
                status.setTextColor(COLOR_CYAN)
                status.text = "API와 Media 연결을 확인하는 중…"
                serverConnectionTester.test(candidate) { result ->
                    runOnUiThread {
                        status.setTextColor(if (result.success) COLOR_CYAN else 0xFFFF6B6B.toInt())
                        status.text = buildString {
                            append(if (result.success) "연결 성공" else "연결 실패")
                            append("\nAPI · ").append(result.api.message)
                            append("\nMedia · ").append(result.media.message)
                            if (!result.success && candidate.profile == ServerProfile.USB) {
                                append("\n\nUSB Cable, Docker 실행 상태와 adb reverse를 확인하세요.")
                            }
                        }
                    }
                }
            }
        }
        lateinit var dialog: AlertDialog
        val saveButton = cyberButton("저장하고 적용", COLOR_AMBER) {
            val candidate = currentInput()
            val error = candidate.validationError()
            if (error != null) {
                status.setTextColor(0xFFFF6B6B.toInt())
                status.text = error
            } else {
                serverSettingsStore.save(candidate)
                applyServerSettings(candidate)
                dialog.dismiss()
            }
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(6), dp(16), dp(8))
            addView(profileGroup, LinearLayout.LayoutParams(-1, -2))
            addView(apiInput, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
            addView(mediaInput, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
            addView(usbNote, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
            addView(status, LinearLayout.LayoutParams(-1, -2))
            addView(testButton, LinearLayout.LayoutParams(-1, -2))
            addView(saveButton, LinearLayout.LayoutParams(-1, -2))
        }
        dialog = AlertDialog.Builder(this)
            .setTitle("Server 연결 설정")
            .setView(ScrollView(this).apply { addView(content) })
            .setNegativeButton("닫기", null)
            .create()
        dialog.show()
    }

    private fun serverUrlInput(label: String) = EditText(this).apply {
        hint = label
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        setSingleLine(true)
        textSize = 14f
        setPadding(dp(12), dp(10), dp(12), dp(10))
    }

    private fun applyServerSettings(settings: ServerSettings) {
        apiClient.close()
        apiClient = GeoTimeApiClient(settings.apiBaseUrl)
        if (appScreen == AppScreen.VIEWER) loadZoneAndMoments()
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
        glassContentBaselinePose = null
        headGestureRecognizer.reset(lastHeadPose)
    }

    private fun worldGuideText(): String = if (experienceMode == ExperienceMode.GLASS_DEMO) {
        "GLASS · 마커를 화면 중앙에서 5초간 응시하세요"
    } else {
        "시간 기록 마커를 터치해 미리보세요"
    }

    private fun glassContentGuideText() =
        "GLASS · 빠른 좌우 왕복: 기록 이동 · 좌우 Roll 15°: AR 복귀"

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
                    if (!status.startsWith("6DoF")) {
                        resetGlassDwell()
                        hudRollBaselineDegrees = null
                    }
                }
            }
            onSpatialFrame = { markerId, pose, timestampMs ->
                runOnUiThread { processGlassFrame(markerId, pose, timestampMs) }
            }
            setOnTouchListener { _, event -> handleWorldTouch(event) }
        }
        zoneLabel = overlayText("현재 장소 확인 중").apply {
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(COLOR_CYAN)
        }
        trackingLabel = overlayText("ARCore 준비 중").apply { textSize = 12f }
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

        root = FrameLayout(this).apply { setBackgroundColor(COLOR_BACKGROUND) }
        viewerRoot = FrameLayout(this).apply {
            visibility = View.GONE
            addView(arView, FrameLayout.LayoutParams(-1, -1))
        }

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(12), dp(16), dp(12))
            background = glassBackground(COLOR_SURFACE, COLOR_CYAN)
            addView(compactButton("‹ 홈") { showStartScreen() })
            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(8), 0, 0, 0)
                addView(zoneLabel)
                addView(trackingLabel)
            }, LinearLayout.LayoutParams(0, -2, 1f))
        }
        viewerRoot.addView(top, FrameLayout.LayoutParams(-1, -2, Gravity.TOP).apply {
            marginStart = dp(10)
            marginEnd = dp(10)
            topMargin = dp(10)
        })

        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(8), dp(12), dp(14))
            addView(markerHint)
            addView(coachHint)
        }
        viewerRoot.addView(bottom, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))

        buildViewerStatePanel()
        viewerRoot.addView(
            viewerStatePanel,
            FrameLayout.LayoutParams(-1, -2, Gravity.CENTER).apply {
                marginStart = dp(28)
                marginEnd = dp(28)
            },
        )

        buildPlayerOverlay()
        viewerRoot.addView(playerOverlay, FrameLayout.LayoutParams(-1, -1))
        buildViewerHud()
        root.addView(viewerRoot, FrameLayout.LayoutParams(-1, -1))

        startScreen = buildStartScreen()
        creatorScreen = buildCreatorScreen().apply { visibility = View.GONE }
        root.addView(startScreen, FrameLayout.LayoutParams(-1, -1))
        root.addView(creatorScreen, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)
    }

    private fun buildStartScreen(): View = FrameLayout(this).apply {
        addView(ImageView(this@MainActivity).apply {
            setImageResource(R.drawable.start_background)
            scaleType = ImageView.ScaleType.CENTER_CROP
            contentDescription = null
        }, FrameLayout.LayoutParams(-1, -1))
        addView(View(this@MainActivity).apply { setBackgroundColor(0x42000000) }, FrameLayout.LayoutParams(-1, -1))

        val content = LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(48), dp(22), dp(32))
            addView(ImageView(this@MainActivity).apply {
                setImageResource(R.drawable.brand_app_icon_master)
                scaleType = ImageView.ScaleType.CENTER_CROP
                contentDescription = "Geo-Time AR"
            }, LinearLayout.LayoutParams(dp(72), dp(72)).apply { gravity = Gravity.CENTER_HORIZONTAL })
            addView(titleText("GEO · TIME · AR", 29f).apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(14), 0, 0)
            })
            addView(bodyText("현재 공간에 남아 있는 과거의 신호를 복원합니다.").apply {
                gravity = Gravity.CENTER
                setPadding(dp(12), dp(8), dp(12), dp(20))
            })
            addView(modeCard(
                R.drawable.mode_phone_viewer,
                "PHONE VIEWER",
                "Camera로 주변 Moment를 찾고 터치해 재생합니다.",
                COLOR_CYAN,
            ) { openViewer(ExperienceMode.PHONE) })
            addView(modeCard(
                R.drawable.mode_glass,
                "GLASS DEMO",
                "응시와 Head Gesture 흐름을 Phone에서 체험합니다.",
                COLOR_CYAN,
            ) { openViewer(ExperienceMode.GLASS_DEMO) })
            addView(modeCard(
                R.drawable.mode_creator,
                "CREATOR",
                "지금의 장면을 촬영하고 공간에 남기는 흐름입니다.",
                COLOR_AMBER,
            ) { showCreatorScreen() })
            addView(compactButton("설정 · 연결 상태") { showSettings() }.apply {
                setTextColor(0xFFB7C9D8.toInt())
            }, LinearLayout.LayoutParams(-2, -2).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = dp(10)
            })
        }
        addView(ScrollView(this@MainActivity).apply {
            isFillViewport = true
            addView(content)
        }, FrameLayout.LayoutParams(-1, -1))
    }

    private fun buildCreatorScreen(): View = FrameLayout(this).apply {
        setBackgroundColor(COLOR_BACKGROUND)
        addView(ImageView(this@MainActivity).apply {
            setImageResource(R.drawable.start_background)
            scaleType = ImageView.ScaleType.CENTER_CROP
            alpha = 0.32f
        }, FrameLayout.LayoutParams(-1, -1))
        val content = LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(28), dp(22), dp(30))
            addView(compactButton("‹ 시작 화면") { showStartScreen() })
            addView(titleText("새로운 시간을 남기세요", 27f).apply {
                setPadding(0, dp(24), 0, dp(8))
            })
            addView(bodyText("영상 선택 → 공간 배치 → 업로드의 3단계로 Moment를 만듭니다."))
            addView(ImageView(this@MainActivity).apply {
                setImageResource(R.drawable.creator_record_ring)
                scaleType = ImageView.ScaleType.CENTER_CROP
                contentDescription = "Creator record ring"
                background = glassBackground(COLOR_SURFACE, COLOR_AMBER)
            }, LinearLayout.LayoutParams(-1, dp(230)).apply {
                topMargin = dp(22)
                bottomMargin = dp(18)
            })
            addView(cyberButton("지금 촬영", COLOR_AMBER) { showCreatorPendingMessage() })
            addView(cyberButton("갤러리에서 선택", COLOR_CYAN) { showCreatorPendingMessage() })
            addView(bodyText("01  영상 선택    →    02  공간 배치    →    03  업로드").apply {
                gravity = Gravity.CENTER
                setTextColor(0xFFB8C7D4.toInt())
                setPadding(0, dp(20), 0, 0)
            })
            addView(bodyText("화면 구조는 준비되었습니다. 촬영·Gallery·Upload 기능은 Product Backlog의 P1 Creator Mode에서 연결합니다.").apply {
                textSize = 12f
                setTextColor(0xFF8598A8.toInt())
                gravity = Gravity.CENTER
                setPadding(dp(16), dp(14), dp(16), 0)
            })
        }
        addView(ScrollView(this@MainActivity).apply { addView(content) }, FrameLayout.LayoutParams(-1, -1))
    }

    private fun buildViewerStatePanel() {
        viewerStateImage = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            contentDescription = null
        }
        viewerStateProgress = ProgressBar(this).apply { isIndeterminate = true }
        viewerStateTitle = titleText("", 20f).apply { gravity = Gravity.CENTER }
        viewerStateMessage = bodyText("").apply { gravity = Gravity.CENTER }
        viewerStateAction = cyberButton("다시 시도", COLOR_CYAN) {}
        viewerStatePanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(22), dp(22), dp(22), dp(20))
            background = glassBackground(0xF2050811.toInt(), COLOR_CYAN)
            addView(viewerStateImage, LinearLayout.LayoutParams(dp(132), dp(112)))
            addView(viewerStateProgress, LinearLayout.LayoutParams(dp(42), dp(42)).apply {
                topMargin = dp(4)
                bottomMargin = dp(12)
            })
            addView(viewerStateTitle, LinearLayout.LayoutParams(-1, -2))
            addView(viewerStateMessage, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
            addView(viewerStateAction, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })
            visibility = View.GONE
        }
    }

    private fun openViewer(mode: ExperienceMode) {
        appScreen = AppScreen.VIEWER
        experienceMode = mode
        experienceState = ExperienceState.WORLD_SCAN
        hudRollBaselineDegrees = null
        startScreen.visibility = View.GONE
        creatorScreen.visibility = View.GONE
        viewerRoot.visibility = View.VISIBLE
        flightHud.visibility = View.VISIBLE
        showViewerState(
            ViewerUiState.LOADING,
            if (mode == ExperienceMode.GLASS_DEMO) "Glass Demo 준비 중" else "Phone Viewer 준비 중",
            "Camera와 현재 장소의 공간 정보를 연결하고 있습니다.",
        )
        resumeViewer()
    }

    private fun showStartScreen() {
        val leavingViewer = appScreen == AppScreen.VIEWER
        if (experienceState != ExperienceState.WORLD_SCAN) exitContent()
        if (leavingViewer && viewerSurfaceResumed) {
            arView.onPause()
            arSession?.pause()
            viewerSurfaceResumed = false
        }
        if (leavingViewer) {
            clearMomentStacks()
        }
        stopGnssDiagnostics()
        appScreen = AppScreen.START
        viewerRoot.visibility = View.GONE
        creatorScreen.visibility = View.GONE
        startScreen.visibility = View.VISIBLE
        flightHud.visibility = View.GONE
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
    }

    private fun showCreatorScreen() {
        val leavingViewer = appScreen == AppScreen.VIEWER
        if (experienceState != ExperienceState.WORLD_SCAN) exitContent()
        if (leavingViewer && viewerSurfaceResumed) {
            arView.onPause()
            arSession?.pause()
            viewerSurfaceResumed = false
        }
        if (leavingViewer) {
            clearMomentStacks()
        }
        appScreen = AppScreen.CREATOR
        viewerRoot.visibility = View.GONE
        startScreen.visibility = View.GONE
        creatorScreen.visibility = View.VISIBLE
    }

    private fun showCreatorPendingMessage() {
        AlertDialog.Builder(this)
            .setTitle("Creator 기능 연결 예정")
            .setMessage("화면 Flow는 준비되었습니다. Camera 촬영, Gallery 선택과 Upload는 P1 Creator Mode 구현에서 연결합니다.")
            .setPositiveButton("확인", null)
            .show()
    }

    private fun showViewerState(
        state: ViewerUiState,
        title: String,
        message: String,
        actionLabel: String? = null,
        action: (() -> Unit)? = null,
    ) {
        viewerStateTitle.text = title
        viewerStateMessage.text = message
        viewerStateProgress.visibility = if (state == ViewerUiState.LOADING) View.VISIBLE else View.GONE
        viewerStateImage.visibility = if (state == ViewerUiState.LOADING) View.GONE else View.VISIBLE
        when (state) {
            ViewerUiState.EMPTY -> viewerStateImage.setImageResource(R.drawable.state_empty_moment)
            ViewerUiState.PERMISSION -> viewerStateImage.setImageResource(R.drawable.state_permission_privacy)
            ViewerUiState.OFFLINE, ViewerUiState.SERVER_ERROR -> {
                viewerStateImage.setImageResource(R.drawable.state_offline_server)
            }
            ViewerUiState.LOADING -> Unit
        }
        viewerStateAction.visibility = if (actionLabel != null && action != null) View.VISIBLE else View.GONE
        if (actionLabel != null && action != null) {
            viewerStateAction.text = actionLabel
            viewerStateAction.setOnClickListener { action() }
        } else {
            viewerStateAction.setOnClickListener(null)
        }
        viewerStatePanel.visibility = View.VISIBLE
    }

    private fun hideViewerState() {
        viewerStatePanel.visibility = View.GONE
    }

    private fun openAppSettings() {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        })
    }

    private fun modeCard(
        imageRes: Int,
        title: String,
        description: String,
        accent: Int,
        action: () -> Unit,
    ): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(10), dp(10), dp(14), dp(10))
        background = glassBackground(0xE0101827.toInt(), accent)
        isClickable = true
        isFocusable = true
        setOnClickListener { action() }
        addView(ImageView(this@MainActivity).apply {
            setImageResource(imageRes)
            scaleType = ImageView.ScaleType.CENTER_CROP
            contentDescription = title
        }, LinearLayout.LayoutParams(dp(92), dp(92)))
        addView(LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), 0, 0, 0)
            addView(titleText(title, 16f).apply { setTextColor(accent) })
            addView(bodyText(description).apply {
                textSize = 13f
                setPadding(0, dp(5), 0, 0)
            })
        }, LinearLayout.LayoutParams(0, -2, 1f))
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) }
    }

    private fun cyberButton(label: String, accent: Int, action: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 15f
        setTextColor(accent)
        background = glassBackground(0xE0101827.toInt(), accent)
        setPadding(dp(16), dp(10), dp(16), dp(10))
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) }
    }

    private fun compactButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 12f
        minHeight = 0
        minimumHeight = 0
        setTextColor(COLOR_CYAN)
        setPadding(dp(10), dp(6), dp(10), dp(6))
        background = glassBackground(0xCC101827.toInt(), 0x5538D9FF)
        setOnClickListener { action() }
    }

    private fun titleText(value: String, size: Float) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(Color.WHITE)
        setTypeface(typeface, Typeface.BOLD)
    }

    private fun bodyText(value: String) = TextView(this).apply {
        text = value
        textSize = 14f
        setTextColor(0xFFD7E5EF.toInt())
        setLineSpacing(0f, 1.15f)
    }

    private fun glassBackground(fill: Int, border: Int) = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = dp(20).toFloat()
        setStroke(dp(1), border)
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
            text = "Full Tracking으로 위성 신호를 기다리는 중…\n\n가능하면 야외에서 하늘이 잘 보이도록 기기를 유지하세요."
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
            val callbackRegistered = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                manager.registerGnssMeasurementsCallback(
                    GnssMeasurementRequest.Builder()
                        .setFullTracking(true)
                        .build(),
                    mainExecutor,
                    gnssMeasurementsCallback,
                )
            } else {
                @Suppress("DEPRECATION")
                manager.registerGnssMeasurementsCallback(gnssMeasurementsCallback, mainHandler)
            }
            manager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                1_000L,
                0f,
                gnssLocationListener,
                Looper.getMainLooper(),
            )
            callbackRegistered
        }.getOrDefault(false)
        if (!registered) {
            gnssDiagnosticText?.text = "GNSS 원시 측정을 시작하지 못했습니다.\nGPS 설정과 기기 지원 여부를 확인하세요."
        } else {
            mainHandler.postDelayed(gnssNoSignalTimeout, GNSS_SIGNAL_TIMEOUT_MS)
        }
    }

    private fun updateGnssDiagnostics(measurements: Collection<GnssMeasurement>) {
        mainHandler.removeCallbacks(gnssNoSignalTimeout)
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
        mainHandler.removeCallbacks(gnssNoSignalTimeout)
        runCatching {
            getSystemService(LocationManager::class.java).also { manager ->
                manager.unregisterGnssMeasurementsCallback(gnssMeasurementsCallback)
                manager.removeUpdates(gnssLocationListener)
            }
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

    private fun buildViewerHud() {
        flightHud = FlightHudView(this).apply {
            visibility = View.GONE
            isClickable = false
        }
        viewerRoot.addView(flightHud, FrameLayout.LayoutParams(-1, -1))
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

    private fun hasViewerPermissions() =
        checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val VIEWER_PERMISSION_REQUEST = 10
        private const val PREVIEW_DURATION_MS = 5_000L
        private const val GLASS_DWELL_MS = 5_000L
        private const val GLASS_EXIT_ROLL_DEGREES = 15f
        private const val DEMO_ZONE_LATITUDE = 37.5648801960179
        private const val DEMO_ZONE_LONGITUDE = 126.991228638001
        private const val GNSS_SIGNAL_TIMEOUT_MS = 10_000L
        private const val PREFERENCES_NAME = "geo_time_ar_settings"
        private const val PREF_SHOW_GUIDES = "show_guides"
        private const val COLOR_BACKGROUND = 0xFF050811.toInt()
        private const val COLOR_SURFACE = 0xDD101827.toInt()
        private const val COLOR_CYAN = 0xFF38D9FF.toInt()
        private const val COLOR_AMBER = 0xFFFFB547.toInt()
        private const val DEMO_VIDEO_URL =
            "https://storage.googleapis.com/exoplayer-test-media-0/BigBuckBunny_320x180.mp4"
    }
}
