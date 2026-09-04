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
        this.baseW = widthM.coerceIn(0.15f, 3f)
        this.baseH = heightM.coerceIn(0.15f, 3f)
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
        if (!armed || node != null || !awaitingPlane) return
        if (placeMarkerNow()) {
            awaitingPlane = false
            status("${label()} 마커를 손가락으로 끌어 옮기세요")
        }
    }

    // ------------------------------------------------------------------- 배치

    /** 삭제된 자리(원래 pose, 없으면 source_region 중심 hitTest)에 마커를 띄운다. */
    private fun placeMarkerNow(): Boolean {
        val pose = originalPose
        if (pose != null) {
            val anchor = runCatching { sceneView.session?.createAnchor(pose) }.getOrNull() ?: return false
            setNode(anchor, onVertical = wantsWall())
            return true
        }
        val src = sourceRect ?: return false
        if (sceneView.width == 0 || sceneView.height == 0) return false
        val cx = (src[0] + src[2] / 2f) * sceneView.width
        val cy = (src[1] + src[3] / 2f) * sceneView.height
        val hit = space.hitTestPreferring(cx, cy, wantsWall()) ?: return false
        val anchor = hit.createAnchorOrNull()
            ?: runCatching { sceneView.session?.createAnchor(hit.hitPose) }.getOrNull()
            ?: return false
        setNode(anchor, (hit.trackable as? Plane)?.type == Plane.Type.VERTICAL)
        return true
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
        if (!canManipulate()) return false
        dragging = true
        node?.updateAnchorPose = false
        return true
    }

    fun onDrag(xPx: Float, yPx: Float): Boolean {
        if (!dragging) return false
        val n = node ?: return true
        val hit = space.hitTestPreferring(xPx, yPx, wantsWall()) ?: return true
        n.pose = hit.hitPose
        onVertical = (hit.trackable as? Plane)?.type == Plane.Type.VERTICAL
        applyChildTransforms()
        return true
    }

    fun onDragEnd(): Boolean {
        if (!dragging) return false
        dragging = false
        val n = node ?: return true
        // 손을 뗀 위치에 최종 고정: 새 앵커로 재고정 (큐브 finalizeDrag 와 동일).
        val fresh = runCatching { sceneView.session?.createAnchor(n.pose) }.getOrNull()
        if (fresh != null) {
            runCatching { n.anchor.detach() }
            n.anchor = fresh
        }
        n.updateAnchorPose = true
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

            // 사물 이미지: 서버의 제거-사물 크롭({job}_object.jpg). 없으면 플레이스홀더.
            val jid = plc.jobId
            val bmp: Bitmap? = if (jid != null) {
                try {
                    val bytes = client.downloadBytes("/scenes/$sceneId/results/${jid}_object.jpg")
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                } catch (_: Exception) {
                    null
                }
            } else {
                null
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
        enableButtons(home = originalPose != null, adjust = true, restore = true, undo = true, clear = true)
        applyChildTransforms()
        Log.d(TAG, "이동 마커: type=$objectType vertical=$onVertical pose=${anchor.pose}")
    }

    /** 자식(이미지/라벨)의 회전·배율·오프셋을 잡는다. 마커는 터치하기 쉽게 조금 크게 보인다. */
    private fun applyChildTransforms() {
        val disp = scaleF * MARKER_SCALE
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
        /** 마커를 실제 크기보다 이만큼 크게 그려 손가락으로 잡기 쉽게 한다. */
        const val MARKER_SCALE = 1.35f
        const val KEY_LAST_SCENE = "moved_last_scene"
        const val KEY_LAST_JOB = "moved_last_job"
        val OBJECT_LABELS = mapOf(
            "tv" to "TV", "sofa" to "소파", "table" to "테이블",
            "chair" to "의자", "shelf" to "선반", "other" to "사물",
        )
    }
}
