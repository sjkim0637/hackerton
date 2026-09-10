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
        binding.btnMovedRotate.setOnClickListener { rotate() }
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
        node?.updateAnchorPose = false
        return true
    }

    fun onDrag(xPx: Float, yPx: Float): Boolean {
        if (!dragging) return false
        val n = node ?: return true
        val hit = space.hitTestPreferring(xPx, yPx, wantsWall())
        if (hit == null) {
            if (++dragLogN % 12 == 0) Log.d(TAG, "onDrag: hitTest 없음 @(${xPx.toInt()},${yPx.toInt()}) — 마커 위치 유지")
            return true
        }
        n.pose = hit.hitPose
        onVertical = (hit.trackable as? Plane)?.type == Plane.Type.VERTICAL
        applyChildTransforms()
        return true
    }

    /** TEMP-DIAG: onDrag 스팸 억제용. */
    private var dragLogN = 0

    fun onDragEnd(): Boolean {
        if (!dragging) return false
        dragging = false
        val n = node ?: return true
        // 손을 뗀 위치에 최종 고정: 새 앵커로 재고정 (큐브 finalizeDrag 와 동일).
        // ※ 노드는 그대로 두고 anchor 만 교체한다 — 원래 자리에 노드/마커를 새로 안 남긴다.
        val fresh = runCatching { sceneView.session?.createAnchor(n.pose) }.getOrNull()
        if (fresh != null) {
            runCatching { n.anchor.detach() }
            n.anchor = fresh
        }
        n.updateAnchorPose = true
        Log.d(
            TAG,
            "onDragEnd: node#%d 같은 노드 재고정(새 노드/마커 생성 안 함) newAnchorPose=%s freshAnchor=%b".format(
                n.hashCode(), poseStr(n.anchor.pose), fresh != null,
            ),
        )
        scheduleSave()   // 제스처 완료 → 최신 상태 저장
        return true
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

    private fun rotate() {
        if (node == null) return
        rotDeg = (rotDeg + 15f) % 360f
        applyChildTransforms()
        scheduleSave()
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
            it.rotation = if (onVertical) Rotation(-90f, 0f, rotDeg) else Rotation(0f, rotDeg, 0f)
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
        binding.btnMovedRotate.isEnabled = adjust
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
        val OBJECT_LABELS = mapOf(
            "tv" to "TV", "sofa" to "소파", "table" to "테이블",
            "chair" to "의자", "shelf" to "선반", "other" to "사물",
        )
    }
}
