package com.geotime.ar

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import com.geotime.ar.ar.GeoTimeArView
import com.geotime.ar.network.GeoTimeApiClient
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Config
import com.google.ar.core.Session
import java.time.Instant

class MainActivity : Activity() {
    private lateinit var arView: GeoTimeArView
    private lateinit var zoneLabel: TextView
    private lateinit var trackingLabel: TextView
    private lateinit var timeLabel: TextView
    private val apiClient = GeoTimeApiClient(BuildConfig.API_BASE_URL)
    private var arSession: Session? = null
    private var installRequested = false
    private var currentZoneId: String? = null
    private var selectedYear = 2026

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
        loadZoneAndCandidates()
    }

    override fun onPause() {
        arView.onPause()
        arSession?.pause()
        super.onPause()
    }

    override fun onDestroy() {
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

    private fun loadZoneAndCandidates() {
        val location = lastKnownLocation()
        val latitude = location?.latitude ?: 37.5665
        val longitude = location?.longitude ?: 126.9780
        zoneLabel.text = "GeoZone 조회 중…"
        apiClient.loadNearby(latitude, longitude) { result ->
            runOnUiThread {
                result.onSuccess { zone ->
                    if (zone == null) {
                        zoneLabel.text = "주변 GeoZone 없음"
                        currentZoneId = null
                    } else {
                        currentZoneId = zone.id
                        zoneLabel.text = "현재 장소: ${zone.name} · ${zone.distanceM.toInt()}m"
                        loadCandidates(zone.id)
                    }
                }.onFailure { zoneLabel.text = "Backend 연결 실패: ${it.message}" }
            }
        }
    }

    private fun loadCandidates(zoneId: String? = currentZoneId) {
        val resolvedZoneId = zoneId ?: return
        val at = Instant.parse("$selectedYear-08-21T05:00:00Z")
        apiClient.loadCandidates(resolvedZoneId, at) { result ->
            runOnUiThread {
                result.onSuccess { candidates ->
                    arView.updateCandidates(candidates)
                    timeLabel.text = "$selectedYear 시간 레이어 · 후보 ${candidates.size}개"
                }.onFailure { timeLabel.text = "콘텐츠 조회 실패: ${it.message}" }
            }
        }
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
        }
        zoneLabel = overlayText("현재 장소 확인 중")
        trackingLabel = overlayText("ARCore 준비 중")
        timeLabel = overlayText("2026 시간 레이어")

        val root = FrameLayout(this)
        root.addView(arView, FrameLayout.LayoutParams(-1, -1))

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(12))
            setBackgroundColor(0xB3111827.toInt())
            addView(zoneLabel)
            addView(trackingLabel)
        }
        root.addView(
            top,
            FrameLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP),
        )

        val slider = SeekBar(this).apply {
            max = 4
            progress = 4
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    selectedYear = 2022 + progress
                    timeLabel.text = "$selectedYear 시간 레이어"
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = loadCandidates()
            })
        }
        val reload = Button(this).apply {
            text = "위치·콘텐츠 다시 조회"
            setOnClickListener { loadZoneAndCandidates() }
        }
        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(16))
            setBackgroundColor(0xB3111827.toInt())
            addView(timeLabel)
            addView(slider)
            addView(reload)
        }
        root.addView(
            bottom,
            FrameLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM),
        )
        setContentView(root)
    }

    private fun overlayText(value: String) = TextView(this).apply {
        text = value
        setTextColor(Color.WHITE)
        textSize = 16f
        setPadding(0, dp(3), 0, dp(3))
    }

    private fun hasCameraPermission() =
        checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
