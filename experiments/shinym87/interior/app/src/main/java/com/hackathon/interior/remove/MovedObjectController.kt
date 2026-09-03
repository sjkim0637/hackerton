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
import android.widget.Toast
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
 * PHASE 4 사용자 1 — 삭제한 사물을 **다른 위치로 이동**, 그리고 그 상태를 **서버에 저장/복원**.
 *
 * 삭제가 끝나면 [arm] 으로 "이동할 사물"(scene/job, 원래 위치, 캡처 이미지, 종류)을 기억한다.
 * "여기로 옮기기" → 새 지점 탭 → 원래 사물을 그 자리에 다시 띄운다.
 * 배치/드래그/회전/크기 변경이 잦아들면(디바운스) `POST /scenes/{id}/placements` 로 저장한다.
 * "서버 배치 복원" 은 `GET .../placements` 의 마지막 active 를 받아, pose 대신
 * `source_region` + plane 을 기준으로 **현재 세션에서 다시 hitTest** 해 재배치한다.
 * "실행 취소" 는 `POST .../placements/undo` 후 화면에서도 그 배치를 없앤다.
 *
 * 큐브 배치와 같은 방식(탭 배치·드래그 이동·핀치/＋－ 크기·회전 버튼)을 재사용하되
 * 표시는 캡처한 사물 이미지 quad 다. 사물 종류로 벽(TV·선반)/바닥 자동 맞춤.
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
) {

    private val prefs = activity.getSharedPreferences("interior", Context.MODE_PRIVATE)
    private val handler = Handler(Looper.getMainLooper())
    private val saveDebounce = Runnable { savePlacementNow() }

    private var armed = false
    private var placing = false
    private var dragging = false

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
        binding.btnMovedPlace.setOnClickListener { startPlacing() }
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
            enableButtons(place = false, home = false, adjust = false, restore = true, undo = true, clear = false)
            status("이전 세션 배치가 있습니다 · 평면 인식 후 '서버 배치 복원'을 누르세요")
        }
    }

    private fun label(): String = OBJECT_LABELS[objectType] ?: "사물"
    private fun wantsWall(): Boolean = objectType == "tv" || objectType == "shelf"

    // ------------------------------------------------------------- arm / disarm

    /** 삭제 완료 시점에 호출. 이동할 사물 정보를 기억하고 이동 패널을 연다. */
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
        prefs.edit().putString(KEY_LAST_SCENE, sceneId).putString(KEY_LAST_JOB, jobId).apply()
        binding.movedObjectPanel.visibility = View.VISIBLE
        enableButtons(
            place = true, home = originalPose != null, adjust = false,
            restore = true, undo = true, clear = true,
        )
        originalPose?.let {
            Log.d(TAG, "원래 위치 저장: t=(%.3f, %.3f, %.3f)".format(it.tx(), it.ty(), it.tz()))
        }
        status("삭제 완료 · '여기로 옮기기'로 ${label()}을(를) 새 위치에 놓을 수 있어요")
    }

    /** 전체 정리 (선택 취소 시). 서버 복원 정보까지 지운다. */
    fun disarm() {
        clearMovedNode()
        armed = false
        placing = false
        currentSceneId = null
        currentJobId = null
        sourceRect = null
        objectBitmap = null
        originalPose = null
        prefs.edit().remove(KEY_LAST_SCENE).remove(KEY_LAST_JOB).apply()
        binding.movedObjectPanel.visibility = View.GONE
    }

    // ------------------------------------------------------------------- 배치

    private fun startPlacing() {
        if (currentSceneId == null) return
        armed = true
        placing = true
        status("옮길 위치를 탭하세요 · ${label()}은(는) ${if (wantsWall()) "벽" else "바닥"}에 맞춰 붙습니다")
    }

    private fun placeAtOriginal() {
        val p = originalPose ?: return
        val anchor = runCatching { sceneView.session?.createAnchor(p) }.getOrNull()
        if (anchor == null) {
            status("원위치 앵커를 만들지 못했습니다")
            return
        }
        placing = false
        setNode(anchor, onVertical = wantsWall())
        scheduleSave()
        status("${label()}을(를) 원래 위치에 되돌렸습니다")
    }

    /** MainActivity 탭 제스처에서 먼저 호출. 처리했으면 true (큐브 쪽으로 안 넘어감). */
    fun onTap(xPx: Float, yPx: Float): Boolean {
        if (!armed || !placing) return false
        val wantWall = wantsWall()
        val hit = space.hitTestPreferring(xPx, yPx, wantWall)
        if (hit == null) {
            status("여기선 평면을 못 찾았어요 · 격자가 보이는 ${if (wantWall) "벽" else "바닥"}을 탭하세요")
            return true
        }
        val hitVertical = (hit.trackable as? Plane)?.type == Plane.Type.VERTICAL
        val anchor = hit.createAnchorOrNull()
            ?: runCatching { sceneView.session?.createAnchor(hit.hitPose) }.getOrNull()
        if (anchor == null) {
            status("앵커 생성 실패 · 다른 지점을 탭하세요")
            return true
        }
        placing = false
        setNode(anchor, hitVertical)
        scheduleSave()
        if (hitVertical != wantWall) {
            Toast.makeText(
                activity,
                "${label()}은(는) ${if (wantWall) "벽" else "바닥"}이 어울리지만 지금은 " +
                    "${if (hitVertical) "벽" else "바닥"}에 붙였어요",
                Toast.LENGTH_SHORT,
            ).show()
        }
        status("${label()} 이동 완료 · ＋－ 크기 · 회전 · 드래그로 조정 (변경은 서버에 저장됩니다)")
        return true
    }

    // ------------------------------------------------- 드래그 이동 (큐브와 동일 방식)

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
        armed && node != null && !placing && !furnitureHasSelection()

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
            binding.movedObjectPanel.visibility = View.VISIBLE
            enableButtons(place = true, home = false, adjust = false, restore = true, undo = true, clear = true)

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
            status("서버 배치 복원 완료 · 드래그/회전/크기로 조정하면 다시 저장됩니다")
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
                    placing = false
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
            bitmap = LabelRenderer.make("여기로 옮김 · ${label()}"),
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
        enableButtons(place = true, home = originalPose != null, adjust = true, restore = true, undo = true, clear = true)
        applyChildTransforms()
        Log.d(TAG, "이동 배치: type=$objectType vertical=$onVertical pose=${anchor.pose}")
    }

    /** 자식(이미지/라벨)의 회전·배율·오프셋을 평면 종류에 맞춰 다시 잡는다. */
    private fun applyChildTransforms() {
        val h = baseH * scaleF
        imageNode?.let {
            it.scale = Scale(scaleF)
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
        enableButtons(
            place = hasScene, home = false, adjust = false,
            restore = hasScene, undo = hasScene, clear = false,
        )
    }

    private fun enableButtons(
        place: Boolean, home: Boolean, adjust: Boolean,
        restore: Boolean, undo: Boolean, clear: Boolean,
    ) {
        binding.btnMovedPlace.isEnabled = place
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
        const val KEY_LAST_SCENE = "moved_last_scene"
        const val KEY_LAST_JOB = "moved_last_job"
        val OBJECT_LABELS = mapOf(
            "tv" to "TV", "sofa" to "소파", "table" to "테이블",
            "chair" to "의자", "shelf" to "선반", "other" to "사물",
        )
    }
}
