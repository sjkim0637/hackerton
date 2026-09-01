package com.example.arcubes

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.PixelCopy
import android.view.View
import android.widget.EditText
import android.widget.SeekBar
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.arcubes.databinding.ActivityMainBinding
import com.google.ar.core.Anchor
import com.google.ar.core.Config
import com.google.ar.core.Plane
import com.google.ar.core.TrackingState
import io.github.sceneview.ar.arcore.createAnchorOrNull
import io.github.sceneview.ar.node.AnchorNode
import io.github.sceneview.ar.scene.PlaneRenderer
import io.github.sceneview.material.setColor
import io.github.sceneview.material.setReflectance
import io.github.sceneview.material.setRoughness
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Scale
import io.github.sceneview.math.Size
import io.github.sceneview.math.colorOf
import io.github.sceneview.node.CubeNode
import io.github.sceneview.node.ImageNode
import io.github.sceneview.node.Node
import com.google.android.filament.MaterialInstance

/**
 * ARCore 큐브 배치 도구.
 *
 * - 평면 위 탭 -> 이름/실물 크기 입력 팝업 -> 반투명 큐브 + 이름표 생성
 *   (수평면=바닥/책상, 수직면=벽 모두 지원. 벽에서는 큐브를 세워서 붙인다)
 * - 큐브 길게 누르기(또는 탭) -> 선택(밝게 빛남) + 하단 조작 패널 표시
 * - 선택 상태에서 드래그 -> 평면 위에서 손가락을 따라 이동, 떼면 그 자리에 고정
 * - 패널의 ＋ / － -> 크기 조절 (최소/최대 제한)
 * - 배경 촬영 -> 지금 카메라 화면(큐브 제외)을 저장하고, 반투명 오버레이로 겹쳐
 *   같은 각도에서 "가구가 없어진 것처럼" 보이게 한다
 */
class MainActivity : AppCompatActivity() {

    private companion object {
        const val TAG = "ARCubes"
        const val MIN_SCALE = 0.3f
        const val MAX_SCALE = 3.0f
        const val SCALE_STEP = 1.15f

        /** 이름표 가로 폭(미터). 큐브 크기와 무관하게 고정. */
        const val LABEL_WIDTH_METERS = 0.1f
        /** 이름표를 큐브 윗면에서 얼마나 더 띄울지(미터). */
        const val LABEL_GAP_METERS = 0.05f

        val PLANE_TYPES = setOf(
            Plane.Type.HORIZONTAL_UPWARD_FACING,
            Plane.Type.HORIZONTAL_DOWNWARD_FACING,
            Plane.Type.VERTICAL,
        )

        // 반투명 = "아직 실제로 없는, 제안된 배치" 느낌.
        // 실내 조명에서도 형태가 보이도록 채도/알파를 조금 높게 잡는다.
        val COLOR_NORMAL = colorOf(r = 0.25f, g = 0.65f, b = 1.0f, a = 0.6f)
        val COLOR_SELECTED = colorOf(r = 0.5f, g = 1.0f, b = 1.0f, a = 0.82f)
    }

    /** 큐브 하나에 딸린 노드/상태 묶음. */
    private class CubeMarker(
        val anchorNode: AnchorNode,
        val cubeNode: CubeNode,
        val labelNode: ImageNode,
        /** 실물 크기(미터). +/- 로 조절하는 배율의 기준. */
        val baseSize: Size,
        var scaleFactor: Float,
        var name: String,
        val material: MaterialInstance,
        /** 수직 평면(벽)에 붙어 있으면 true. 큐브 방향/오프셋이 달라진다. */
        var onVerticalPlane: Boolean,
    )

    private lateinit var binding: ActivityMainBinding

    private val markers = mutableListOf<CubeMarker>()
    private var selected: CubeMarker? = null

    /** 팝업 입력을 기다리는, 아직 큐브가 안 붙은 앵커. */
    private var pendingAnchor: Anchor? = null
    private var pendingIsVertical = false
    private var draggingSelected = false
    private var dragIsVertical = false

    /** 저장된 "빈 배경" 사진. */
    private var backgroundBitmap: Bitmap? = null
    private val backgroundFile by lazy { File(filesDir, "empty_background.png") }
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    // 상태 로그 스팸 방지용.
    private var lastTrackingState: TrackingState? = null
    private var lastFailureReason: String? = null
    private var lastPlaneCount = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val sceneView = binding.sceneView
        sceneView.lifecycle = lifecycle

        sceneView.planeRenderer.isEnabled = true
        sceneView.planeRenderer.planeRendererMode = PlaneRenderer.PlaneRendererMode.RENDER_ALL

        sceneView.configureSession { _, config ->
            // 바닥/책상 같은 수평면 + 벽 같은 수직면 모두 인식.
            config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
            // 실내 조명에 맞춰 큐브 밝기를 자동 조정 (없으면 Filament 오브젝트가 새까맣게 보임).
            config.lightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR
        }

        sceneView.onSessionFailed = { exception ->
            Log.e(TAG, "AR 세션 실패", exception)
            binding.instructionText.text = "AR 세션 실패: ${exception.message}"
        }

        sceneView.onSessionUpdated = { session, frame ->
            // 이름표가 항상 카메라를 향하도록(빌보드).
            val cameraQuaternion = sceneView.cameraNode.worldQuaternion
            markers.forEach { it.labelNode.worldQuaternion = cameraQuaternion }

            logTrackingState(session, frame.camera.trackingState, frame.camera.trackingFailureReason.name)
        }

        sceneView.setOnGestureListener(
            onSingleTapConfirmed = { motionEvent, node ->
                val marker = markerOf(node)
                when {
                    marker != null -> select(marker)
                    selected != null -> deselect()
                    else -> startCreateFlow(motionEvent.x, motionEvent.y)
                }
            },
            onLongPress = { _, node ->
                markerOf(node)?.let { select(it) } ?: deselect()
            },
            onMoveBegin = { _, _, node ->
                // 큐브가 선택된 상태에서 손가락을 움직이기 시작하면 드래그 이동 시작.
                if (selected != null && markerOf(node).let { it == null || it == selected }) {
                    draggingSelected = true
                    selected?.anchorNode?.updateAnchorPose = false
                }
            },
            onMove = { _, motionEvent, _ ->
                if (draggingSelected) {
                    val marker = selected
                    val hit = sceneView.hitTestAR(
                        xPx = motionEvent.x,
                        yPx = motionEvent.y,
                        planeTypes = PLANE_TYPES,
                    )
                    if (marker != null && hit != null) {
                        marker.anchorNode.pose = hit.hitPose
                        dragIsVertical = isVerticalHit(hit)
                    }
                }
            },
            onMoveEnd = { _, _, _ ->
                if (draggingSelected) {
                    draggingSelected = false
                    finalizeDrag()
                }
            },
        )

        binding.btnGrow.setOnClickListener { resizeSelected(SCALE_STEP) }
        binding.btnShrink.setOnClickListener { resizeSelected(1f / SCALE_STEP) }
        binding.btnDeselect.setOnClickListener { deselect() }
        binding.btnDelete.setOnClickListener { deleteSelected() }

        setupBackgroundOverlay()
    }

    // ---------------------------------------------------------------- 배경 촬영 / 오버레이

    private fun setupBackgroundOverlay() {
        binding.btnCaptureBg.setOnClickListener { captureBackground() }
        binding.btnToggleBg.setOnClickListener { toggleBackgroundOverlay() }
        binding.opacitySeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) {
                binding.backgroundOverlay.alpha = (progress / 100f).coerceIn(0.05f, 1f)
            }
            override fun onStartTrackingTouch(bar: SeekBar) {}
            override fun onStopTrackingTouch(bar: SeekBar) {}
        })

        // 지난 실행에서 저장한 배경이 있으면 불러온다 (표시는 꺼둔 상태).
        if (backgroundFile.exists()) {
            runCatching { BitmapFactory.decodeFile(backgroundFile.absolutePath) }.getOrNull()?.let {
                backgroundBitmap = it
                binding.backgroundOverlay.setImageBitmap(it)
                binding.btnToggleBg.isEnabled = true
            }
        }
    }

    /**
     * 지금 카메라 화면(큐브·UI 제외)을 사진으로 찍어 저장한다.
     * PixelCopy 로 윈도우 표면을 읽되, 캡처 순간에는 큐브와 오버레이를 잠깐 숨긴다.
     */
    private fun captureBackground() {
        val sceneView = binding.sceneView
        if (sceneView.width == 0 || sceneView.height == 0) return

        val bitmap = Bitmap.createBitmap(sceneView.width, sceneView.height, Bitmap.Config.ARGB_8888)
        val location = IntArray(2)
        sceneView.getLocationInWindow(location)
        val rect = Rect(
            location[0], location[1],
            location[0] + sceneView.width, location[1] + sceneView.height,
        )

        val overlayWasVisible = binding.backgroundOverlay.visibility == View.VISIBLE
        binding.backgroundOverlay.visibility = View.GONE
        markers.forEach { it.anchorNode.isVisible = false }

        // 큐브 숨김이 AR 렌더 스레드에 반영될 시간을 준 뒤 캡처.
        sceneView.postDelayed({
            PixelCopy.request(window, rect, bitmap, { result ->
                markers.forEach { it.anchorNode.isVisible = true }
                if (result == PixelCopy.SUCCESS) {
                    backgroundBitmap = bitmap
                    binding.backgroundOverlay.setImageBitmap(bitmap)
                    binding.btnToggleBg.isEnabled = true
                    saveBackgroundBitmap(bitmap)
                    showBackgroundOverlay(true)
                    Toast.makeText(this, "빈 배경을 저장했습니다", Toast.LENGTH_SHORT).show()
                } else {
                    if (overlayWasVisible) binding.backgroundOverlay.visibility = View.VISIBLE
                    Toast.makeText(this, "촬영 실패 (코드 $result)", Toast.LENGTH_SHORT).show()
                }
            }, mainHandler)
        }, 120L)
    }

    private fun saveBackgroundBitmap(bitmap: Bitmap) {
        runCatching {
            FileOutputStream(backgroundFile).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }.onFailure { Log.w(TAG, "배경 저장 실패", it) }
    }

    private fun toggleBackgroundOverlay() {
        showBackgroundOverlay(binding.backgroundOverlay.visibility != View.VISIBLE)
    }

    private fun showBackgroundOverlay(show: Boolean) {
        if (show && backgroundBitmap == null) return
        binding.backgroundOverlay.visibility = if (show) View.VISIBLE else View.GONE
        binding.opacitySeekBar.visibility = if (show) View.VISIBLE else View.GONE
        binding.btnToggleBg.text = if (show) "배경 숨김" else "배경 표시"
    }

    // ---------------------------------------------------------------- 생성

    private fun startCreateFlow(xPx: Float, yPx: Float) {
        val hit = binding.sceneView.hitTestAR(xPx = xPx, yPx = yPx, planeTypes = PLANE_TYPES)
        val anchor = hit?.createAnchorOrNull()
        if (hit == null || anchor == null) {
            Toast.makeText(this, "격자가 보이는 평면(바닥/책상/벽) 위를 탭하세요", Toast.LENGTH_SHORT).show()
            return
        }
        pendingAnchor = anchor
        pendingIsVertical = isVerticalHit(hit)
        showCubeInfoDialog()
    }

    /** 히트한 Trackable 이 수직 평면(벽)인지. */
    private fun isVerticalHit(hit: com.google.ar.core.HitResult): Boolean =
        (hit.trackable as? Plane)?.type == Plane.Type.VERTICAL

    private fun showCubeInfoDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_cube_info, null)
        val nameField = view.findViewById<EditText>(R.id.editName)
        val widthField = view.findViewById<EditText>(R.id.editWidth)
        val heightField = view.findViewById<EditText>(R.id.editHeight)
        val depthField = view.findViewById<EditText>(R.id.editDepth)

        AlertDialog.Builder(this)
            .setTitle("큐브 정보 입력")
            .setView(view)
            .setCancelable(true)
            .setPositiveButton("생성") { _, _ ->
                val anchor = pendingAnchor ?: return@setPositiveButton
                pendingAnchor = null

                val name = nameField.text.toString().trim().ifEmpty { "큐브" }
                val widthCm = widthField.readCm()
                val heightCm = heightField.readCm()
                val depthCm = depthField.readCm()
                val baseSize = Size(widthCm / 100f, heightCm / 100f, depthCm / 100f)

                createCube(anchor, name, baseSize, pendingIsVertical)
            }
            .setNegativeButton("취소") { _, _ ->
                pendingAnchor?.detach()
                pendingAnchor = null
            }
            .setOnCancelListener {
                pendingAnchor?.detach()
                pendingAnchor = null
            }
            .show()
    }

    /** 입력값(cm)을 2~200 범위로 읽는다. 비었거나 잘못되면 10cm. */
    private fun EditText.readCm(): Float =
        text.toString().toFloatOrNull()?.coerceIn(2f, 200f) ?: 10f

    private fun createCube(anchor: Anchor, name: String, baseSize: Size, isVertical: Boolean) {
        val sceneView = binding.sceneView

        val material = sceneView.materialLoader.createColorInstance(color = COLOR_NORMAL)

        // 오프셋/회전은 노드에서 처리하므로 지오메트리는 원점 중심으로 만든다.
        val cubeNode = CubeNode(
            engine = sceneView.engine,
            size = baseSize,
            center = Position(0f),
            materialInstance = material,
        )

        val labelBitmap = makeLabelBitmap(name)
        val labelNode = ImageNode(
            materialLoader = sceneView.materialLoader,
            bitmap = labelBitmap,
            size = Size(
                x = LABEL_WIDTH_METERS,
                y = LABEL_WIDTH_METERS * labelBitmap.height / labelBitmap.width,
            ),
        ).apply { isTouchable = false }

        val anchorNode = AnchorNode(sceneView.engine, anchor).apply {
            isPositionEditable = false // 이동은 직접 제어한다.
            addChildNode(cubeNode)
            addChildNode(labelNode)
        }
        sceneView.addChildNode(anchorNode)

        val marker = CubeMarker(anchorNode, cubeNode, labelNode, baseSize, 1f, name, material, isVertical)
        markers += marker
        applyPlacement(marker)
        Log.d(
            TAG,
            "큐브 생성: '$name' size=${baseSize.x}x${baseSize.y}x${baseSize.z}m " +
                "vertical=$isVertical at ${anchor.pose}",
        )
    }

    /**
     * 큐브/이름표의 로컬 위치·회전을 평면 종류와 현재 배율에 맞춰 다시 잡는다.
     *
     * - 수평면: 큐브 아랫면이 평면에 닿도록 +Y 로 절반 높이만큼 올린다.
     * - 수직면(벽): 앵커 로컬 +Y 가 벽 바깥 방향이므로, 큐브를 X축 -90° 회전해서
     *   "높이"가 벽을 따라 서게 하고, +Y 로 절반 깊이만큼 밀어 뒷면을 벽에 붙인다.
     */
    private fun applyPlacement(marker: CubeMarker) {
        val f = marker.scaleFactor
        val s = marker.baseSize
        if (marker.onVerticalPlane) {
            marker.cubeNode.rotation = Rotation(x = -90f, y = 0f, z = 0f)
            marker.cubeNode.position = Position(x = 0f, y = s.z * f / 2f, z = 0f)
            marker.labelNode.position = Position(x = 0f, y = s.z * f + LABEL_GAP_METERS, z = 0f)
        } else {
            marker.cubeNode.rotation = Rotation(0f, 0f, 0f)
            marker.cubeNode.position = Position(x = 0f, y = s.y * f / 2f, z = 0f)
            marker.labelNode.position = Position(x = 0f, y = s.y * f + LABEL_GAP_METERS, z = 0f)
        }
    }

    // ---------------------------------------------------------------- 선택 / 이동 / 크기

    private fun markerOf(node: Node?): CubeMarker? {
        var current = node
        while (current != null) {
            markers.firstOrNull { it.cubeNode == current || it.anchorNode == current || it.labelNode == current }
                ?.let { return it }
            current = current.parent
        }
        return null
    }

    private fun select(marker: CubeMarker) {
        if (selected == marker) return
        deselect()
        selected = marker
        marker.material.setColor(COLOR_SELECTED)
        marker.material.setRoughness(0.1f)
        marker.material.setReflectance(1.0f)

        binding.selectionPanel.visibility = View.VISIBLE
        updatePanelText(marker)
    }

    private fun deselect() {
        selected?.let {
            it.material.setColor(COLOR_NORMAL)
            it.material.setRoughness(0.4f)
            it.material.setReflectance(0.5f)
        }
        selected = null
        binding.selectionPanel.visibility = View.GONE
    }

    private fun finalizeDrag() {
        val marker = selected ?: return
        val session = binding.sceneView.session
        val newPose = marker.anchorNode.pose
        val oldAnchor = marker.anchorNode.anchor
        val newAnchor = runCatching { session?.createAnchor(newPose) }.getOrNull()
        if (newAnchor != null) {
            marker.anchorNode.anchor = newAnchor
            oldAnchor.detach()
        }
        marker.anchorNode.updateAnchorPose = true
        marker.onVerticalPlane = dragIsVertical
        applyPlacement(marker)
        Log.d(TAG, "'${marker.name}' 이동 완료 (vertical=${marker.onVerticalPlane}) -> $newPose")
    }

    private fun resizeSelected(factor: Float) {
        val marker = selected ?: return
        marker.scaleFactor = (marker.scaleFactor * factor).coerceIn(MIN_SCALE, MAX_SCALE)
        marker.cubeNode.scale = Scale(marker.scaleFactor)
        applyPlacement(marker)
        updatePanelText(marker)
    }

    private fun deleteSelected() {
        val marker = selected ?: return
        binding.sceneView.removeChildNode(marker.anchorNode)
        runCatching { marker.anchorNode.anchor.detach() }
        runCatching { marker.anchorNode.destroy() }
        markers.remove(marker)
        selected = null
        binding.selectionPanel.visibility = View.GONE
        Log.d(TAG, "'${marker.name}' 삭제")
    }

    private fun updatePanelText(marker: CubeMarker) {
        val f = marker.scaleFactor
        val w = marker.baseSize.x * 100f * f
        val h = marker.baseSize.y * 100f * f
        val d = marker.baseSize.z * 100f * f
        binding.selectedNameText.text =
            "%s  ·  %.0f×%.0f×%.0f cm  (x%.2f)".format(marker.name, w, h, d, f)
    }

    // ---------------------------------------------------------------- 이름표 비트맵

    private fun makeLabelBitmap(text: String): Bitmap {
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.DEFAULT_BOLD
            textSize = 72f
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
        }
        val padX = 40f
        val padY = 28f
        val fm = textPaint.fontMetrics
        val textWidth = textPaint.measureText(text)
        val width = (textWidth + padX * 2).toInt().coerceAtLeast(8)
        val height = (fm.bottom - fm.top + padY * 2).toInt().coerceAtLeast(8)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(190, 0, 0, 0) }
        canvas.drawRoundRect(
            RectF(0f, 0f, width.toFloat(), height.toFloat()), 24f, 24f, bgPaint,
        )
        canvas.drawText(text, width / 2f, height / 2f - (fm.ascent + fm.descent) / 2f, textPaint)
        return bitmap
    }

    // ---------------------------------------------------------------- 진단 로그 + 안내 문구

    private fun logTrackingState(
        session: com.google.ar.core.Session,
        trackingState: TrackingState,
        failureReason: String,
    ) {
        val planes = session.getAllTrackables(Plane::class.java)
        val trackingPlanes = planes.count { it.trackingState == TrackingState.TRACKING }

        if (trackingState == lastTrackingState &&
            failureReason == lastFailureReason &&
            trackingPlanes == lastPlaneCount
        ) return

        lastTrackingState = trackingState
        lastFailureReason = failureReason
        lastPlaneCount = trackingPlanes
        Log.d(TAG, "tracking=$trackingState failureReason=$failureReason planes=$trackingPlanes/${planes.size}")

        if (selected == null && pendingAnchor == null) {
            binding.instructionText.text = when {
                trackingState != TrackingState.TRACKING ->
                    "추적 준비 중 ($failureReason) · 밝은 곳에서 폰을 좌우로 천천히 움직이세요"
                trackingPlanes == 0 ->
                    "평면 찾는 중 · 바닥/책상/벽을 비추며 폰을 움직이세요"
                else ->
                    "평면 $trackingPlanes 개 (바닥·벽) · 탭하면 큐브 생성, 길게 누르면 선택"
            }
        }
    }
}
