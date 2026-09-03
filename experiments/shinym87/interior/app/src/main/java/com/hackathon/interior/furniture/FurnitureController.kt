package com.hackathon.interior.furniture

import android.app.Activity
import android.graphics.Bitmap
import android.util.Log
import android.view.MotionEvent
import android.widget.Toast
import com.google.ar.core.Anchor
import com.google.ar.core.HitResult
import com.hackathon.interior.ar.PlaneKind
import com.hackathon.interior.ui.FurnitureInfoDialog
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.ar.arcore.createAnchorOrNull
import io.github.sceneview.ar.node.AnchorNode
import io.github.sceneview.material.setColor
import io.github.sceneview.material.setReflectance
import io.github.sceneview.material.setRoughness
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Scale
import io.github.sceneview.math.Size
import io.github.sceneview.node.CubeNode
import io.github.sceneview.node.ImageNode
import io.github.sceneview.node.Node

/**
 * 가구(현재는 반투명 큐브)의 생성 · 선택 · 이동 · 크기 조절 · 삭제를 담당한다.
 *
 * 설계서 "사용자 1 — 공간 / AR" 작업 흐름 중
 * - 임시 가구 배치 (탭 + 이름/크기 입력, 또는 PHASE 5 서버 카탈로그에서 선택)
 * - 가구 이동 (드래그) · 크기 조절 (핀치 / ＋－) · 회전 (회전 버튼)
 *
 * 카탈로그 가구도 같은 [FurnitureItem]/제스처 로직을 그대로 쓴다. 표시만 썸네일이 있으면
 * 큐브 대신 이미지 quad 이고, 없으면 기존처럼 이름표 붙은 반투명 큐브다.
 */
class FurnitureController(
    private val activity: Activity,
    private val sceneView: ARSceneView,
    private val hitTest: (Float, Float) -> HitResult?,
    /** 사물 종류에 맞는 평면(벽/바닥) 우선 hitTest. 카탈로그 배치·드래그에 쓴다. */
    private val hitTestPreferring: (Float, Float, Boolean) -> HitResult?,
    /** 선택 대상이나 크기가 바뀔 때 호출. null 이면 선택 해제. */
    private val onSelectionChanged: (FurnitureItem?) -> Unit,
) {

    private val items = mutableListOf<FurnitureItem>()
    private var selected: FurnitureItem? = null

    /** 팝업 입력을 기다리는, 아직 가구가 안 붙은 앵커. */
    private var pendingAnchor: Anchor? = null
    private var pendingIsVertical = false

    /** PHASE 5: 카탈로그에서 고른 뒤, 평면 탭을 기다리는 가구. null 이면 카탈로그 배치 모드 아님. */
    private var pendingCatalog: PendingCatalog? = null

    private class PendingCatalog(
        val name: String,
        val size: Size,
        val wantWall: Boolean,
        val thumb: Bitmap?,
    )

    private var draggingSelected = false
    private var dragIsVertical = false

    /** 선택/입력 중이 아니면 true. 안내 문구 자동 갱신 조건으로 쓰인다. */
    fun isIdle(): Boolean = selected == null && pendingAnchor == null && pendingCatalog == null

    // ------------------------------------------------- PHASE 5: 카탈로그 가구 배치

    /**
     * 카탈로그에서 한 항목을 골랐다. 이제 [wantWall] 이면 벽, 아니면 바닥을 탭하면
     * 그 자리에 이 가구를 배치한다. [thumb] 가 있으면 이미지로, 없으면 큐브+이름표로.
     */
    fun beginCatalogPlacement(
        name: String, widthM: Float, heightM: Float, depthM: Float,
        wantWall: Boolean, thumb: Bitmap?,
    ) {
        deselect()
        pendingCatalog = PendingCatalog(name, Size(widthM, heightM, depthM), wantWall, thumb)
    }

    /** 카탈로그 배치 대기 취소. */
    fun cancelCatalogPlacement() {
        pendingCatalog = null
    }

    fun isPlacingCatalog(): Boolean = pendingCatalog != null

    private fun placeCatalog(xPx: Float, yPx: Float) {
        val pc = pendingCatalog ?: return
        val hit = hitTestPreferring(xPx, yPx, pc.wantWall)
        val anchor = hit?.createAnchorOrNull()
        if (hit == null || anchor == null) {
            Toast.makeText(
                activity,
                "격자가 보이는 ${if (pc.wantWall) "벽" else "바닥"} 위를 탭하세요",
                Toast.LENGTH_SHORT,
            ).show()
            return
        }
        pendingCatalog = null
        createFurniture(anchor, pc.name, pc.size, PlaneKind.isVerticalHit(hit), pc.thumb)
    }

    /** 지금 선택된 큐브가 있는지. (제스처를 큐브 vs 이동된 사물 중 누구에게 줄지 판단용) */
    fun hasSelection(): Boolean = selected != null

    /** 이름표가 항상 카메라를 향하도록(빌보드) 매 프레임 갱신한다. */
    fun billboard() {
        val cameraQuaternion = sceneView.cameraNode.worldQuaternion
        items.forEach { it.labelNode.worldQuaternion = cameraQuaternion }
    }

    fun setAllVisible(visible: Boolean) {
        items.forEach { it.anchorNode.isVisible = visible }
    }

    // ---------------------------------------------------------------- 제스처 진입점

    /**
     * 단일 탭:
     * - 가구를 탭하면 선택
     * - 카탈로그 배치 대기 중이면(빈 곳 탭) 그 자리에 카탈로그 가구 배치
     * - 선택된 게 있으면 선택 해제
     * - 그 외 빈 곳 탭이면 이름/크기 입력 다이얼로그로 큐브 생성
     */
    fun handleTap(motionEvent: MotionEvent, node: Node?) {
        val item = markerOf(node)
        when {
            item != null -> select(item)
            pendingCatalog != null -> placeCatalog(motionEvent.x, motionEvent.y)
            selected != null -> deselect()
            else -> startCreateFlow(motionEvent.x, motionEvent.y)
        }
    }

    fun handleLongPress(node: Node?) {
        markerOf(node)?.let { select(it) } ?: deselect()
    }

    fun beginDrag(node: Node?) {
        // 가구가 선택된 상태에서 손가락을 움직이기 시작하면 드래그 이동 시작.
        if (selected != null && markerOf(node).let { it == null || it == selected }) {
            draggingSelected = true
            selected?.anchorNode?.updateAnchorPose = false
        }
    }

    fun drag(motionEvent: MotionEvent) {
        if (!draggingSelected) return
        val item = selected ?: return
        // 벽 가구는 벽만, 바닥 가구는 바닥만 따라가도록 종류에 맞는 평면을 우선한다.
        val hit = hitTestPreferring(motionEvent.x, motionEvent.y, item.onVerticalPlane)
            ?: hitTest(motionEvent.x, motionEvent.y) ?: return
        item.anchorNode.pose = hit.hitPose
        dragIsVertical = PlaneKind.isVerticalHit(hit)
    }

    fun endDrag() {
        if (!draggingSelected) return
        draggingSelected = false
        finalizeDrag()
    }

    /** 핀치 또는 ＋－ 버튼에서 호출. factor 를 현재 배율에 곱한다. */
    fun scaleSelectedBy(factor: Float) {
        val item = selected ?: return
        if (draggingSelected) return
        item.scaleFactor = (item.scaleFactor * factor)
            .coerceIn(FurnitureItem.MIN_SCALE, FurnitureItem.MAX_SCALE)
        item.cubeNode.scale = Scale(item.scaleFactor)
        applyPlacement(item)
        onSelectionChanged(item)
    }

    /** "회전" 버튼에서 호출. 평면 안에서 [deg] 만큼 누적 회전 (큐브·이미지 공통). */
    fun rotateSelectedBy(deg: Float) {
        val item = selected ?: return
        if (draggingSelected) return
        item.rotationDeg = (item.rotationDeg + deg).mod(360f)
        applyPlacement(item)
    }

    fun deleteSelected() {
        val item = selected ?: return
        sceneView.removeChildNode(item.anchorNode)
        runCatching { item.anchorNode.anchor.detach() }
        runCatching { item.anchorNode.destroy() }
        items.remove(item)
        selected = null
        onSelectionChanged(null)
        Log.d(TAG, "'${item.name}' 삭제")
    }

    // ---------------------------------------------------------------- 생성

    private fun startCreateFlow(xPx: Float, yPx: Float) {
        val hit = hitTest(xPx, yPx)
        val anchor = hit?.createAnchorOrNull()
        if (hit == null || anchor == null) {
            Toast.makeText(activity, "격자가 보이는 평면(바닥/책상/벽) 위를 탭하세요", Toast.LENGTH_SHORT).show()
            return
        }
        pendingAnchor = anchor
        pendingIsVertical = PlaneKind.isVerticalHit(hit)

        FurnitureInfoDialog.show(
            activity = activity,
            onCreate = { name, baseSize ->
                val pending = pendingAnchor ?: return@show
                pendingAnchor = null
                createFurniture(pending, name, baseSize, pendingIsVertical)
            },
            onCancel = {
                pendingAnchor?.detach()
                pendingAnchor = null
            },
        )
    }

    private fun createFurniture(
        anchor: Anchor,
        name: String,
        baseSize: Size,
        isVertical: Boolean,
        thumb: Bitmap? = null,
    ) {
        val material = sceneView.materialLoader.createColorInstance(color = FurnitureItem.COLOR_NORMAL)

        // 오프셋/회전은 노드에서 처리하므로 지오메트리는 원점 중심으로 만든다.
        val cubeNode = CubeNode(
            engine = sceneView.engine,
            size = baseSize,
            center = Position(0f),
            materialInstance = material,
        )

        val labelBitmap = LabelRenderer.make(name)
        val labelNode = ImageNode(
            materialLoader = sceneView.materialLoader,
            bitmap = labelBitmap,
            size = Size(
                x = FurnitureItem.LABEL_WIDTH_METERS,
                y = FurnitureItem.LABEL_WIDTH_METERS * labelBitmap.height / labelBitmap.width,
            ),
        ).apply { isTouchable = false }

        // 카탈로그 썸네일이 있으면 이미지 quad 로 표시하고 큐브는 숨긴다(형태 프록시로만 유지).
        val imageNode: ImageNode? = thumb?.let {
            ImageNode(
                materialLoader = sceneView.materialLoader,
                bitmap = it,
                size = Size(baseSize.x, baseSize.y),
            ).apply { isTouchable = false }
        }
        cubeNode.isVisible = imageNode == null

        val anchorNode = AnchorNode(sceneView.engine, anchor).apply {
            isPositionEditable = false // 이동은 직접 제어한다.
            addChildNode(cubeNode)
            addChildNode(labelNode)
            imageNode?.let { addChildNode(it) }
        }
        sceneView.addChildNode(anchorNode)

        val item = FurnitureItem(
            anchorNode, cubeNode, labelNode, baseSize, 1f, name, material, isVertical,
            imageNode = imageNode,
        )
        items += item
        select(item)          // 방금 놓은 가구를 바로 선택 → 조작 패널 표시
        applyPlacement(item)
        Log.d(
            TAG,
            "가구 생성: '$name' size=${baseSize.x}x${baseSize.y}x${baseSize.z}m " +
                "vertical=$isVertical thumb=${imageNode != null} at ${anchor.pose}",
        )
    }

    /**
     * 큐브/이름표의 로컬 위치·회전을 평면 종류와 현재 배율에 맞춰 다시 잡는다.
     *
     * - 수평면: 큐브 아랫면이 평면에 닿도록 +Y 로 절반 높이만큼 올린다.
     * - 수직면(벽): 앵커 로컬 +Y 가 벽 바깥 방향이므로, 큐브를 X축 -90° 회전해서
     *   "높이"가 벽을 따라 서게 하고, +Y 로 절반 깊이만큼 밀어 뒷면을 벽에 붙인다.
     */
    private fun applyPlacement(item: FurnitureItem) {
        val f = item.scaleFactor
        val s = item.baseSize
        val r = item.rotationDeg
        if (item.onVerticalPlane) {
            val h = s.z * f
            item.cubeNode.rotation = Rotation(x = -90f, y = 0f, z = r)
            item.cubeNode.position = Position(x = 0f, y = h / 2f, z = 0f)
            item.labelNode.position = Position(x = 0f, y = h + FurnitureItem.LABEL_GAP_METERS, z = 0f)
            item.imageNode?.let {
                it.scale = Scale(f)
                it.rotation = Rotation(x = -90f, y = 0f, z = r)
                it.position = Position(x = 0f, y = h / 2f, z = 0f)
            }
        } else {
            val h = s.y * f
            item.cubeNode.rotation = Rotation(0f, r, 0f)
            item.cubeNode.position = Position(x = 0f, y = h / 2f, z = 0f)
            item.labelNode.position = Position(x = 0f, y = h + FurnitureItem.LABEL_GAP_METERS, z = 0f)
            item.imageNode?.let {
                it.scale = Scale(f)
                it.rotation = Rotation(x = 0f, y = r, z = 0f)
                it.position = Position(x = 0f, y = h / 2f, z = 0f)
            }
        }
    }

    // ---------------------------------------------------------------- 선택 / 이동

    private fun markerOf(node: Node?): FurnitureItem? {
        var current = node
        while (current != null) {
            items.firstOrNull {
                it.cubeNode == current || it.anchorNode == current ||
                    it.labelNode == current || it.imageNode == current
            }?.let { return it }
            current = current.parent
        }
        return null
    }

    private fun select(item: FurnitureItem) {
        if (selected == item) return
        deselect()
        selected = item
        item.material.setColor(FurnitureItem.COLOR_SELECTED)
        item.material.setRoughness(0.1f)
        item.material.setReflectance(1.0f)
        onSelectionChanged(item)
    }

    fun deselect() {
        selected?.let {
            it.material.setColor(FurnitureItem.COLOR_NORMAL)
            it.material.setRoughness(0.4f)
            it.material.setReflectance(0.5f)
        }
        selected = null
        onSelectionChanged(null)
    }

    private fun finalizeDrag() {
        val item = selected ?: return
        val session = sceneView.session
        val newPose = item.anchorNode.pose
        val oldAnchor = item.anchorNode.anchor
        val newAnchor = runCatching { session?.createAnchor(newPose) }.getOrNull()
        if (newAnchor != null) {
            item.anchorNode.anchor = newAnchor
            oldAnchor.detach()
        }
        item.anchorNode.updateAnchorPose = true
        item.onVerticalPlane = dragIsVertical
        applyPlacement(item)
        Log.d(TAG, "'${item.name}' 이동 완료 (vertical=${item.onVerticalPlane}) -> $newPose")
    }

    private companion object {
        const val TAG = "InteriorAR"
    }
}
