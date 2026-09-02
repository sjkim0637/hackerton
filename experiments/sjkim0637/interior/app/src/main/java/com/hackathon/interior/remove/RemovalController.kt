package com.hackathon.interior.remove

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.View
import com.google.ar.core.Anchor
import com.google.ar.core.Plane
import com.google.ar.core.Pose
import com.hackathon.interior.ar.ArSpaceController
import com.hackathon.interior.databinding.ActivityMainBinding
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.ar.arcore.createAnchorOrNull
import io.github.sceneview.ar.node.AnchorNode
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Size
import io.github.sceneview.node.ImageNode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.sqrt

/**
 * 사물(TV) 제거 흐름: 영역 지정 → 키프레임 캡처 → 서버 호출 → job 폴링 →
 * 결과 이미지를 벽 평면에 붙이기 → "삭제 전/후" 전환.
 *
 * PHASE 1 목표는 흐름 연결이다. 서버는 아직 mock(Pillow) 이라 결과 품질은 기대하지 않는다.
 * 3D 배치의 방향/스케일은 대략치이며 실기기에서 다듬는다(PHASE 3).
 */
class RemovalController(
    private val activity: Activity,
    private val scope: CoroutineScope,
    private val sceneView: ARSceneView,
    private val space: ArSpaceController,
    private val binding: ActivityMainBinding,
    private val api: InteriorApiClient = InteriorApiClient(),
    private val onBeforeCapture: () -> Unit = {},
    private val onAfterCapture: () -> Unit = {},
) {

    private val mainHandler = Handler(Looper.getMainLooper())

    private var selectionMode = false
    private var bboxNorm: FloatArray? = null          // [x, y, w, h] — sceneView 대비 정규화
    private var wallAnchor: Anchor? = null
    private var planeIsVertical = false
    private var wallPlaneJson: JSONObject? = null
    private var patchWidthM = 1.2f
    private var patchHeightM = 0.7f

    private var resultNode: AnchorNode? = null
    private var showingAfter = false
    private var busy = false

    init {
        binding.bboxSelectionView.onRectFinalized = ::onRectSelected
        binding.btnTvSelectMode.setOnClickListener { toggleSelectionMode() }
        binding.btnRequestRemove.setOnClickListener { requestRemoval() }
        binding.btnToggleRemoval.setOnClickListener { toggleBeforeAfter() }
    }

    // -------------------------------------------------------------- 1. 영역 지정 (P1-2)

    fun toggleSelectionMode() {
        selectionMode = !selectionMode
        binding.bboxSelectionView.isSelecting = selectionMode
        binding.bboxSelectionView.visibility =
            if (selectionMode || bboxNorm != null) View.VISIBLE else View.GONE
        binding.btnTvSelectMode.text = if (selectionMode) "선택 모드 끄기" else "TV 선택 모드"
        status(if (selectionMode) "화면에서 TV 위를 사각형으로 드래그하세요" else "")
    }

    private fun onRectSelected(rect: RectF) {
        val vw = sceneView.width.toFloat().coerceAtLeast(1f)
        val vh = sceneView.height.toFloat().coerceAtLeast(1f)
        bboxNorm = floatArrayOf(
            (rect.left / vw).coerceIn(0f, 1f),
            (rect.top / vh).coerceIn(0f, 1f),
            (rect.width() / vw).coerceIn(0f, 1f),
            (rect.height() / vh).coerceIn(0f, 1f),
        )
        clearResult()
        resolveWall(rect)

        selectionMode = false
        binding.bboxSelectionView.isSelecting = false
        binding.bboxSelectionView.visibility = View.VISIBLE   // 그린 사각형은 확인용으로 유지
        binding.btnTvSelectMode.text = "TV 선택 모드"
        binding.btnRequestRemove.isEnabled = true
        status("영역 지정됨 · '삭제 요청'을 누르세요")
    }

    /** 사각형 중심/네 변에서 hitTest 해 벽 앵커와 실제 크기(m), 평면 정보를 잡는다. */
    private fun resolveWall(rect: RectF) {
        wallAnchor?.let { runCatching { it.detach() } }
        wallAnchor = null
        wallPlaneJson = null
        planeIsVertical = false
        patchWidthM = 1.2f
        patchHeightM = 0.7f

        val center = space.hitTest(rect.centerX(), rect.centerY())
        wallAnchor = center?.createAnchorOrNull()
        (center?.trackable as? Plane)?.let { plane ->
            planeIsVertical = plane.type == Plane.Type.VERTICAL
            wallPlaneJson = planeToJson(plane)
        }

        val left = space.hitTest(rect.left, rect.centerY())?.hitPose
        val right = space.hitTest(rect.right, rect.centerY())?.hitPose
        val top = space.hitTest(rect.centerX(), rect.top)?.hitPose
        val bottom = space.hitTest(rect.centerX(), rect.bottom)?.hitPose
        if (left != null && right != null) patchWidthM = distance(left, right).coerceIn(0.2f, 4f)
        if (top != null && bottom != null) patchHeightM = distance(top, bottom).coerceIn(0.2f, 4f)
    }

    // ----------------------------------------------- 2·3. 캡처 → 서버 → 폴링 → 적용 (P1-3, P1-8)

    private fun requestRemoval() {
        if (busy) return
        val bbox = bboxNorm ?: run {
            status("먼저 'TV 선택 모드'로 영역을 지정하세요")
            return
        }
        busy = true
        setControlsEnabled(false)
        status("현재 화면 캡처 중…")

        captureSceneJpeg { jpeg, imageW, imageH ->
            if (jpeg == null) {
                status("화면 캡처 실패")
                busy = false
                setControlsEnabled(true)
                return@captureSceneJpeg
            }
            val meta = buildMetaJson(imageW, imageH, bbox)
            scope.launch {
                try {
                    runFlow(jpeg, meta, bbox)
                } catch (e: Exception) {
                    status("실패: ${e.message}")
                } finally {
                    busy = false
                    setControlsEnabled(true)
                }
            }
        }
    }

    private suspend fun runFlow(jpeg: ByteArray, metaJson: String, bbox: FloatArray) {
        status("세션 생성 중…")
        val sceneId = api.createScene()

        status("키프레임 업로드 중…")
        val keyframeId = api.uploadKeyframe(sceneId, jpeg, metaJson)

        status("삭제 요청 전송 중…")
        val jobId = api.requestRemoveObject(sceneId, keyframeId, bbox, "tv")

        var job = api.getJob(sceneId, jobId)
        var tries = 0
        while (job.status != "done" && job.status != "failed" && tries < 60) {
            status("AI 처리 중… (${job.status})")
            delay(1000)
            job = api.getJob(sceneId, jobId)
            tries++
        }
        if (job.status != "done") {
            status("실패: job=${job.status} ${job.error.orEmpty()}")
            return
        }

        val url = job.resultImageUrl ?: run {
            status("결과 URL 이 없습니다")
            return
        }
        status("결과 이미지 받는 중…")
        val bytes = api.downloadBytes(url)
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: run {
                status("결과 이미지 디코드 실패")
                return
            }
        applyResult(bitmap, job.changedRect ?: bbox)
    }

    /** 결과 이미지를 벽 평면 quad 로 붙인다. 벽 앵커가 없으면 전체화면으로 대체 표시. */
    private fun applyResult(full: Bitmap, region: FloatArray) {
        clearResult()
        val patch = cropNormalized(full, region)
        val anchor = wallAnchor
        if (anchor != null) {
            val image = ImageNode(
                materialLoader = sceneView.materialLoader,
                bitmap = patch,
                size = Size(patchWidthM, patchHeightM),
            ).apply {
                isTouchable = false
                // 수직 평면(벽): 앵커 로컬 +Y 가 벽 바깥이므로 quad 를 X축 -90° 세운다.
                rotation = if (planeIsVertical) Rotation(x = -90f) else Rotation(0f, 0f, 0f)
            }
            val node = AnchorNode(sceneView.engine, anchor).apply {
                isPositionEditable = false
                addChildNode(image)
            }
            sceneView.addChildNode(node)
            resultNode = node
            binding.resultOverlay.visibility = View.GONE
        } else {
            binding.resultOverlay.setImageBitmap(full)
            binding.resultOverlay.visibility = View.VISIBLE
        }
        showingAfter = true
        binding.btnToggleRemoval.visibility = View.VISIBLE
        binding.btnToggleRemoval.text = "삭제 후 (보임)"
        status("완료 · '삭제 전/후'로 전환하세요")
    }

    // -------------------------------------------------------------- 삭제 전/후 (P1-9)

    fun toggleBeforeAfter() {
        showingAfter = !showingAfter
        resultNode?.isVisible = showingAfter
        if (binding.resultOverlay.drawable != null) {
            binding.resultOverlay.visibility = if (showingAfter) View.VISIBLE else View.GONE
        }
        binding.btnToggleRemoval.text = if (showingAfter) "삭제 후 (보임)" else "삭제 전 (원본)"
    }

    // -------------------------------------------------------------- 내부 유틸

    private fun clearResult() {
        resultNode?.let { node ->
            sceneView.removeChildNode(node)
            runCatching { node.destroy() }
        }
        resultNode = null
        binding.btnToggleRemoval.visibility = View.GONE
        binding.resultOverlay.visibility = View.GONE
        binding.resultOverlay.setImageDrawable(null)
        showingAfter = false
    }

    private fun captureSceneJpeg(onResult: (ByteArray?, Int, Int) -> Unit) {
        val vw = sceneView.width
        val vh = sceneView.height
        if (vw == 0 || vh == 0) {
            onResult(null, 0, 0)
            return
        }
        val bitmap = Bitmap.createBitmap(vw, vh, Bitmap.Config.ARGB_8888)
        val loc = IntArray(2)
        sceneView.getLocationInWindow(loc)
        val rect = Rect(loc[0], loc[1], loc[0] + vw, loc[1] + vh)

        binding.bboxSelectionView.visibility = View.GONE
        binding.resultOverlay.visibility = View.GONE
        resultNode?.isVisible = false
        onBeforeCapture()

        sceneView.postDelayed({
            PixelCopy.request(activity.window, rect, bitmap, { copyResult ->
                onAfterCapture()
                resultNode?.isVisible = showingAfter
                if (bboxNorm != null) binding.bboxSelectionView.visibility = View.VISIBLE
                if (copyResult == PixelCopy.SUCCESS) {
                    val out = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                    onResult(out.toByteArray(), vw, vh)
                } else {
                    onResult(null, 0, 0)
                }
            }, mainHandler)
        }, 100L)
    }

    private fun buildMetaJson(imageW: Int, imageH: Int, bbox: FloatArray): String {
        val meta = JSONObject()
        meta.put("capturedAt", isoNow())
        meta.put("imageSize", JSONObject().put("width", imageW).put("height", imageH))

        val frame = space.latestFrame
        if (frame != null) {
            val cam = frame.camera
            val view = FloatArray(16)
            cam.getViewMatrix(view, 0) // column-major
            val rowMajor = FloatArray(16)
            for (r in 0..3) for (c in 0..3) rowMajor[r * 4 + c] = view[c * 4 + r]
            meta.put("worldToCamera", JSONArray(rowMajor.map { it.toDouble() }))

            val intr = cam.imageIntrinsics
            val f = intr.focalLength
            val pp = intr.principalPoint
            val dims = intr.imageDimensions
            val sx = if (dims[0] != 0) imageW.toFloat() / dims[0] else 1f
            val sy = if (dims[1] != 0) imageH.toFloat() / dims[1] else 1f
            meta.put(
                "cameraIntrinsics",
                JSONObject()
                    .put("fx", (f[0] * sx).toDouble())
                    .put("fy", (f[1] * sy).toDouble())
                    .put("cx", (pp[0] * sx).toDouble())
                    .put("cy", (pp[1] * sy).toDouble()),
            )
        } else {
            meta.put("worldToCamera", JSONArray(IDENTITY_16.map { it.toDouble() }))
            meta.put(
                "cameraIntrinsics",
                JSONObject()
                    .put("fx", imageW * 0.8)
                    .put("fy", imageW * 0.8)
                    .put("cx", imageW / 2.0)
                    .put("cy", imageH / 2.0),
            )
        }

        wallPlaneJson?.let { meta.put("wallPlane", it) }
        meta.put(
            "targetObject",
            JSONObject()
                .put("objectType", "tv")
                .put(
                    "region",
                    JSONObject()
                        .put("type", "bbox")
                        .put("rect", JSONArray(bbox.map { it.toDouble() })),
                ),
        )
        return meta.toString()
    }

    private fun planeToJson(plane: Plane): JSONObject {
        val cp = plane.centerPose
        val quat = FloatArray(4)
        cp.getRotationQuaternion(quat, 0)
        val normal = FloatArray(3)
        cp.getTransformedAxis(1, 1f, normal, 0) // 평면 로컬 +Y = 법선
        return JSONObject()
            .put(
                "center",
                JSONObject()
                    .put("position", JSONArray(listOf(cp.tx().toDouble(), cp.ty().toDouble(), cp.tz().toDouble())))
                    .put("rotation", JSONArray(quat.map { it.toDouble() })),
            )
            .put("normal", JSONArray(normal.map { it.toDouble() }))
            .put(
                "extent",
                JSONObject().put("x", plane.extentX.toDouble()).put("z", plane.extentZ.toDouble()),
            )
    }

    private fun cropNormalized(bmp: Bitmap, r: FloatArray): Bitmap {
        val x = (r[0] * bmp.width).toInt().coerceIn(0, bmp.width - 1)
        val y = (r[1] * bmp.height).toInt().coerceIn(0, bmp.height - 1)
        val w = (r[2] * bmp.width).toInt().coerceIn(1, bmp.width - x)
        val h = (r[3] * bmp.height).toInt().coerceIn(1, bmp.height - y)
        return Bitmap.createBitmap(bmp, x, y, w, h)
    }

    private fun distance(a: Pose, b: Pose): Float {
        val dx = a.tx() - b.tx()
        val dy = a.ty() - b.ty()
        val dz = a.tz() - b.tz()
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    private fun isoNow(): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(Date())

    private fun status(message: String) {
        binding.removalStatusText.text = message
    }

    private fun setControlsEnabled(enabled: Boolean) {
        binding.btnTvSelectMode.isEnabled = enabled
        binding.btnRequestRemove.isEnabled = enabled && bboxNorm != null
    }

    private companion object {
        val IDENTITY_16 = floatArrayOf(
            1f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f,
            0f, 0f, 1f, 0f,
            0f, 0f, 0f, 1f,
        )
    }
}
