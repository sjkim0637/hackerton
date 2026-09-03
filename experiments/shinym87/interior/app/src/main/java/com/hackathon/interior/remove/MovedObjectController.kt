package com.hackathon.interior.remove

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Color
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

/**
 * PHASE 4 사용자 1 — 삭제한 사물을 **다른 위치로 이동**.
 *
 * 삭제가 끝나면 [arm] 으로 "이동할 사물"(원래 위치 + 캡처 이미지 + 종류)을 기억한다.
 * "여기로 옮기기" 를 누르고 새 지점을 탭하면, 원래 사물을 그 자리에 다시 띄운다.
 *
 * 큐브 배치와 **같은 방식**을 재사용한다: 탭으로 배치, 드래그로 이동, 핀치/＋－ 로 크기,
 * 버튼으로 회전. 다만 표시는 큐브 대신 캡처한 사물 이미지 quad 다.
 * 사물 종류에 따라 벽(TV·선반) 또는 바닥(그 외)에 자동으로 맞춰 붙인다.
 *
 * 목표는 "완벽한 3D 재배치"가 아니라 **"지운 자리 + 새 자리 두 곳에서 원래 사물의
 * 흔적을 본다"** 는 개념 증명이다.
 */
class MovedObjectController(
    private val activity: Activity,
    private val sceneView: ARSceneView,
    private val space: ArSpaceController,
    private val binding: ActivityMainBinding,
    /** 지금 큐브가 선택돼 있으면 제스처는 큐브 몫 — 이동된 사물은 손대지 않는다. */
    private val furnitureHasSelection: () -> Boolean,
    private val status: (String) -> Unit,
) {

    private var armed = false
    private var placing = false
    private var dragging = false

    private var objectType = "other"
    private var objectBitmap: Bitmap? = null
    private var originalPose: Pose? = null     // 요구사항 1: 삭제 시점의 원래 위치
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
        binding.btnMovedShrink.setOnClickListener { bump(1f / SCALE_STEP) }
        binding.btnMovedGrow.setOnClickListener { bump(SCALE_STEP) }
        binding.btnMovedRotate.setOnClickListener { rotate() }
        binding.btnMovedClear.setOnClickListener { disarm(announce = true) }
    }

    private fun label(): String = OBJECT_LABELS[objectType] ?: "사물"
    private fun wantsWall(): Boolean = objectType == "tv" || objectType == "shelf"

    // ------------------------------------------------------------- arm / disarm

    /** 삭제 완료 시점에 호출. 이동할 사물 정보를 기억하고 이동 패널을 연다. */
    fun arm(objectType: String, bitmap: Bitmap?, originalPose: Pose?, widthM: Float, heightM: Float) {
        disarm(announce = false)
        this.objectType = objectType
        this.objectBitmap = (bitmap ?: placeholderBitmap()).let { EdgeFade.feather(downscale(it)) }
        this.originalPose = originalPose
        this.baseW = widthM.coerceIn(0.15f, 3f)
        this.baseH = heightM.coerceIn(0.15f, 3f)
        this.scaleF = 1f
        this.rotDeg = 0f
        armed = true
        binding.movedObjectPanel.visibility = View.VISIBLE
        binding.btnMovedPlace.isEnabled = true
        binding.btnMovedHome.isEnabled = originalPose != null
        binding.btnMovedClear.isEnabled = true
        setAdjustButtonsEnabled(false)
        originalPose?.let {
            Log.d(TAG, "원래 위치 저장: t=(%.3f, %.3f, %.3f)".format(it.tx(), it.ty(), it.tz()))
        }
        status("삭제 완료 · '여기로 옮기기'로 ${label()}을(를) 새 위치에 놓을 수 있어요")
    }

    /** 이동된 사물/패널을 정리한다. 새 삭제 요청·선택 취소 시 호출된다. */
    fun disarm(announce: Boolean) {
        removeNode()
        armed = false
        placing = false
        dragging = false
        objectBitmap = null
        originalPose = null
        binding.movedObjectPanel.visibility = View.GONE
        if (announce) status("이동한 사물을 치웠습니다")
    }

    // ------------------------------------------------------------------- 배치

    private fun startPlacing() {
        if (!armed) return
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
        if (hitVertical != wantWall) {
            Toast.makeText(
                activity,
                "${label()}은(는) ${if (wantWall) "벽" else "바닥"}이 어울리지만 지금은 " +
                    "${if (hitVertical) "벽" else "바닥"}에 붙였어요",
                Toast.LENGTH_SHORT,
            ).show()
        }
        status("${label()} 이동 완료 · ＋－ 크기 · 회전 · 드래그로 조정")
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
    }

    private fun rotate() {
        if (node == null) return
        rotDeg = (rotDeg + 15f) % 360f
        applyChildTransforms()
    }

    // ------------------------------------------------------------------- 노드

    private fun setNode(anchor: Anchor, onVertical: Boolean) {
        removeNode()
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
        setAdjustButtonsEnabled(true)
        applyChildTransforms()
        Log.d(TAG, "이동 배치: type=$objectType vertical=$onVertical pose=${anchor.pose}")
    }

    /** 자식(이미지/라벨)의 회전·배율·오프셋을 평면 종류에 맞춰 다시 잡는다. */
    private fun applyChildTransforms() {
        val h = baseH * scaleF
        imageNode?.let {
            it.scale = Scale(scaleF)
            // 수직 평면(벽): 앵커 로컬 +Y 가 벽 바깥이라 quad 를 X -90° 세운다(결과 quad 와 동일).
            it.rotation = if (onVertical) Rotation(-90f, 0f, rotDeg) else Rotation(0f, rotDeg, 0f)
        }
        labelNode?.let {
            it.position = Position(0f, h + LABEL_GAP, 0f)
            it.rotation = if (onVertical) Rotation(-90f, 0f, 0f) else Rotation(0f, rotDeg, 0f)
        }
    }

    private fun removeNode() {
        node?.let {
            sceneView.removeChildNode(it)
            runCatching { it.anchor.detach() }
            runCatching { it.destroy() }
        }
        node = null
        imageNode = null
        labelNode = null
        setAdjustButtonsEnabled(false)
    }

    private fun setAdjustButtonsEnabled(enabled: Boolean) {
        binding.btnMovedShrink.isEnabled = enabled
        binding.btnMovedGrow.isEnabled = enabled
        binding.btnMovedRotate.isEnabled = enabled
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
        val OBJECT_LABELS = mapOf(
            "tv" to "TV", "sofa" to "소파", "table" to "테이블",
            "chair" to "의자", "shelf" to "선반", "other" to "사물",
        )
    }
}
