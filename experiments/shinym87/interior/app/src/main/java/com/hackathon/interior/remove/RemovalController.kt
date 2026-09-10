package com.hackathon.interior.remove

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import android.view.View
import android.widget.Toast
import com.google.ar.core.Pose
import com.hackathon.interior.ar.ArSpaceController
import com.hackathon.interior.databinding.ActivityMainBinding
import com.hackathon.interior.rgbd.OnDeviceRgbdEditor
import com.hackathon.interior.rgbd.PlaceableObject
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.ar.node.AnchorNode
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Size
import io.github.sceneview.node.ImageNode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Local-only RGB-D removal entry point. The capture, MobileSAM, LaMa and the
 * resulting 2.5D object are all derived on the device; no scene/job/image upload
 * is made from this controller.
 */
class RemovalController(
    private val activity: Activity,
    private val scope: CoroutineScope,
    private val sceneView: ARSceneView,
    private val space: ArSpaceController,
    private val binding: ActivityMainBinding,
    @Suppress("UNUSED_PARAMETER") private val serverBaseUrl: () -> String,
    private val onBeforeCapture: () -> Unit = {},
    private val onAfterCapture: () -> Unit = {},
    private val onRemovalApplied: (String, String, PlaceableObject, Pose, FloatArray?) -> Unit = { _, _, _, _, _ -> },
    private val onRemovalCleared: () -> Unit = {},
) : AutoCloseable {
    private val editor = OnDeviceRgbdEditor(activity.applicationContext)
    private var selectionMode = false
    private var selection: RectF? = null
    private var busy = false
    private var backgroundPatchNode: AnchorNode? = null

    init {
        binding.bboxSelectionView.onRectFinalized = { rect ->
            selection = RectF(rect)
            selectionMode = false
            binding.bboxSelectionView.isSelecting = false
            binding.bboxSelectionView.visibility = View.VISIBLE
            binding.btnClearSelection.visibility = View.VISIBLE
            refresh()
            status("선택됨 · 기기 내부에서 마스크와 깊이를 캡처합니다")
        }
        binding.btnTvSelectMode.setOnClickListener { toggleSelectionMode() }
        binding.btnClearSelection.setOnClickListener { clearSelection() }
        binding.btnRequestRemove.setOnClickListener { requestRemoval() }
        binding.btnToggleRemoval.setOnClickListener { togglePreview() }
        refresh()
    }

    fun toggleSelectionMode() {
        if (busy) return
        selectionMode = !selectionMode
        binding.bboxSelectionView.isSelecting = selectionMode
        binding.bboxSelectionView.visibility = if (selectionMode || selection != null) View.VISIBLE else View.GONE
        binding.btnTvSelectMode.text = if (selectionMode) "영역 선택 끝" else "영역 선택 모드"
        status(if (selectionMode) "지울 사물을 사각형으로 감싸세요" else "")
    }

    fun clearSelection() {
        selection = null
        selectionMode = false
        binding.bboxSelectionView.clear()
        binding.bboxSelectionView.isSelecting = false
        binding.bboxSelectionView.visibility = View.GONE
        binding.btnClearSelection.visibility = View.GONE
        binding.resultOverlay.visibility = View.GONE
        clearBackgroundPatch()
        onRemovalCleared()
        refresh()
    }

    private fun requestRemoval() {
        val rect = selection ?: run { status("먼저 영역을 선택하세요"); return }
        if (busy) return
        val frame = space.latestFrame ?: run { status("AR 프레임을 기다리는 중입니다"); return }
        val type = "other"
        busy = true
        refresh()
        onBeforeCapture()
        val snapshot = editor.capture(frame, rect)
        onAfterCapture()
        if (snapshot == null) {
            busy = false
            refresh()
            status("카메라 또는 Depth 프레임이 준비되지 않았습니다. 다시 시도하세요.")
            return
        }
        scope.launch {
            try {
                status("기기 내부 MobileSAM 마스킹 중")
                val result = withContext(Dispatchers.Default) { editor.edit(snapshot) }
                    ?: error("선택 영역에서 유효한 RGB-D 객체를 만들지 못했습니다")
                val region = floatArrayOf(
                    result.changedRegion.left.toFloat() / result.cleanedRgb.width,
                    result.changedRegion.top.toFloat() / result.cleanedRgb.height,
                    result.changedRegion.width().toFloat() / result.cleanedRgb.width,
                    result.changedRegion.height().toFloat() / result.cleanedRgb.height,
                )
                // Preview is intentionally opt-in. The AR renderer consumes the same
                // capture result; no server JPEG/base64 roundtrip occurs.
                binding.resultOverlay.setImageBitmap(result.cleanedRgb)
                binding.resultOverlay.visibility = View.GONE
                binding.btnToggleRemoval.visibility = View.VISIBLE
                buildBackgroundPatch(result.cleanedRgb, result.changedRegion, result.snapshot)
                val pivot = result.placeableObject.pivotMeters
                val objectPose = snapshot.cameraPose.compose(Pose.makeTranslation(pivot[0], pivot[1], pivot[2]))
                onRemovalApplied("local-${snapshot.timestampNanos}", type, result.placeableObject, objectPose, region)
                status("완료 · 온디바이스 ROI LaMa 복원 / RGB-D 객체 준비됨")
            } catch (error: Throwable) {
                Log.e(TAG, "on-device RGB-D edit failed", error)
                status("온디바이스 처리 실패: ${error.message ?: error.javaClass.simpleName}")
            } finally {
                busy = false
                refresh()
            }
        }
    }

    private fun togglePreview() {
        binding.resultOverlay.visibility = if (binding.resultOverlay.visibility == View.VISIBLE) View.GONE else View.VISIBLE
    }

    /**
     * The restored pixels live on an AR anchor, not on the phone screen. We use
     * surrounding depth to size a small background plane at the capture pose; this
     * makes the patch stay in the room when the camera moves.
     */
    private fun buildBackgroundPatch(cleaned: Bitmap, region: android.graphics.Rect, snapshot: com.hackathon.interior.rgbd.ObjectCaptureSnapshot) {
        clearBackgroundPatch()
        val depth = snapshot.depthFrame
        val z = estimateBackgroundDepth(region, snapshot) ?: return
        val cx = (region.exactCenterX() / cleaned.width * depth.width).coerceIn(0f, (depth.width - 1).toFloat())
        val cy = (region.exactCenterY() / cleaned.height * depth.height).coerceIn(0f, (depth.height - 1).toFloat())
        val widthDepthPx = region.width().toFloat() / cleaned.width * depth.width
        val heightDepthPx = region.height().toFloat() / cleaned.height * depth.height
        val widthM = (widthDepthPx * z / depth.intrinsics.fx).coerceIn(0.03f, 4f)
        val heightM = (heightDepthPx * z / depth.intrinsics.fy).coerceIn(0.03f, 4f)
        val pose = snapshot.cameraPose.compose(Pose.makeTranslation(
            (cx - depth.intrinsics.cx) * z / depth.intrinsics.fx,
            -(cy - depth.intrinsics.cy) * z / depth.intrinsics.fy,
            -z,
        ))
        val anchor = runCatching { sceneView.session?.createAnchor(pose) }.getOrNull() ?: return
        val patch = Bitmap.createBitmap(cleaned, region.left, region.top, region.width(), region.height())
        val image = ImageNode(sceneView.materialLoader, EdgeFade.feather(patch), Size(widthM, heightM)).apply {
            isTouchable = false
            rotation = Rotation(x = -90f)
        }
        backgroundPatchNode = AnchorNode(sceneView.engine, anchor).apply {
            isPositionEditable = false
            addChildNode(image)
        }.also(sceneView::addChildNode)
    }

    private fun estimateBackgroundDepth(region: android.graphics.Rect, snapshot: com.hackathon.interior.rgbd.ObjectCaptureSnapshot): Float? {
        val depth = snapshot.depthFrame
        val values = ArrayList<Float>()
        val margin = (maxOf(region.width(), region.height()) * 0.15f).toInt().coerceAtLeast(8)
        for (y in region.top - margin..region.bottom + margin step margin.coerceAtLeast(4)) {
            for (x in region.left - margin..region.right + margin step margin.coerceAtLeast(4)) {
                val onBorder = x < region.left || x >= region.right || y < region.top || y >= region.bottom
                if (!onBorder) continue
                val dx = (x.toFloat() / snapshot.rgbFrame.width * depth.width).toInt().coerceIn(0, depth.width - 1)
                val dy = (y.toFloat() / snapshot.rgbFrame.height * depth.height).toInt().coerceIn(0, depth.height - 1)
                val value = (depth.depthMillimeters[dy * depth.width + dx].toInt() and 0xffff) / 1000f
                if (value in 0.15f..8f) values += value
            }
        }
        return values.sorted().getOrNull(values.size / 2)
    }

    private fun clearBackgroundPatch() {
        backgroundPatchNode?.let {
            sceneView.removeChildNode(it)
            runCatching { it.anchor.detach() }
            runCatching { it.destroy() }
        }
        backgroundPatchNode = null
    }

    private fun refresh() {
        binding.btnRequestRemove.isEnabled = !busy && selection != null
        binding.btnTvSelectMode.isEnabled = !busy
        binding.btnClearSelection.isEnabled = !busy && selection != null
    }

    private fun status(text: String) { binding.removalStatusText.text = text }
    fun onFrame() = Unit
    override fun close() { clearBackgroundPatch(); editor.close() }
    fun release() = close()
    private companion object { const val TAG = "InteriorRgbd" }
}
