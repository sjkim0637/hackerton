package com.hackathon.interior.ar

import android.util.Log
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import com.google.ar.core.Config
import com.google.ar.core.Frame
import com.google.ar.core.HitResult
import com.google.ar.core.Plane
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import com.project.depthplacement.arcore.ArCoreDepthAdapter
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.ar.scene.PlaneRenderer

/**
 * 카메라 실행 · AR 세션 구성 · 벽/바닥 평면 인식을 담당한다.
 *
 * 설계서 "사용자 1 — 공간 / AR" 작업 흐름 중
 * - 카메라 화면 표시
 * - AR 실행 환경 구성
 * - 벽 / 바닥 평면 탐지
 * - 화면 터치 위치 획득(hitTest)
 * 부분을 캡슐화한다. 가구 생성/편집 로직은 [com.hackathon.interior.furniture] 쪽이 담당한다.
 */
class ArSpaceController(
    private val sceneView: ARSceneView,
    lifecycle: Lifecycle,
    private val instruction: TextView,
) {

    /** 매 프레임 호출된다. 이름표 빌보드 갱신 등 프레임 동기 작업에 사용한다. */
    var onFrame: (() -> Unit)? = null

    /** 가장 최근 AR 프레임. 키프레임 캡처 시 카메라 pose/intrinsics 를 뽑는 데 쓴다. */
    var latestFrame: Frame? = null
        private set

    /** 화면에 아무것도 선택/입력 중이 아니면 true. 이때만 안내 문구를 자동 갱신한다. */
    var isIdle: () -> Boolean = { true }

    /** true면 화면 배치 UX는 ARCore 격자 대신 Depth world pose를 사용한다. */
    var usesDepthPlacement: Boolean = false
        private set

    // 상태 로그 스팸 방지용.
    private var lastTrackingState: TrackingState? = null
    private var lastFailureReason: String? = null
    private var lastPlaneCount = -1

    /** TEMP-DIAG: 삭제 완료 뒤에도 onFrame 이 계속 도는지 확인용 하트비트 카운터. */
    private var frameCount = 0L

    init {
        sceneView.lifecycle = lifecycle

        // 인식된 평면 위에 격자(그리드)를 그린다.
        // Session capability를 확인하기 전에는 격자를 숨긴다. Depth 미지원일 때만 아래에서 켠다.
        sceneView.planeRenderer.isEnabled = false
        sceneView.planeRenderer.planeRendererMode = PlaneRenderer.PlaneRendererMode.RENDER_ALL

        sceneView.configureSession { session, config ->
            // 바닥/책상 같은 수평면 + 벽 같은 수직면 모두 인식.
            config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
            // 실내 조명에 맞춰 오브젝트 밝기를 자동 조정 (없으면 Filament 오브젝트가 새까맣게 보임).
            config.lightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR
            // 지원 기기에서는 가구 footprint 검증에 사용할 dense Depth를 함께 활성화한다.
            usesDepthPlacement = ArCoreDepthAdapter.isDepthSupported(session)
            ArCoreDepthAdapter.prepareConfig(session, config)
            sceneView.planeRenderer.isEnabled = !usesDepthPlacement
        }

        sceneView.onSessionFailed = { exception ->
            Log.e(TAG, "AR 세션 실패", exception)
            instruction.text = "AR 세션 실패: ${exception.message}"
        }

        sceneView.onSessionUpdated = { session, frame ->
            latestFrame = frame
            onFrame?.invoke()
            logTrackingState(session, frame)
            // TEMP-DIAG: 약 2초(=120프레임)마다 한 줄. 이게 계속 찍히면 세션/카메라는 살아있다.
            if (++frameCount % 120L == 0L) {
                val planes = session.getAllTrackables(Plane::class.java)
                    .count { it.trackingState == TrackingState.TRACKING }
                Log.d(TAG, "onFrame heartbeat #$frameCount tracking=${frame.camera.trackingState} planes=$planes")
            }
        }
    }

    /** 화면 좌표 (xPx, yPx) 에서 벽/바닥 평면과의 hitTest 결과를 돌려준다. */
    fun hitTest(xPx: Float, yPx: Float): HitResult? =
        sceneView.hitTestAR(xPx = xPx, yPx = yPx, planeTypes = PlaneKind.PLANE_TYPES)

    /**
     * 원하는 평면 종류를 우선해서 hitTest 한다.
     * TV/선반은 벽(수직), 소파/테이블 등은 바닥(수평)에 붙이려고 쓴다.
     * 원하는 종류가 없으면 null을 반환한다. 벽 물체가 바닥에 놓이는 식의 fallback은 하지 않는다.
     */
    fun hitTestPreferring(xPx: Float, yPx: Float, wantVertical: Boolean): HitResult? {
        val preferred: Set<Plane.Type> =
            if (wantVertical) setOf(Plane.Type.VERTICAL)
            else setOf(Plane.Type.HORIZONTAL_UPWARD_FACING, Plane.Type.HORIZONTAL_DOWNWARD_FACING)
        return sceneView.hitTestAR(xPx = xPx, yPx = yPx, planeTypes = preferred)
    }

    /**
     * 평면 격자·특징점 시각화를 켜고 끈다.
     *
     * 키프레임 캡처 직전에 잠깐 꺼서, 서버(AI)로 보내는 이미지에 흰 점/격자가 찍히지 않게 한다.
     * 캡처가 끝나면 다시 켠다. (인식 자체는 계속 동작하고, 화면 표시만 멈춘다.)
     */
    fun setPlaneVisualizationEnabled(enabled: Boolean) {
        runCatching { sceneView.planeRenderer.isEnabled = enabled && !usesDepthPlacement }
    }

    private fun logTrackingState(session: Session, frame: Frame) {
        val trackingState = frame.camera.trackingState
        val failureReason = frame.camera.trackingFailureReason.name

        val planes = session.getAllTrackables(Plane::class.java)
        val trackingPlanes = planes.count { it.trackingState == TrackingState.TRACKING }

        if (trackingState == lastTrackingState &&
            failureReason == lastFailureReason &&
            trackingPlanes == lastPlaneCount
        ) return

        lastTrackingState = trackingState
        lastFailureReason = failureReason
        lastPlaneCount = trackingPlanes
        Log.d(TAG, "tracking=$trackingState failureReason=$failureReason planes=$trackingPlanes/${planes.size}")

        if (isIdle()) {
            instruction.text = when {
                trackingState != TrackingState.TRACKING ->
                    "추적 준비 중 ($failureReason) · 밝은 곳에서 폰을 좌우로 천천히 움직이세요"
                usesDepthPlacement ->
                    "Depth 직접 배치 · 바닥이나 벽을 비추고 원하는 위치를 탭하세요"
                trackingPlanes == 0 ->
                    "평면 찾는 중 · 바닥/책상/벽을 비추며 폰을 움직이세요"
                else ->
                    "평면 $trackingPlanes 개 (바닥·벽) · 탭하면 가구 생성, 길게 누르면 선택"
            }
        }
    }

    private companion object {
        const val TAG = "InteriorAR"
    }
}
