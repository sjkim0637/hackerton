package com.hackathon.interior

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.hackathon.interior.ar.ArSpaceController
import com.hackathon.interior.ar.DepthPlacementController
import com.hackathon.interior.databinding.ActivityMainBinding
import com.hackathon.interior.furniture.FurnitureController
import com.hackathon.interior.furniture.FurnitureItem
import com.hackathon.interior.keyframe.BackgroundKeyframe
import com.hackathon.interior.remove.MovedObjectController
import com.hackathon.interior.remove.RemovalController
import com.hackathon.interior.settings.AppSettings

/** AR 배치와 사진 속 사물 지우기를 한 카메라 화면에서 제공한다. */
class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var space: ArSpaceController
    private lateinit var furniture: FurnitureController
    private lateinit var depthPlacement: DepthPlacementController
    private lateinit var keyframe: BackgroundKeyframe
    private lateinit var removal: RemovalController
    private lateinit var moved: MovedObjectController
    private lateinit var settings: AppSettings

    /** TEMP-DIAG: onMove 는 초당 수십 번 → 15회마다 한 줄만 찍는다. */
    private var moveEventLog = 0
    private var toolsExpanded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        settings = AppSettings(this)

        space = ArSpaceController(binding.sceneView, lifecycle, binding.instructionText)
        depthPlacement = DepthPlacementController()
        furniture = FurnitureController(
            activity = this,
            sceneView = binding.sceneView,
            hitTest = space::hitTest,
            hitTestPreferring = space::hitTestPreferring,
            scope = lifecycleScope,
            serverBaseUrl = { settings.serverBaseUrl },
            validatePlacement = { x, y, size, wall, callback ->
                depthPlacement.validate(space.latestFrame, x, y, size, wall, callback)
            },
            onSelectionChanged = ::renderSelectionPanel,
        )

        val beforeCapture = {
            furniture.setAllVisible(false)
            space.setPlaneVisualizationEnabled(false)
        }
        val afterCapture = {
            furniture.setAllVisible(true)
            space.setPlaneVisualizationEnabled(true)
        }

        // "빈 배경" 오버레이. PHASE 5 데모에서는 버튼/슬라이더를 layout 에서 gone 처리해
        // 사실상 비활성이다(효과 미미 + 시나리오에 없음). 배선은 되돌리기 쉽게 남겨둔다.
        keyframe = BackgroundKeyframe(
            activity = this,
            sceneView = binding.sceneView,
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
            sceneView = binding.sceneView,
            space = space,
            binding = binding,
            serverBaseUrl = { settings.serverBaseUrl },
            onBeforeCapture = beforeCapture,
            onAfterCapture = afterCapture,
            onRemovalApplied = { sid, jid, type, bmp, pose, src, w, h ->
                moved.arm(sid, jid, type, bmp, pose, src, w, h)
            },
            onRemovalCleared = { moved.disarm() },
        )

        moved = MovedObjectController(
            activity = this,
            sceneView = binding.sceneView,
            space = space,
            binding = binding,
            scope = lifecycleScope,
            serverBaseUrl = { settings.serverBaseUrl },
            furnitureHasSelection = furniture::hasSelection,
            status = { binding.removalStatusText.text = it },
            onAlsoRestore = { furniture.restoreCatalogFromServer() },
        )

        space.onFrame = {
            space.latestFrame?.let(depthPlacement::onFrame)
            furniture.billboard()
            removal.onFrame()
            moved.onFrame()
        }
        space.isIdle = { furniture.isIdle() }

        // 이동 마커는 탭이 아니라 드래그로만 옮긴다 → 탭은 그대로 큐브/카탈로그 몫.
        // TEMP-DIAG: 제스처가 앱에 실제로 도달하는지 / 이동 마커가 먹는지 logcat 추적 (tag InteriorAR).
        binding.sceneView.setOnGestureListener(
            onSingleTapConfirmed = { event, node ->
                Log.d(TAG, "[gesture] tap @(${event.x.toInt()},${event.y.toInt()}) → furniture.handleTap")
                furniture.handleTap(event, node)
            },
            onLongPress = { _, node -> furniture.handleLongPress(node) },
            onMoveBegin = { _, event, node ->
                val byMoved = moved.onDragBegin(event.x, event.y)
                Log.d(TAG, "[gesture] moveBegin @(${event.x.toInt()},${event.y.toInt()}) movedTook=$byMoved")
                if (!byMoved) furniture.beginDrag(node)
            },
            onMove = { _, event, _ ->
                val byMoved = moved.onDrag(event.x, event.y)
                if (++moveEventLog % 15 == 0) {
                    Log.d(TAG, "[gesture] move #$moveEventLog @(${event.x.toInt()},${event.y.toInt()}) movedTook=$byMoved")
                }
                if (!byMoved) furniture.drag(event)
            },
            onMoveEnd = { _, event, _ ->
                val byMoved = moved.onDragEnd()
                Log.d(TAG, "[gesture] moveEnd movedTook=$byMoved")
                if (!byMoved) furniture.endDrag(event)
            },
            onScale = { detector, _, _ ->
                if (!moved.onScale(detector.scaleFactor)) furniture.scaleSelectedBy(detector.scaleFactor)
            },
        )

        binding.btnGrow.setOnClickListener { furniture.scaleSelectedBy(FurnitureItem.SCALE_STEP) }
        binding.btnShrink.setOnClickListener { furniture.scaleSelectedBy(1f / FurnitureItem.SCALE_STEP) }
        binding.btnRotateLeft.setOnClickListener { furniture.rotateSelectedBy(-15f) }
        binding.btnRotateRight.setOnClickListener { furniture.rotateSelectedBy(15f) }
        binding.btnDeselect.setOnClickListener {
            furniture.deselect()
            setToolsExpanded(false)
        }
        binding.btnDelete.setOnClickListener { furniture.deleteSelected() }

        setupUnifiedWorkspace()
    }

    private fun setupUnifiedWorkspace() {
        binding.homeScreen.visibility = View.GONE
        binding.catalogPanel.visibility = View.GONE
        binding.settingsScreen.visibility = View.GONE
        binding.arTopPanel.visibility = View.VISIBLE
        binding.arPrimaryActions.visibility = View.VISIBLE
        binding.removalTypeRow.visibility = View.VISIBLE
        binding.removalSelectionRow.visibility = View.VISIBLE
        binding.removalRequestRow.visibility = View.VISIBLE
        binding.removalStatusText.visibility = View.VISIBLE
        setToolsExpanded(false)

        binding.serverUrlInput.setText(settings.serverBaseUrl)
        binding.btnWorkspaceHome.setOnClickListener { finish() }
        binding.btnArTools.setOnClickListener { setToolsExpanded(!toolsExpanded) }
        binding.movedObjectPanel.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            if (binding.movedObjectPanel.visibility == View.VISIBLE && !toolsExpanded) {
                setToolsExpanded(true)
            }
        }
        binding.btnAddFurniture.text = "다른 가구"
        binding.btnRemovalTools.setOnClickListener {
            val expanded = binding.removalTools.visibility != View.VISIBLE
            if (!expanded && binding.bboxSelectionView.isSelecting) removal.toggleSelectionMode()
            binding.removalTools.visibility = if (expanded) View.VISIBLE else View.GONE
            binding.btnRemovalTools.text = if (expanded) "사물 지우기 ▴" else "사물 지우기 ▾"
            binding.btnRemovalTools.contentDescription = if (expanded) "사물 지우기 도구 접기" else "사물 지우기 도구 펼치기"
        }
        binding.btnAddFurniture.setOnClickListener { finish() }
        binding.btnWorkspaceSettings.setOnClickListener {
            binding.serverUrlInput.setText(settings.serverBaseUrl)
            binding.settingsScreen.visibility = View.VISIBLE
        }
        binding.btnSettingsSave.setOnClickListener {
            settings.serverBaseUrl = binding.serverUrlInput.text?.toString().orEmpty()
            binding.serverUrlInput.setText(settings.serverBaseUrl)
            Toast.makeText(this, "서버 주소를 저장했습니다.", Toast.LENGTH_SHORT).show()
            binding.settingsScreen.visibility = View.GONE
        }
        binding.btnSettingsCancel.setOnClickListener {
            binding.serverUrlInput.setText(settings.serverBaseUrl)
            binding.settingsScreen.visibility = View.GONE
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.settingsScreen.visibility == View.VISIBLE) {
                    binding.settingsScreen.visibility = View.GONE
                } else if (toolsExpanded) {
                    setToolsExpanded(false)
                } else finish()
            }
        })
        applyMagazineSelection()
    }

    private fun applyMagazineSelection() {
        val name = intent.getStringExtra(CatalogActivity.EXTRA_OBJECT_NAME)
        if (name == null) {
            binding.instructionText.text = "AR 배치와 사진 속 사물 지우기를 한 화면에서 사용할 수 있습니다."
            return
        }
        val anchor = intent.getStringExtra(CatalogActivity.EXTRA_OBJECT_ANCHOR) ?: "floor"
        furniture.ensureCatalogScene()
        furniture.beginCatalogPlacement(
            name = name,
            widthM = intent.getFloatExtra(CatalogActivity.EXTRA_OBJECT_WIDTH_M, 0.8f),
            heightM = intent.getFloatExtra(CatalogActivity.EXTRA_OBJECT_HEIGHT_M, 0.8f),
            depthM = intent.getFloatExtra(CatalogActivity.EXTRA_OBJECT_DEPTH_M, 0.8f),
            wantWall = anchor == "wall",
            catalogItemId = intent.getStringExtra(CatalogActivity.EXTRA_OBJECT_ID),
            objectType = intent.getStringExtra(CatalogActivity.EXTRA_OBJECT_CATEGORY) ?: "other",
        )
        binding.instructionText.text =
            "$name\n${if (anchor == "wall") "벽" else "바닥"}을 넓게 비춘 뒤 놓을 곳을 탭하세요"
    }

    override fun onDestroy() {
        if (::removal.isInitialized) removal.release()
        if (::depthPlacement.isInitialized) depthPlacement.release()
        super.onDestroy()
    }

    private fun renderSelectionPanel(item: FurnitureItem?) {
        if (item == null) {
            binding.selectionPanel.visibility = View.GONE
            binding.arQuickActions.visibility = View.VISIBLE
            return
        }
        binding.selectionPanel.visibility = View.VISIBLE
        // 가구를 고르는 동안에는 관련 없는 전역 메뉴를 감춰 편집 바만 남긴다.
        binding.arQuickActions.visibility = View.GONE
        setToolsExpanded(true)
        val scale = item.scaleFactor
        binding.selectedNameText.text = "%s  ·  %.0f × %.0f × %.0f cm  ·  %.0f°".format(
            item.name,
            item.baseSize.x * 100f * scale,
            item.baseSize.y * 100f * scale,
            item.baseSize.z * 100f * scale,
            item.rotationDeg,
        )
    }

    private fun setToolsExpanded(expanded: Boolean) {
        if (!expanded && binding.bboxSelectionView.isSelecting) removal.toggleSelectionMode()
        toolsExpanded = expanded
        binding.arToolsPanel.visibility = if (expanded) View.VISIBLE else View.GONE
        // 열린 도구 시트와 FAB가 같은 우하단 영역을 차지하지 않도록 한다.
        binding.btnArTools.visibility = if (expanded) View.GONE else View.VISIBLE
        binding.btnArTools.contentDescription = if (expanded) "AR 도구 접기" else "AR 도구 펼치기"
        binding.btnArTools.isSelected = expanded
    }

    private companion object {
        const val TAG = "InteriorAR"
    }
}
