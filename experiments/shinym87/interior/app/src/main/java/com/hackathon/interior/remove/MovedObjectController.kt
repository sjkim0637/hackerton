package com.hackathon.interior.remove

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import com.google.ar.core.Anchor
import com.google.ar.core.Plane
import com.google.ar.core.Pose
import com.hackathon.interior.ar.ArSpaceController
import com.hackathon.interior.databinding.ActivityMainBinding
import com.hackathon.interior.furniture.LabelRenderer
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.ar.arcore.createAnchorOrNull
import io.github.sceneview.ar.node.AnchorNode
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Scale
import io.github.sceneview.math.Size
import io.github.sceneview.node.ImageNode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.sqrt

/**
 * PHASE 4/5 사용자 1 — 삭제한 사물을 **바로 드래그해서 다른 위치로 이동**, 서버에 저장/복원.
 *
 * 삭제가 끝나면 [arm] 이 곧바로 삭제된 자리 근처에 **사물 마커**(캡처 이미지 quad, 터치하기
 * 쉽게 조금 크게)를 띄운다. 그 마커를 손가락으로 끌면 실시간 hitTest 로 따라오고, 떼는 순간
 * ([onDragEnd]) 그 자리에 최종 배치된다("여기로 옮기기" 버튼/탭 단계 없음).
 *
 * 배치/드래그/회전/크기 변경이 잦아들면(디바운스) `POST /scenes/{id}/placements` 로 저장한다.
 * "원위치" 는 삭제된 자리로 되돌리고, "서버 배치 복원" 은 `GET .../placements` 로,
 * "실행 취소" 는 `POST .../placements/undo` 로 처리한다.
 */
class MovedObjectController(
    private val activity: Activity,
    private val sceneView: ARSceneView,
    private val space: ArSpaceController,
    private val binding: ActivityMainBinding,
    private val scope: CoroutineScope,
    /** 현재 서버 주소(부수효과 없이). */
    private val serverBaseUrl: () -> String,
    /** 지금 큐브가 선택돼 있으면 제스처는 큐브 몫 — 이동된 사물은 손대지 않는다. */
    private val furnitureHasSelection: () -> Boolean,
    private val status: (String) -> Unit,
    /** "서버 배치 복원" 을 누를 때 함께 호출 — 카탈로그 가구 복원 등. */
    private val onAlsoRestore: () -> Unit = {},
) {

    private val prefs = activity.getSharedPreferences("interior", Context.MODE_PRIVATE)
    private val handler = Handler(Looper.getMainLooper())
    private val saveDebounce = Runnable { savePlacementNow() }

    private var armed = false
    private var dragging = false
    /** 마커를 아직 못 띄운 상태(평면 미인식). onFrame 에서 계속 재시도한다. */
    private var awaitingPlane = false

    /**
     * 드래그 스무딩(EMA). `onDrag` 는 hitTest 목표만 갱신하고, 실제 노드 pose 는 `onFrame`
     * 의 [stepDragSmoothing] 이 목표로 부드럽게 따라가게 한다(팔딱거림 방지 —
     * [RemovalController] PHASE 3 anchor 스무딩과 동일 방식). 손을 뗀 뒤에도 [settleFramesLeft]
     * 프레임 동안 계속 보간해 최종 위치에 부드럽게 안착시킨 뒤 앵커를 고정한다.
     */
    private var dragTargetPos: FloatArray? = null
    private var dragTargetQuat: FloatArray? = null
    private var smoothPos: FloatArray? = null
    private var smoothQuat: FloatArray? = null
    private var settleFramesLeft = 0

    private var currentSceneId: String? = null
    private var currentJobId: String? = null
    private var sourceRect: FloatArray? = null       // 원래 제거 bbox [x, y, w, h] (재정합 기준)

    private var objectType = "other"
    private var objectBitmap: Bitmap? = null
    private var originalPose: Pose? = null           // 삭제 시점의 원래 위치
    private var baseW = 0.6f
    private var baseH = 0.6f
    private var scaleF = 1f
    private var rotDeg = 0f
    /** 상하(pitch) 기울임 각도. 좌우(rotDeg)와 함께 십자 다이얼로 조절. 세션 로컬(서버 저장 안 함). */
    private var tiltDeg = 0f

    private var node: AnchorNode? = null
    private var imageNode: ImageNode? = null
    private var labelNode: ImageNode? = null
    private var onVertical = false

    /** TEMP-DIAG(A/B): 마커 생성 시점 카메라 pose + onFrame 로그 스로틀. */
    private var markerCamPoseAtCreate: Pose? = null
    private var frameLog = 0

    init {
        binding.btnMovedHome.setOnClickListener { placeAtOriginal() }
        binding.btnMovedRestore.setOnClickListener { restoreFromServer() }
        binding.btnMovedUndo.setOnClickListener { undoOnServer() }
        binding.btnMovedShrink.setOnClickListener { bump(1f / SCALE_STEP) }
        binding.btnMovedGrow.setOnClickListener { bump(SCALE_STEP) }
        binding.btnMovedRotateLeft.setOnClickListener { rotate(-15f) }
        binding.btnMovedRotateRight.setOnClickListener { rotate(15f) }
        binding.btnMovedTiltUp.setOnClickListener { tilt(-15f) }
        binding.btnMovedTiltDown.setOnClickListener { tilt(15f) }
        binding.btnMovedAlign.setOnClickListener { alignVerticalNow() }
        binding.btnMovedClear.setOnClickListener { clearMovedNode(); status("이동한 사물을 치웠습니다") }

        // 지난 세션에 저장된 배치가 있으면, 복원/취소만 가능한 상태로 패널을 연다.
        currentSceneId = prefs.getString(KEY_LAST_SCENE, null)
        currentJobId = prefs.getString(KEY_LAST_JOB, null)
        if (currentSceneId != null) {
            binding.movedObjectPanel.visibility = View.VISIBLE
            enableButtons(home = false, adjust = false, restore = true, undo = true, clear = false)
            status("이전 세션 배치가 있습니다 · 평면 인식 후 '서버 배치 복원'을 누르세요")
        }
    }

    private fun label(): String = OBJECT_LABELS[objectType] ?: "사물"
    private fun wantsWall(): Boolean = objectType == "tv" || objectType == "shelf"

    // ------------------------------------------------------------- arm / disarm

    /** 삭제 완료 시점에 호출. 이동할 사물을 기억하고 **바로 드래그 가능한 마커**를 띄운다. */
    fun arm(
        sceneId: String,
        jobId: String?,
        objectType: String,
        bitmap: Bitmap?,
        originalPose: Pose?,
        sourceRect: FloatArray?,
        widthM: Float,
        heightM: Float,
    ) {
        clearMovedNode()
        currentSceneId = sceneId
        currentJobId = jobId
        this.sourceRect = sourceRect?.copyOf()
        this.objectType = objectType
        this.objectBitmap = (bitmap ?: placeholderBitmap()).let { EdgeFade.feather(downscale(it)) }
        this.originalPose = originalPose
        // 실제 크기: 삭제 당시 hitTest 로 잰 미터값을 그대로 쓴다. quad 는 월드 미터 단위라
        // 새 위치의 카메라 거리 차이는 원근으로 자동 반영된다(별도 거리 보정 불필요).
        //  - 폭(widthM)을 기준으로 잡는다: 평면 위 수평 hitTest 라 세로보다 안정적.
        //  - 세로는 크롭 이미지 종횡비로 유도해, quad 비율이 이미지와 어긋나 늘어나지 않게 한다.
        //  - 작은 소품(텀블러/컵)까지 살릴 수 있게 하한을 0.05m 로 낮춘다(기존 0.15m).
        //  - MOVED_SCALE_CORRECTION: 원근 때문에 폭이 약간 크게 측정되는 잔차용 임시 노브.
        val cropBmp = this.objectBitmap
        this.baseW = (widthM * MOVED_SCALE_CORRECTION).coerceIn(0.05f, 3f)
        this.baseH = if (cropBmp != null && cropBmp.width > 0) {
            (this.baseW * cropBmp.height.toFloat() / cropBmp.width.toFloat()).coerceIn(0.05f, 3f)
        } else {
            (heightM * MOVED_SCALE_CORRECTION).coerceIn(0.05f, 3f)
        }
        Log.d(
            TAG,
            "arm size: 측정 W=%.3f H=%.3f m → baseW=%.3f baseH=%.3f (crop=%dx%d corr=%.2f)".format(
                widthM, heightM, this.baseW, this.baseH,
                cropBmp?.width ?: 0, cropBmp?.height ?: 0, MOVED_SCALE_CORRECTION,
            ),
        )
        this.scaleF = 1f
        this.rotDeg = 0f
        this.tiltDeg = 0f
        armed = true
        awaitingPlane = false
        prefs.edit().putString(KEY_LAST_SCENE, sceneId).putString(KEY_LAST_JOB, jobId).apply()
        binding.movedObjectPanel.visibility = View.VISIBLE
        enableButtons(
            home = originalPose != null, adjust = false,
            restore = true, undo = true, clear = true,
        )
        originalPose?.let {
            Log.d(TAG, "원래 위치 저장: t=(%.3f, %.3f, %.3f)".format(it.tx(), it.ty(), it.tz()))
        }
        if (placeMarkerNow()) {
            status("삭제 완료 · ${label()} 마커를 손가락으로 끌어 옮기세요 (놓으면 그 자리에 배치)")
        } else {
            awaitingPlane = true
            status("삭제 완료 · 평면이 인식되면 ${label()} 마커가 나타납니다 · 끌어서 옮기세요")
        }
        Log.d(
            TAG,
            "arm: type=$objectType hasOriginalPose=${originalPose != null} " +
                "src=${sourceRect?.joinToString(",")} markerPlaced=${node != null} awaitingPlane=$awaitingPlane",
        )
    }

    /** 전체 정리 (선택 취소 시). 서버 복원 정보까지 지운다. */
    fun disarm() {
        clearMovedNode()
        armed = false
        awaitingPlane = false
        currentSceneId = null
        currentJobId = null
        sourceRect = null
        objectBitmap = null
        originalPose = null
        prefs.edit().remove(KEY_LAST_SCENE).remove(KEY_LAST_JOB).apply()
        binding.movedObjectPanel.visibility = View.GONE
    }

    /** 매 프레임(MainActivity space.onFrame). 마커를 아직 못 띄웠으면 평면 인식되는 대로 띄운다. */
    fun onFrame() {
        // TEMP-DIAG(A): 이동 마커가 하나만 존재하고, 드래그 후에도 원래 자리에 새 노드가
        // 안 남는지 확인. node# 가 계속 같고 worldPos 가 "새 위치" 면 A 는 아니다.
        node?.let { n ->
            if (++frameLog % 60 == 0) {
                val camNow = space.latestFrame?.camera?.pose
                val atCreate = markerCamPoseAtCreate
                val dPos = if (camNow != null && atCreate != null) distancePose(camNow, atCreate) else -1f
                val dAng = if (camNow != null && atCreate != null) quatAngleDeg(camNow, atCreate) else -1f
                Log.d(
                    TAG,
                    "[moved A] node#%d anchorPos=%s track=%s dragging=%b · 생성 후 카메라 Δ이동=%.2fm Δ회전=%.1f°".format(
                        n.hashCode(), poseStr(n.anchor.pose),
                        runCatching { n.anchor.trackingState }.getOrNull(), dragging, dPos, dAng,
                    ),
                )
            }
        }
        stepDragSmoothing()
        if (!armed || node != null || !awaitingPlane) return
        if (placeMarkerNow()) {
            awaitingPlane = false
            Log.d(TAG, "onFrame: 이동 마커 배치 성공 (awaitingPlane 해제)")
            status("${label()} 마커를 손가락으로 끌어 옮기세요")
        }
    }

    // ------------------------------------------------------------------- 배치

    /**
     * 삭제된 자리에 마커를 띄운다. 순서대로 시도한다:
     *  1) 삭제 시점 벽/바닥 앵커 pose(`originalPose`)
     *  2) `source_region` 중심을 현재 화면에서 hitTest 한 자리
     *  3) (평면 미인식) 카메라 앞 ~1.2m — 평면에 고정되진 않지만 **바로 붙잡아 끌 수 있고**,
     *     드래그 중 `onDrag` 의 hitTest 로 평면에 재고정된다.
     * 3번까지 실패하는 건 프레임 자체가 없을 때뿐이며, 그땐 `awaitingPlane` 재시도로 넘어간다.
     */
    private fun placeMarkerNow(): Boolean {
        val pose = originalPose
        if (pose != null) {
            val anchor = runCatching { sceneView.session?.createAnchor(pose) }.getOrNull()
            if (anchor != null) {
                Log.d(TAG, "placeMarkerNow: originalPose 사용")
                setNode(anchor, onVertical = wantsWall())
                return true
            }
            Log.d(TAG, "placeMarkerNow: originalPose 앵커 생성 실패 → 다음 경로")
        }

        val src = sourceRect
        if (src != null && sceneView.width > 0 && sceneView.height > 0) {
            val cx = (src[0] + src[2] / 2f) * sceneView.width
            val cy = (src[1] + src[3] / 2f) * sceneView.height
            val hit = space.hitTestPreferring(cx, cy, wantsWall())
            val anchor = hit?.let {
                it.createAnchorOrNull()
                    ?: runCatching { sceneView.session?.createAnchor(it.hitPose) }.getOrNull()
            }
            if (hit != null && anchor != null) {
                Log.d(TAG, "placeMarkerNow: source_region hitTest 사용 @(${cx.toInt()},${cy.toInt()})")
                setNode(anchor, (hit.trackable as? Plane)?.type == Plane.Type.VERTICAL)
                return true
            }
            Log.d(TAG, "placeMarkerNow: source_region hitTest 실패 @(${cx.toInt()},${cy.toInt()}) hit=${hit != null}")
        }

        // 3) 마지막 수단 — 카메라 앞 1.2m.
        val camPose = space.latestFrame?.camera?.pose
        if (camPose != null) {
            val front = camPose.compose(Pose.makeTranslation(0f, 0f, -1.2f))
            val anchor = runCatching { sceneView.session?.createAnchor(front) }.getOrNull()
            if (anchor != null) {
                Log.d(TAG, "placeMarkerNow: 평면 미인식 → 카메라 앞 1.2m fallback 마커")
                setNode(anchor, onVertical = wantsWall())
                return true
            }
        }
        Log.d(
            TAG,
            "placeMarkerNow: 모든 경로 실패 (session=${sceneView.session != null} frame=${space.latestFrame != null})",
        )
        return false
    }

    private fun placeAtOriginal() {
        val p = originalPose ?: return
        val anchor = runCatching { sceneView.session?.createAnchor(p) }.getOrNull()
        if (anchor == null) {
            status("원위치 앵커를 만들지 못했습니다")
            return
        }
        setNode(anchor, onVertical = wantsWall())
        scheduleSave()
        status("${label()}을(를) 삭제된 자리로 되돌렸습니다")
    }

    // --------------------------------------------- 드래그 이동 (큐브와 동일 방식)

    fun onDragBegin(xPx: Float, yPx: Float): Boolean {
        val ok = canManipulate()
        Log.d(
            TAG,
            "onDragBegin @(${xPx.toInt()},${yPx.toInt()}) canManipulate=$ok " +
                "(armed=$armed node=${node != null} furnitureSel=${furnitureHasSelection()})",
        )
        if (!ok) return false
        dragging = true
        settleFramesLeft = 0
        node?.let { n ->
            n.updateAnchorPose = false
            // 스무딩 시작점 = 지금 마커가 있는 자리 (첫 프레임에 튀지 않게).
            val p = n.pose
            val sp = FloatArray(3).also { p.getTranslation(it, 0) }
            val sq = FloatArray(4).also { p.getRotationQuaternion(it, 0) }
            smoothPos = sp
            smoothQuat = sq
            dragTargetPos = sp.copyOf()
            dragTargetQuat = sq.copyOf()
        }
        return true
    }

    fun onDrag(xPx: Float, yPx: Float): Boolean {
        if (!dragging) return false
        node ?: return true
        val hit = space.hitTestPreferring(xPx, yPx, wantsWall())
        if (hit == null) {
            if (++dragLogN % 12 == 0) Log.d(TAG, "onDrag: hitTest 없음 @(${xPx.toInt()},${yPx.toInt()}) — 마커 위치 유지")
            return true
        }
        val plane = hit.trackable as? Plane
        onVertical = plane?.type == Plane.Type.VERTICAL
        // 원시 hitPose 회전(손 떨림·시선 각도로 사다리꼴처럼 삐뚤어짐)을 그대로 쓰지 않고,
        // 평면 대표 법선(plane.centerPose)을 기준으로 항상 반듯하게 세운 pose 를 목표로 삼는다.
        // 실제 노드 이동은 stepDragSmoothing(onFrame) 의 EMA 가 담당 → 팔딱거림 방지.
        val pos = FloatArray(3).also { hit.hitPose.getTranslation(it, 0) }
        val normal = FloatArray(3)
        (plane?.centerPose ?: hit.hitPose).getTransformedAxis(1, 1f, normal, 0)
        val target = uprightPose(pos, normal, onVertical)
        dragTargetPos = FloatArray(3).also { target.getTranslation(it, 0) }
        dragTargetQuat = FloatArray(4).also { target.getRotationQuaternion(it, 0) }
        return true
    }

    /** TEMP-DIAG: onDrag 스팸 억제용. */
    private var dragLogN = 0

    fun onDragEnd(): Boolean {
        if (!dragging) return false
        dragging = false
        val n = node ?: return true
        if (dragTargetPos == null || smoothPos == null) {
            // 드래그 중 hitTest 가 한 번도 안 잡힘 → 안착 보간 없이 현재 자리에서 즉시 고정.
            finalizeDragAnchor()
            return true
        }
        // 손을 뗀 뒤에도 몇 프레임 더 목표로 보간해 부드럽게 안착시킨 뒤 앵커를 고정한다.
        settleFramesLeft = SETTLE_FRAMES
        Log.d(TAG, "onDragEnd: 안착 보간 시작 ($SETTLE_FRAMES 프레임) → finalizeDragAnchor")
        return true
    }

    /** 스무딩된 현재 pose 로 새 앵커를 만들어 노드를 최종 고정한다(큐브 finalizeDrag 와 동일). */
    private fun finalizeDragAnchor() {
        val n = node ?: return
        settleFramesLeft = 0
        val poseNow = n.pose
        val fresh = runCatching { sceneView.session?.createAnchor(poseNow) }.getOrNull()
        if (fresh != null) {
            runCatching { n.anchor.detach() }
            n.anchor = fresh
        }
        n.updateAnchorPose = true   // 이후엔 앵커(=스무딩 끝난 pose)를 그대로 따라감 → 튐 없음
        smoothPos = null
        smoothQuat = null
        dragTargetPos = null
        dragTargetQuat = null
        Log.d(
            TAG,
            "finalizeDragAnchor: node#%d 재고정 pose=%s fresh=%b".format(
                n.hashCode(), poseStr(n.anchor.pose), fresh != null,
            ),
        )
        scheduleSave()   // 제스처 완료 → 최신 상태 저장
    }

    /**
     * onFrame 매 프레임: 드래그/안착 중이면 노드 pose 를 목표(`dragTarget*`)로 EMA easing.
     * 위치는 선형 EMA, 회전은 최단경로 nlerp. 손을 뗀 뒤엔 [settleFramesLeft] 를 소진하며
     * 계속 보간하고, 0 이 되면 [finalizeDragAnchor] 로 앵커를 고정한다.
     */
    private fun stepDragSmoothing() {
        if (!dragging && settleFramesLeft <= 0) return
        val n = node ?: return
        val tp = dragTargetPos ?: return
        val tq = dragTargetQuat ?: return
        val sp = smoothPos ?: tp.copyOf().also { smoothPos = it }
        val sq = smoothQuat ?: tq.copyOf().also { smoothQuat = it }
        for (i in 0..2) sp[i] += (tp[i] - sp[i]) * DRAG_SMOOTH_ALPHA
        nlerpInto(sq, tq, DRAG_SMOOTH_ALPHA)
        n.pose = Pose(sp, sq)
        applyChildTransforms()
        if (!dragging && settleFramesLeft > 0) {
            settleFramesLeft--
            if (settleFramesLeft <= 0) finalizeDragAnchor()
        }
    }

    /** [sq] 를 [tq] 쪽으로 [t] 만큼 최단경로 nlerp — 결과를 [sq] 에 다시 쓴다. */
    private fun nlerpInto(sq: FloatArray, tq: FloatArray, t: Float) {
        val dot = sq[0] * tq[0] + sq[1] * tq[1] + sq[2] * tq[2] + sq[3] * tq[3]
        val s = if (dot < 0f) -1f else 1f
        for (i in 0..3) sq[i] += (s * tq[i] - sq[i]) * t
        val l = sqrt(sq[0] * sq[0] + sq[1] * sq[1] + sq[2] * sq[2] + sq[3] * sq[3])
        if (l > 1e-6f) for (i in 0..3) sq[i] /= l
    }

    // --------------------------------------------- 크기(핀치/＋－) · 회전(버튼)

    fun onScale(factor: Float): Boolean {
        if (!canManipulate()) return false
        bump(factor)
        return true
    }

    private fun canManipulate(): Boolean =
        armed && node != null && !furnitureHasSelection()

    private fun bump(factor: Float) {
        if (node == null) return
        scaleF = (scaleF * factor).coerceIn(0.3f, 3f)
        applyChildTransforms()
        scheduleSave()
    }

    private fun rotate(deltaDeg: Float) {
        if (node == null) return
        rotDeg = ((rotDeg + deltaDeg) % 360f + 360f) % 360f
        applyChildTransforms()
        scheduleSave()
    }
    /** 상하 기울임. 뒤집히지 않게 ±60° 로 제한. 서버 저장 없음(세션 로컬 표시 조정). */
    private fun tilt(deltaDeg: Float) {
        if (node == null) return
        tiltDeg = (tiltDeg + deltaDeg).coerceIn(-60f, 60f)
        applyChildTransforms()
    }

    // --------------------------------------------- 수직 정렬 (손 떨림/각도로 삐뚤어짐 보정)

    /**
     * "수직 정렬" 버튼. 이동한 이미지가 손 떨림·시선 각도로 사다리꼴처럼 기울어졌을 때
     * 반듯하게 되돌린다.
     * - 벽 사물: 자식 quad 의 기울기(`tiltDeg`)와 roll(`rotDeg`)을 0 으로, 부모 앵커를
     *   벽 **대표 법선** 방향의 중력 정렬 pose 로 다시 고정 → 항상 수평·수직이 맞는 직사각형.
     * - 바닥 사물: `tiltDeg` 만 0 으로 (앞뒤 기울기 제거), 좌우 회전(`rotDeg` = yaw)은 유지.
     */
    fun alignVerticalNow() {
        val n = node ?: run { status("정렬할 사물이 없습니다"); return }
        // 진행 중인 드래그 스무딩/안착이 있으면 멈추고 지금 상태에서 정렬한다.
        dragging = false
        settleFramesLeft = 0
        smoothPos = null
        smoothQuat = null
        dragTargetPos = null
        dragTargetQuat = null
        tiltDeg = 0f
        if (onVertical) {
            rotDeg = 0f
            val cur = n.pose
            val pos = FloatArray(3).also { cur.getTranslation(it, 0) }
            // 현재 부모 +Y = 배치 시 쓴 법선(신규 경로) 또는 원시 hitPose 법선(구 경로).
            val normal = FloatArray(3).also { cur.getTransformedAxis(1, 1f, it, 0) }
            reanchor(n, uprightPose(pos, normal, wantVertical = true))
            status("${label()} 이미지를 벽에 수직으로 맞췄습니다")
        } else {
            status("${label()} 앞뒤 기울기를 없앴습니다")
        }
        applyChildTransforms()
        scheduleSave()
    }

    /** 노드를 [pose] 로 재고정한다(새 앵커 생성 → 교체). onDragEnd 의 재고정과 동일 방식. */
    private fun reanchor(n: AnchorNode, pose: Pose) {
        val fresh = runCatching { sceneView.session?.createAnchor(pose) }.getOrNull()
        if (fresh != null) {
            n.updateAnchorPose = false
            runCatching { n.anchor.detach() }
            n.anchor = fresh
            n.pose = pose
            n.updateAnchorPose = true
        } else {
            n.pose = pose
        }
    }

    /**
     * 배치/정렬에 쓸 "반듯한" 부모 pose.
     * - 수평면(바닥): 회전 항등 — 자식 `rotDeg` 가 yaw 를 담당한다.
     * - 수직면(벽): [normalIn] 을 수평으로 투영해 법선(+Y)으로 삼고, in-plane 축을 세계
     *   up 에 맞춰 roll 을 제거한다(hitPose 와 같은 "+Y = 법선" 규약이라 자식 회전은 그대로).
     */
    private fun uprightPose(position: FloatArray, normalIn: FloatArray, wantVertical: Boolean): Pose {
        if (!wantVertical) return Pose(position, IDENTITY_QUAT)
        val n = floatArrayOf(normalIn[0], 0f, normalIn[2])   // 수평 투영 = 완전 수직 벽 강제
        var len = sqrt(n[0] * n[0] + n[2] * n[2])
        if (len < 1e-4f) { n[0] = 0f; n[2] = 1f; len = 1f }
        n[0] /= len; n[2] /= len
        // 카메라 쪽을 향하도록 법선 부호 정렬
        space.latestFrame?.camera?.pose?.let { cam ->
            if (n[0] * (cam.tx() - position[0]) + n[2] * (cam.tz() - position[2]) < 0f) {
                n[0] = -n[0]; n[2] = -n[2]
            }
        }
        val up = floatArrayOf(0f, 1f, 0f)
        val xAxis = normalize3(cross3(up, n))         // 수평, in-plane
        val zAxis = normalize3(cross3(n, xAxis))      // ≈ 세계 up, in-plane
        return Pose(position, quatFromBasis(xAxis, n, zAxis))
    }

    private fun cross3(a: FloatArray, b: FloatArray) = floatArrayOf(
        a[1] * b[2] - a[2] * b[1],
        a[2] * b[0] - a[0] * b[2],
        a[0] * b[1] - a[1] * b[0],
    )

    private fun normalize3(v: FloatArray): FloatArray {
        val l = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])
        return if (l < 1e-6f) floatArrayOf(0f, 0f, 1f) else floatArrayOf(v[0] / l, v[1] / l, v[2] / l)
    }

    /** 정규직교 열벡터 3개(로컬 X/Y/Z 축)로 이뤄진 회전 → 쿼터니언 [x,y,z,w]. */
    private fun quatFromBasis(x: FloatArray, y: FloatArray, z: FloatArray): FloatArray {
        val m00 = x[0]; val m10 = x[1]; val m20 = x[2]
        val m01 = y[0]; val m11 = y[1]; val m21 = y[2]
        val m02 = z[0]; val m12 = z[1]; val m22 = z[2]
        val tr = m00 + m11 + m22
        val q = FloatArray(4)
        when {
            tr > 0f -> {
                val s = sqrt(tr + 1f) * 2f
                q[3] = 0.25f * s; q[0] = (m21 - m12) / s; q[1] = (m02 - m20) / s; q[2] = (m10 - m01) / s
            }
            m00 > m11 && m00 > m22 -> {
                val s = sqrt(1f + m00 - m11 - m22) * 2f
                q[3] = (m21 - m12) / s; q[0] = 0.25f * s; q[1] = (m01 + m10) / s; q[2] = (m02 + m20) / s
            }
            m11 > m22 -> {
                val s = sqrt(1f + m11 - m00 - m22) * 2f
                q[3] = (m02 - m20) / s; q[0] = (m01 + m10) / s; q[1] = 0.25f * s; q[2] = (m12 + m21) / s
            }
            else -> {
                val s = sqrt(1f + m22 - m00 - m11) * 2f
                q[3] = (m10 - m01) / s; q[0] = (m02 + m20) / s; q[1] = (m12 + m21) / s; q[2] = 0.25f * s
            }
        }
        val l = sqrt(q[0] * q[0] + q[1] * q[1] + q[2] * q[2] + q[3] * q[3])
        if (l > 1e-6f) { q[0] /= l; q[1] /= l; q[2] /= l; q[3] /= l }
        return q
    }

    // ------------------------------------------------------ 서버: 저장 / 복원 / 취소

    /** 변경이 잦아들면(디바운스) 한 번만 저장한다. 핀치처럼 연속 이벤트를 합친다. */
    private fun scheduleSave() {
        if (currentSceneId == null || node == null) return
        handler.removeCallbacks(saveDebounce)
        handler.postDelayed(saveDebounce, SAVE_DEBOUNCE_MS)
    }

    private fun savePlacementNow() {
        val sceneId = currentSceneId ?: return
        val n = node ?: return
        val p = n.anchor.pose
        val pos = FloatArray(3).also { p.getTranslation(it, 0) }
        val quat = FloatArray(4).also { p.getRotationQuaternion(it, 0) }
        val plane = if (onVertical) "wall" else "floor"
        val sc = scaleF
        val rot = rotDeg
        val type = objectType
        val jid = currentJobId
        val src = sourceRect?.copyOf()
        val base = serverBaseUrl()
        scope.launch {
            try {
                val id = InteriorApiClient(base).createPlacement(
                    sceneId, type, pos, quat, sc, rot, plane, jid, src,
                )
                Log.d(TAG, "배치 저장 $id (scene=$sceneId scale=$sc rot=$rot plane=$plane)")
                status("배치 저장됨")
            } catch (e: Exception) {
                status("배치 저장 실패: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    private fun restoreFromServer() {
        onAlsoRestore()   // 카탈로그 가구 등 다른 복원도 함께
        val sceneId = currentSceneId ?: run { status("복원할 세션이 없습니다"); return }
        val base = serverBaseUrl()
        status("서버에서 배치 불러오는 중…")
        scope.launch {
            val client = InteriorApiClient(base)
            val plc = try {
                client.latestActivePlacement(sceneId)
            } catch (e: Exception) {
                status("배치 조회 실패: ${e.message ?: e.javaClass.simpleName}")
                return@launch
            }
            if (plc == null) {
                status("서버에 복원할 active 배치가 없습니다")
                return@launch
            }

            // 사물 이미지: 투명 배경 컷아웃({job}_object.png) 을 우선, 없으면 네모 크롭
            // ({job}_object.jpg), 그것도 없으면 플레이스홀더.
            val jid = plc.jobId
            var bmp: Bitmap? = null
            if (jid != null) {
                for (path in listOf(
                    "/scenes/$sceneId/results/${jid}_object.png",
                    "/scenes/$sceneId/results/${jid}_object.jpg",
                )) {
                    bmp = try {
                        val bytes = client.downloadBytes(path)
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    } catch (_: Exception) {
                        null
                    }
                    if (bmp != null) break
                }
            }

            objectType = plc.objectType
            scaleF = plc.scale.coerceIn(0.3f, 3f)
            rotDeg = plc.rotationDeg
            sourceRect = plc.sourceRect?.copyOf()
            currentJobId = jid
            objectBitmap = (bmp ?: placeholderBitmap()).let { EdgeFade.feather(downscale(it)) }
            armed = true
            awaitingPlane = false
            binding.movedObjectPanel.visibility = View.VISIBLE
            enableButtons(home = false, adjust = false, restore = true, undo = true, clear = true)

            // pose 는 세션 로컬이라 못 쓴다 → source_region 중심을 현재 화면에서 다시 hitTest.
            val src = plc.sourceRect
            val wantWall = when (plc.plane) {
                "wall" -> true
                "floor" -> false
                else -> wantsWall()
            }
            val cx = (((src?.getOrNull(0) ?: 0.4f) + (src?.getOrNull(2) ?: 0.2f) / 2f)) * sceneView.width
            val cy = (((src?.getOrNull(1) ?: 0.4f) + (src?.getOrNull(3) ?: 0.2f) / 2f)) * sceneView.height
            val hit = space.hitTestPreferring(cx, cy, wantWall)
            if (hit == null) {
                status("평면을 아직 못 찾았어요 · 그 방향을 비춘 뒤 '서버 배치 복원'을 다시 눌러주세요")
                return@launch
            }
            val hitVertical = (hit.trackable as? Plane)?.type == Plane.Type.VERTICAL
            val anchor = hit.createAnchorOrNull()
                ?: runCatching { sceneView.session?.createAnchor(hit.hitPose) }.getOrNull()
            if (anchor == null) {
                status("앵커 생성 실패 · 다시 시도해주세요")
                return@launch
            }
            setNode(anchor, hitVertical)   // 복원은 다시 저장하지 않는다
            status("서버 배치 복원 완료 · 마커를 끌어서 위치를 다듬으면 다시 저장됩니다")
        }
    }

    private fun undoOnServer() {
        val sceneId = currentSceneId ?: run { status("취소할 배치가 없습니다"); return }
        val base = serverBaseUrl()
        scope.launch {
            try {
                val ok = InteriorApiClient(base).undoPlacement(sceneId)
                if (ok) {
                    handler.removeCallbacks(saveDebounce)   // 취소 직전 예약된 저장은 버린다
                    clearMovedNode()
                    status("실행 취소됨 · 서버 배치 1건 취소")
                } else {
                    status("취소할 배치가 없습니다")
                }
            } catch (e: Exception) {
                status("실행 취소 실패: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    // ------------------------------------------------------------------- 노드

    private fun setNode(anchor: Anchor, onVertical: Boolean) {
        clearMovedNode()
        this.onVertical = onVertical

        val img = ImageNode(
            materialLoader = sceneView.materialLoader,
            bitmap = objectBitmap ?: EdgeFade.feather(placeholderBitmap()),
            size = Size(baseW, baseH),
            // 커버 quad 와 동일 — 기본 샘플러(REPEAT + mipmap)의 가장자리 밝은 선 방지.
            textureSampler = EdgeFade.crispSampler(),
        ).apply { isTouchable = false }

        val lbl = ImageNode(
            materialLoader = sceneView.materialLoader,
            bitmap = LabelRenderer.make("${label()} · 끌어 옮기기"),
            size = Size(LABEL_W, LABEL_W * 0.34f),
        ).apply { isTouchable = false }

        val n = AnchorNode(sceneView.engine, anchor).apply {
            isPositionEditable = false
            updateAnchorPose = true
            addChildNode(img)
            addChildNode(lbl)
        }
        sceneView.addChildNode(n)

        node = n
        imageNode = img
        labelNode = lbl
        awaitingPlane = false
        markerCamPoseAtCreate = space.latestFrame?.camera?.pose
        enableButtons(home = originalPose != null, adjust = true, restore = true, undo = true, clear = true)
        applyChildTransforms()
        Log.d(
            TAG,
            "setNode: 이동 마커 node#%d type=%s vertical=%b anchorPose=%s camAtCreate=%s".format(
                n.hashCode(), objectType, onVertical, poseStr(anchor.pose), poseStr(markerCamPoseAtCreate),
            ),
        )
    }

    // ------------------------------------------------------- TEMP-DIAG 헬퍼

    private fun poseStr(p: Pose?): String =
        if (p == null) "null" else "t=(%.2f,%.2f,%.2f)".format(p.tx(), p.ty(), p.tz())

    private fun distancePose(a: Pose, b: Pose): Float {
        val dx = a.tx() - b.tx()
        val dy = a.ty() - b.ty()
        val dz = a.tz() - b.tz()
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    private fun quatAngleDeg(a: Pose, b: Pose): Float {
        val qa = FloatArray(4).also { a.getRotationQuaternion(it, 0) }
        val qb = FloatArray(4).also { b.getRotationQuaternion(it, 0) }
        var dot = qa[0] * qb[0] + qa[1] * qb[1] + qa[2] * qb[2] + qa[3] * qb[3]
        dot = abs(dot).coerceIn(0f, 1f)
        return Math.toDegrees(2.0 * acos(dot.toDouble())).toFloat()
    }

    /** 자식(이미지/라벨)의 회전·배율·오프셋을 잡는다. */
    private fun applyChildTransforms() {
        // MARKER_SCALE 은 이제 1.0 (표시 확대 없음). MOVED_SCALE_CORRECTION 은 실기기
        // 미세 조정용 임시 노브 — 둘 다 1.0 이면 baseW×baseH(실측 미터) 그대로 그린다.
        val disp = scaleF * MARKER_SCALE * MOVED_SCALE_CORRECTION
        val h = baseH * disp
        imageNode?.let {
            it.scale = Scale(disp)
            it.rotation =
                if (onVertical) Rotation(-90f + tiltDeg, 0f, rotDeg)
                else Rotation(tiltDeg, rotDeg, 0f)
        }
        labelNode?.let {
            it.position = Position(0f, h + LABEL_GAP, 0f)
            it.rotation = if (onVertical) Rotation(-90f, 0f, 0f) else Rotation(0f, rotDeg, 0f)
        }
    }

    /** 화면의 이동된 노드만 제거한다. scene/job/복원 정보는 유지. */
    private fun clearMovedNode() {
        node?.let {
            Log.d(TAG, "clearMovedNode: node#${it.hashCode()} 제거 (remove + anchor.detach + destroy)")
            sceneView.removeChildNode(it)
            runCatching { it.anchor.detach() }
            runCatching { it.destroy() }
        }
        node = null
        imageNode = null
        labelNode = null
        dragging = false
        settleFramesLeft = 0
        smoothPos = null
        smoothQuat = null
        dragTargetPos = null
        dragTargetQuat = null
        val hasScene = currentSceneId != null
        enableButtons(home = false, adjust = false, restore = hasScene, undo = hasScene, clear = false)
    }

    private fun enableButtons(
        home: Boolean, adjust: Boolean, restore: Boolean, undo: Boolean, clear: Boolean,
    ) {
        binding.btnMovedHome.isEnabled = home
        binding.btnMovedRestore.isEnabled = restore
        binding.btnMovedUndo.isEnabled = undo
        binding.btnMovedClear.isEnabled = clear
        binding.btnMovedShrink.isEnabled = adjust
        binding.btnMovedGrow.isEnabled = adjust
        binding.btnMovedRotateLeft.isEnabled = adjust
        binding.btnMovedRotateRight.isEnabled = adjust
        binding.btnMovedTiltUp.isEnabled = adjust
        binding.btnMovedTiltDown.isEnabled = adjust
        binding.btnMovedAlign.isEnabled = adjust
    }

    private fun downscale(src: Bitmap): Bitmap {
        val longest = maxOf(src.width, src.height)
        if (longest <= MAX_TEX) return src
        val f = MAX_TEX.toFloat() / longest
        return Bitmap.createScaledBitmap(
            src,
            (src.width * f).toInt().coerceAtLeast(1),
            (src.height * f).toInt().coerceAtLeast(1),
            true,
        )
    }

    private fun placeholderBitmap(): Bitmap =
        Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.argb(120, 90, 170, 255))
        }

    private companion object {
        const val TAG = "InteriorAR"
        const val SCALE_STEP = 1.15f
        const val MAX_TEX = 512
        const val LABEL_W = 0.12f
        const val LABEL_GAP = 0.06f
        const val SAVE_DEBOUNCE_MS = 500L

        /**
         * 마커 표시 배율. 예전엔 1.35 로 키웠지만(터치 편의 목적), 마커 ImageNode 는
         * `isTouchable = false` 이고 드래그 판정은 화면 좌표 기반(`onDragBegin` 이 노드를
         * hitTest 하지 않음)이라 확대 이득이 전혀 없었다. 오히려 이동된 사물이 원본보다
         * ~1.35배 커 보이는 주원인이라 1.0 으로 되돌렸다.
         */
        const val MARKER_SCALE = 1f

        /**
         * 이동 사물 크기 임시 보정 계수 (설계서의 INTERIOR_MOVED_SCALE_CORRECTION 대응).
         * 크기 계산은 전부 앱(클라이언트)에서 하므로 서버 env 가 아니라 이 상수다.
         * bbox 가장자리 hitTest 로 잰 폭이 원근 때문에 실제보다 조금 크게 나오는 잔차를
         * 실기기에서 맞추기 위한 노브. 1.0 = 보정 없음. 크게 나오면 0.67 등으로 내린다.
         * 값 변경 후 `:app:assembleDebug` 증분 빌드(~40s) → 재설치.
         */
        const val MOVED_SCALE_CORRECTION = 1f
        const val KEY_LAST_SCENE = "moved_last_scene"
        const val KEY_LAST_JOB = "moved_last_job"

        /**
         * 드래그/안착 위치·회전 EMA 계수(0~1). 작을수록 부드럽지만 손가락을 더 늦게 따라온다.
         * [RemovalController.SMOOTH_ALPHA](0.2, anchor 지터 완화)와 같은 취지이며, 능동적
         * 드래그라 조금 더 민첩하게 0.30 을 쓴다.
         */
        const val DRAG_SMOOTH_ALPHA = 0.30f

        /** 손을 뗀 뒤 최종 위치로 부드럽게 안착시키며 보간하는 프레임 수. */
        const val SETTLE_FRAMES = 8

        /** 회전 없음 쿼터니언 (x,y,z,w). 바닥 배치의 부모 앵커에 회전을 안 줄 때. */
        val IDENTITY_QUAT = floatArrayOf(0f, 0f, 0f, 1f)
        val OBJECT_LABELS = mapOf(
            "tv" to "TV", "sofa" to "소파", "table" to "테이블",
            "chair" to "의자", "shelf" to "선반", "other" to "사물",
        )
    }
}
