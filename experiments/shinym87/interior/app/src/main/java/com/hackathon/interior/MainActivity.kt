package com.hackathon.interior

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.hackathon.interior.ar.ArSpaceController
import com.hackathon.interior.databinding.ActivityMainBinding
import com.hackathon.interior.furniture.CatalogController
import com.hackathon.interior.furniture.FurnitureController
import com.hackathon.interior.furniture.FurnitureItem
import com.hackathon.interior.keyframe.BackgroundKeyframe
import com.hackathon.interior.remove.MovedObjectController
import com.hackathon.interior.remove.RemovalController

/**
 * 카메라 기반 공간 편집 / AR 가구 재배치 시뮬레이터 — 공간·AR 작업 흐름의 진입점.
 *
 * 화면 구성은 네 조각으로 나뉜다.
 * - [ArSpaceController]  : 카메라 실행, AR 세션, 벽/바닥 평면 인식, hitTest
 * - [FurnitureController]: 탭 생성 · 드래그 이동 · 핀치/버튼 크기 조절 · 회전 · 삭제
 * - [CatalogController]  : "가구 추가" → 서버 카탈로그 목록 → 골라서 배치 (PHASE 5)
 * - [BackgroundKeyframe] : "빈 배경" 대표 이미지 캡처와 반투명 오버레이
 * - [RemovalController]  : TV 영역 지정 → 키프레임 캡처 → 서버 호출 → 결과를 벽에 적용
 * - [MovedObjectController]: 삭제한 사물을 다른 위치로 이동 + placements 서버 저장/복원
 *
 * MainActivity 는 이 조각들을 레이아웃 위젯과 제스처에 연결만 한다.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var space: ArSpaceController
    private lateinit var furniture: FurnitureController
    private lateinit var keyframe: BackgroundKeyframe
    private lateinit var removal: RemovalController
    private lateinit var moved: MovedObjectController
    private lateinit var catalog: CatalogController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val sceneView = binding.sceneView

        space = ArSpaceController(sceneView, lifecycle, binding.instructionText)

        furniture = FurnitureController(
            activity = this,
            sceneView = sceneView,
            hitTest = space::hitTest,
            hitTestPreferring = space::hitTestPreferring,
            scope = lifecycleScope,
            serverBaseUrl = { removal.serverBaseUrl() },
            onSelectionChanged = ::renderSelectionPanel,
        )

        // 캡처 직전: 가구 노드 + 평면 격자/특징점 시각화를 끈다 (AI 로 보내는 이미지에 안 찍히게).
        val beforeCapture = {
            furniture.setAllVisible(false)
            space.setPlaneVisualizationEnabled(false)
        }
        val afterCapture = {
            furniture.setAllVisible(true)
            space.setPlaneVisualizationEnabled(true)
        }

        keyframe = BackgroundKeyframe(
            activity = this,
            sceneView = sceneView,
            overlay = binding.backgroundOverlay,
            captureButton = binding.btnCaptureBg,
            toggleButton = binding.btnToggleBg,
            opacityBar = binding.opacitySeekBar,
            beforeCapture = beforeCapture,
            afterCapture = afterCapture,
        )

        removal = RemovalController(
            activity = this,
            scope = lifecycleScope,
            sceneView = sceneView,
            space = space,
            binding = binding,
            onBeforeCapture = beforeCapture,
            onAfterCapture = afterCapture,
            onRemovalApplied = { sid, jid, type, bmp, pose, src, w, h ->
                moved.arm(sid, jid, type, bmp, pose, src, w, h)
            },
            onRemovalCleared = { moved.disarm() },
        )

        moved = MovedObjectController(
            activity = this,
            sceneView = sceneView,
            space = space,
            binding = binding,
            scope = lifecycleScope,
            serverBaseUrl = { removal.serverBaseUrl() },
            furnitureHasSelection = furniture::hasSelection,
            status = { binding.removalStatusText.text = it },
            onAlsoRestore = { furniture.restoreCatalogFromServer() },  // "서버 배치 복원" 이 카탈로그도 복원
        )

        // PHASE 5: 서버 카탈로그에서 새 가구 추가 (삭제-후-재배치와 별개 진입점).
        catalog = CatalogController(
            activity = this,
            scope = lifecycleScope,
            binding = binding,
            serverBaseUrl = { removal.serverBaseUrl() },
            onOpen = { furniture.ensureCatalogScene() },  // "가구 추가" 최초에 scene 확보 + 복원
            onPick = { item, thumb ->
                furniture.beginCatalogPlacement(
                    name = item.name,
                    widthM = item.widthM, heightM = item.heightM, depthM = item.depthM,
                    wantWall = item.anchorHint == "wall",
                    thumb = thumb,
                    catalogItemId = item.id, objectType = item.category,
                )
                binding.instructionText.text =
                    "‘${item.name}’ — ${if (item.anchorHint == "wall") "벽" else "바닥"}을 탭해 배치하세요"
            },
        )

        space.onFrame = {
            furniture.billboard()
            removal.onFrame()   // 결과 quad 를 벽 앵커에 스무딩해서 고정
        }
        space.isIdle = { furniture.isIdle() }

        // 이동된 사물이 제스처를 먼저 볼 기회를 준다(처리하면 true → 큐브로 안 넘어감).
        sceneView.setOnGestureListener(
            onSingleTapConfirmed = { me, node -> if (!moved.onTap(me.x, me.y)) furniture.handleTap(me, node) },
            onLongPress = { _, node -> furniture.handleLongPress(node) },
            onMoveBegin = { _, me, node -> if (!moved.onDragBegin(me.x, me.y)) furniture.beginDrag(node) },
            onMove = { _, me, _ -> if (!moved.onDrag(me.x, me.y)) furniture.drag(me) },
            onMoveEnd = { _, _, _ -> if (!moved.onDragEnd()) furniture.endDrag() },
            onScale = { detector, _, _ ->
                if (!moved.onScale(detector.scaleFactor)) furniture.scaleSelectedBy(detector.scaleFactor)
            },
        )

        binding.btnGrow.setOnClickListener { furniture.scaleSelectedBy(FurnitureItem.SCALE_STEP) }
        binding.btnShrink.setOnClickListener { furniture.scaleSelectedBy(1f / FurnitureItem.SCALE_STEP) }
        binding.btnRotate.setOnClickListener { furniture.rotateSelectedBy(15f) }
        binding.btnDeselect.setOnClickListener { furniture.deselect() }
        binding.btnDelete.setOnClickListener { furniture.deleteSelected() }
    }

    /** 선택된 가구가 있으면 하단 조작 패널을 채우고, 없으면 숨긴다. */
    private fun renderSelectionPanel(item: FurnitureItem?) {
        if (item == null) {
            binding.selectionPanel.visibility = View.GONE
            return
        }
        binding.selectionPanel.visibility = View.VISIBLE
        val f = item.scaleFactor
        val w = item.baseSize.x * 100f * f
        val h = item.baseSize.y * 100f * f
        val d = item.baseSize.z * 100f * f
        binding.selectedNameText.text =
            "%s  ·  %.0f×%.0f×%.0f cm  (x%.2f)".format(item.name, w, h, d, f)
    }
}
