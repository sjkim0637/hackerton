package com.hackathon.interior.ar

import android.os.Handler
import android.os.Looper
import com.google.ar.core.Frame
import com.google.ar.core.Pose
import com.project.depthplacement.DepthPlacementEngineFactory
import com.project.depthplacement.PlacementFailureReason
import com.project.depthplacement.PlacementObjectSize
import com.project.depthplacement.PlacementTarget
import com.project.depthplacement.arcore.ArCoreDepthAdapter
import io.github.sceneview.math.Size
import java.util.concurrent.Executors

data class DepthPlacementDecision(
    val depthAvailable: Boolean,
    val accepted: Boolean,
    val pose: Pose? = null,
    val isVertical: Boolean? = null,
    val message: String? = null,
)

/**
 * 독립 Depth 모듈을 제품 AR 화면에 연결한다.
 *
 * Frame에서 Depth를 복사하는 짧은 작업만 render callback에서 하고, point cloud 생성과
 * footprint 판정은 worker에서 수행한다. Depth가 아직 준비되지 않았거나 미지원이면 기존
 * ARCore plane 배치를 허용하고, 실제 Depth 결과가 있을 때만 품질 gate로 사용한다.
 */
class DepthPlacementController {
    private val engine = DepthPlacementEngineFactory.create().also { it.start() }
    private val frameExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastCaptureNanos = 0L

    fun onFrame(frame: Frame) {
        val now = frame.timestamp
        if (now - lastCaptureNanos < FRAME_INTERVAL_NANOS) return
        val copied = runCatching { ArCoreDepthAdapter.convert(frame) }.getOrNull() ?: return
        lastCaptureNanos = now
        frameExecutor.execute { engine.updateDepthFrame(copied.input) }
    }

    fun validate(
        frame: Frame?,
        viewX: Float,
        viewY: Float,
        size: Size,
        wantWall: Boolean?,
        callback: (DepthPlacementDecision) -> Unit,
    ) {
        val snapshot = engine.getLatestPointCloud()
        if (frame == null || snapshot == null) {
            callback(DepthPlacementDecision(depthAvailable = false, accepted = true))
            return
        }
        val depthPoint = runCatching {
            ArCoreDepthAdapter.viewToDepth(
                frame, viewX, viewY, snapshot.sourceWidth, snapshot.sourceHeight,
            )
        }.getOrNull()
        if (depthPoint == null) {
            callback(DepthPlacementDecision(depthAvailable = false, accepted = true))
            return
        }
        if (wantWall == null) {
            evaluate(depthPoint, size, wantWall = false) { floor ->
                if (floor.accepted) callback(floor)
                else evaluate(depthPoint, size, wantWall = true) { wall ->
                    callback(if (wall.accepted) wall else floor)
                }
            }
        } else {
            evaluate(depthPoint, size, wantWall, callback)
        }
    }

    private fun evaluate(
        depthPoint: Pair<Float, Float>,
        size: Size,
        wantWall: Boolean,
        callback: (DepthPlacementDecision) -> Unit,
    ) {
        val objectSize = if (wantWall) {
            PlacementObjectSize(size.x, size.y, size.z)
        } else {
            PlacementObjectSize(size.x, size.z, size.y)
        }
        engine.evaluatePlacementAsync(
            screenX = depthPoint.first,
            screenY = depthPoint.second,
            objectSize = objectSize,
            target = if (wantWall) PlacementTarget.WALL else PlacementTarget.HORIZONTAL,
        ) { result ->
            mainHandler.post {
                val corePose = result.pose
                callback(
                    DepthPlacementDecision(
                        depthAvailable = true,
                        accepted = result.isValid,
                        pose = corePose?.let {
                            Pose(
                                floatArrayOf(it.position.x, it.position.y, it.position.z),
                                it.rotation.copyOf(),
                            )
                        },
                        isVertical = wantWall,
                        message = if (result.isValid) null else failureMessage(result.failureReason),
                    ),
                )
            }
        }
    }

    fun release() {
        frameExecutor.shutdownNow()
        engine.release()
    }

    private fun failureMessage(reason: PlacementFailureReason?): String = when (reason) {
        PlacementFailureReason.WRONG_SURFACE -> "선택한 가구와 맞는 벽/바닥을 탭하세요"
        PlacementFailureReason.SURFACE_TOO_STEEP -> "기울기가 큰 곳에는 안정적으로 놓을 수 없어요"
        PlacementFailureReason.INSUFFICIENT_SURFACE -> "가구 크기만큼 평평한 공간이 부족해요"
        PlacementFailureReason.OBSTACLE_DETECTED -> "놓을 자리에 다른 물체가 있어요"
        PlacementFailureReason.OUTSIDE_DEPTH_IMAGE -> "화면 안쪽의 표면을 다시 탭하세요"
        PlacementFailureReason.INSUFFICIENT_POINTS -> "표면을 천천히 비춰 Depth를 더 모아 주세요"
        PlacementFailureReason.NO_DEPTH_FRAME, null -> "Depth 준비 중입니다. 잠시 후 다시 시도하세요"
    }

    private companion object {
        const val FRAME_INTERVAL_NANOS = 66_000_000L // 최대 약 15 FPS
    }
}
