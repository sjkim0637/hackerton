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
 *
 * D6(`docs/decisions.md`): 기존엔 순수 Plane 추적만 써서, 사용자가 폰을 좌우로 움직여
 * 특징점을 충분히 모으기 전까지는 아무 곳도 hitTest 되지 않았다. `Config.DepthMode.AUTOMATIC`
 * 과 `Config.InstantPlacementMode.LOCAL_Y_UP` 을 추가해 두 단계로 즉시성을 높인다:
 * - Depth API 는 기기에 실제 ToF/IR Depth 센서가 있으면 그 하드웨어 깊이 값을 쓰고, 없으면
 *   ARCore 의 Motion Stereo(Depth-from-Motion) 로 대체된다 — "IR ToF" 는 지원 기기에서만
 *   실제로 동작하고, 그 외 기기는 여전히 약간의 시차(움직임)가 필요하다(완전히 없앨 수는 없음).
 * - Instant Placement 는 하드웨어와 무관하게 Plane 이 아직 없어도 즉시 대략적인 위치에
 *   배치를 허용하고, 이후 실제 Plane/Depth 가 잡히면 자동으로 자리를 다듬는다. 이게
 *   "폰을 막 돌려야 하는" 체감을 실제로 없애는 부분이다.
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

    /** 이 기기가 Depth API(ToF/IR 센서 또는 Depth-from-Motion)를 지원하는지. `configureSession`에서 채워진다. */
    var depthSupported: Boolean = false
        private set

    // 상태 로그 스팸 방지용.
    private var lastTrackingState: TrackingState? = null
    private var lastFailureReason: String? = null
    private var lastPlaneCount = -1

    init {
        sceneView.lifecycle = lifecycle

        // 인식된 평면 위에 격자(그리드)를 그린다. 평소엔 꺼 둔다(D5/D7: 화면 탭 = 바로 삭제라
        // 격자가 늘 보일 필요가 없어졌다) — "가구 추가" 모드에 들어갈 때만 켠다
        // (MainActivity 의 CatalogController onOpen/onClose 참고, docs/handoffs/
        // interior-removal-fallback.md 5번 요청과 같은 방향).
        sceneView.planeRenderer.isEnabled = false
        sceneView.planeRenderer.planeRendererMode = PlaneRenderer.PlaneRendererMode.RENDER_ALL

        sceneView.configureSession { session, config ->
            // 바닥/책상 같은 수평면 + 벽 같은 수직면 모두 인식.
            config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
            // 실내 조명에 맞춰 오브젝트 밝기를 자동 조정 (없으면 Filament 오브젝트가 새까맣게 보임).
            config.lightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR

            // D6: 기기에 ToF/IR Depth 센서가 있으면 그 값을, 없으면 Depth-from-Motion 을 쓴다.
            // 지원 여부를 반드시 먼저 확인해야 한다 — 미지원 기기에 그냥 설정하면 세션 설정이
            // 실패한다. `depthSupported` 는 hitTest 에서 depthPoint 를 켤지 판단하는 데도 쓴다.
            depthSupported = session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)
            config.depthMode =
                if (depthSupported) Config.DepthMode.AUTOMATIC else Config.DepthMode.DISABLED
            Log.d(TAG, "Depth API 지원: $depthSupported")

            // D6: Plane 이 아직 안 잡혀도 대략적인 위치에 즉시 배치를 허용한다(하드웨어 무관).
            // 이후 실제 Plane/Depth 가 추적되면 ARCore 가 자동으로 위치를 다듬는다.
            config.instantPlacementMode = Config.InstantPlacementMode.LOCAL_Y_UP
        }

        sceneView.onSessionFailed = { exception ->
            Log.e(TAG, "AR 세션 실패", exception)
            instruction.text = "AR 세션 실패: ${exception.message}"
        }

        sceneView.onSessionUpdated = { session, frame ->
            latestFrame = frame
            onFrame?.invoke()
            logTrackingState(session, frame)
        }
    }

    /**
     * 화면 좌표 (xPx, yPx) 에서 hitTest 한다. Plane 을 우선하되, D6 부터는 Depth 포인트와
     * Instant Placement 포인트도 함께 후보로 받아 Plane 이 아직 없어도 즉시 결과를 준다
     * (SceneView `hitTestAR` 는 내부적으로 Plane → 그 외 순으로 가장 적절한 결과 하나를 고른다).
     */
    fun hitTest(xPx: Float, yPx: Float): HitResult? =
        sceneView.hitTestAR(
            xPx = xPx, yPx = yPx, planeTypes = PlaneKind.PLANE_TYPES,
            depthPoint = depthSupported, instantPlacementPoint = true,
        )

    /**
     * 원하는 평면 종류를 우선해서 hitTest 한다.
     * TV/선반은 벽(수직), 소파/테이블 등은 바닥(수평)에 붙이려고 쓴다.
     * 원하는 종류가 없으면 아무 평면이나, 그마저 없으면 Depth/Instant Placement 포인트(D6),
     * 그것도 없으면 null 을 돌려준다.
     */
    fun hitTestPreferring(xPx: Float, yPx: Float, wantVertical: Boolean): HitResult? {
        val preferred: Set<Plane.Type> =
            if (wantVertical) setOf(Plane.Type.VERTICAL)
            else setOf(Plane.Type.HORIZONTAL_UPWARD_FACING, Plane.Type.HORIZONTAL_DOWNWARD_FACING)
        return sceneView.hitTestAR(xPx = xPx, yPx = yPx, planeTypes = preferred)
            ?: sceneView.hitTestAR(
                xPx = xPx, yPx = yPx, planeTypes = PlaneKind.PLANE_TYPES,
                depthPoint = depthSupported, instantPlacementPoint = true,
            )
    }

    /**
     * 평면 격자·특징점 시각화를 켜고 끈다.
     *
     * 키프레임 캡처 직전에 잠깐 꺼서, 서버(AI)로 보내는 이미지에 흰 점/격자가 찍히지 않게 한다.
     * 캡처가 끝나면 다시 켠다. (인식 자체는 계속 동작하고, 화면 표시만 멈춘다.)
     */
    fun setPlaneVisualizationEnabled(enabled: Boolean) {
        runCatching { sceneView.planeRenderer.isEnabled = enabled }
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
            // D6: Instant Placement 덕분에 Plane 이 0개여도 탭하면 바로 배치된다(위치는
            // Plane/Depth 가 잡히면 자동으로 다듬어짐) — 더 이상 "평면부터 찾아야" 안내하지 않는다.
            instruction.text = when {
                trackingState != TrackingState.TRACKING ->
                    "추적 준비 중 ($failureReason) · 밝은 곳에서 폰을 천천히 움직이세요"
                trackingPlanes == 0 ->
                    "탭하면 바로 가구를 놓을 수 있어요 (평면 인식 중 · 자동으로 위치가 맞춰져요)"
                else ->
                    "평면 $trackingPlanes 개 (바닥·벽) · 탭하면 가구 생성, 길게 누르면 선택"
            }
        }
    }

    private companion object {
        const val TAG = "InteriorAR"
    }
}
