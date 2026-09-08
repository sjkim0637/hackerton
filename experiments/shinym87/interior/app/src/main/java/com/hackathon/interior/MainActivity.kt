package com.hackathon.interior

import android.os.Bundle
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

        binding.sceneView.setOnGestureListener(
            onSingleTapConfirmed = { event, node -> furniture.handleTap(event, node) },
            onLongPress = { _, node -> furniture.handleLongPress(node) },
            onMoveBegin = { _, event, node ->
                if (!moved.onDragBegin(event.x, event.y)) furniture.beginDrag(node)
            },
            onMove = { _, event, _ -> if (!moved.onDrag(event.x, event.y)) furniture.drag(event) },
            onMoveEnd = { _, event, _ -> if (!moved.onDragEnd()) furniture.endDrag(event) },
            onScale = { detector, _, _ ->
                if (!moved.onScale(detector.scaleFactor)) furniture.scaleSelectedBy(detector.scaleFactor)
            },
        )

        binding.btnGrow.setOnClickListener { furniture.scaleSelectedBy(FurnitureItem.SCALE_STEP) }
        binding.btnShrink.setOnClickListener { furniture.scaleSelectedBy(1f / FurnitureItem.SCALE_STEP) }
        binding.btnRotateLeft.setOnClickListener { furniture.rotateSelectedBy(-15f) }
        binding.btnRotateRight.setOnClickListener { furniture.rotateSelectedBy(15f) }
        binding.btnDeselect.setOnClickListener { furniture.deselect() }
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

        binding.serverUrlInput.setText(settings.serverBaseUrl)
        binding.btnWorkspaceHome.setOnClickListener { finish() }
        binding.btnAddFurniture.text = "잡지"
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
            "$name 선택됨 · ${if (anchor == "wall") "벽" else "바닥"}을 눌러 배치하세요. 사물 지우기도 바로 사용할 수 있습니다."
    }

    override fun onDestroy() {
        if (::depthPlacement.isInitialized) depthPlacement.release()
        super.onDestroy()
    }

    private fun renderSelectionPanel(item: FurnitureItem?) {
        if (item == null) {
            binding.selectionPanel.visibility = View.GONE
            return
        }
        binding.selectionPanel.visibility = View.VISIBLE
        val scale = item.scaleFactor
        binding.selectedNameText.text = "%s · %.0f×%.0f×%.0f cm (x%.2f · %.0f°)".format(
            item.name,
            item.baseSize.x * 100f * scale,
            item.baseSize.y * 100f * scale,
            item.baseSize.z * 100f * scale,
            scale,
            item.rotationDeg,
        )
    }
}
