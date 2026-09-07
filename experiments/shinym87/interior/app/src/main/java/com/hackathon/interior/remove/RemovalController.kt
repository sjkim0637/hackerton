package com.hackathon.interior.remove

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.PixelCopy
import android.view.View
import com.google.ar.core.Anchor
import com.google.ar.core.Plane
import com.google.ar.core.Pose
import com.google.ar.core.TrackingState
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
 * D5/D7: 화면을 한 번 탭 → 그 점을 서버(MobileSAM)로 보내 사물 마스크를 얻고 → 로컬 LaMa
 * 인페인팅으로 빈 공간을 채운 결과를 벽/바닥 평면에 붙인다.
 *
 * 예전엔 "영역 선택 모드" 켜기 → 사각형 드래그 → 사물 종류 선택 → "삭제 요청" 버튼,
 * 이렇게 여러 단계였다(D3). MobileSAM 도입 후 사각형을 정확히 그릴 필요가 없어져 이
 * 단계들을 없애고 탭 한 번으로 압축했다 — 사물 종류는 서버가 몰라도 되므로 항상
 * `other`(범용)로 보낸다.
 */
class RemovalController(
    private val activity: Activity,
    private val scope: CoroutineScope,
    private val sceneView: ARSceneView,
    private val space: ArSpaceController,
    private val binding: ActivityMainBinding,
    private val onBeforeCapture: () -> Unit = {},
    private val onAfterCapture: () -> Unit = {},
    /**
     * 삭제 완료 시 — PHASE 4 이동/서버 저장용.
     * (sceneId, jobId, 종류, 캡처한 사물 이미지, 원래 위치, 원래 bbox[x,y,w,h], 폭 m, 높이 m)
     */
    private val onRemovalApplied: (
        String, String?, String, Bitmap?, Pose?, FloatArray?, Float, Float,
    ) -> Unit = { _, _, _, _, _, _, _, _ -> },
    /** 선택 취소 등으로 삭제 결과를 물릴 때. 이동된 사물도 함께 정리하라는 신호. */
    private val onRemovalCleared: () -> Unit = {},
) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val prefs = activity.getSharedPreferences("interior", Context.MODE_PRIVATE)

    private var pointNorm: FloatArray? = null         // [x, y] — sceneView 대비 정규화, 탭 지점
    private var wallAnchor: Anchor? = null
    private var planeIsVertical = false
    private var wallPlaneJson: JSONObject? = null
    private var patchWidthM = DEFAULT_PATCH_WIDTH_M
    private var patchHeightM = DEFAULT_PATCH_HEIGHT_M

    private var resultNode: AnchorNode? = null
    private var showingAfter = false
    private var busy = false

    /** PHASE 4: 삭제 요청 시점의 "원래 사물" 스냅샷(이동 기능이 재사용). */
    private var capturedObjectBitmap: Bitmap? = null
    private var originalObjectPose: Pose? = null

    /** 결과 quad 위치의 이동 평균값(지터 완화). onFrame 에서 갱신. */
    private var smoothedPos: FloatArray? = null

    init {
        binding.serverUrlInput.setText(prefs.getString(KEY_SERVER_URL, DEFAULT_SERVER_URL))
        binding.btnToggleRemoval.setOnClickListener { toggleBeforeAfter() }
    }

    /** 이미 처리 중이면 새 탭을 무시한다(FurnitureController 가 탭 라우팅 전에 확인용으로도 씀). */
    fun isBusy(): Boolean = busy

    /** 지금까지의 선택/결과를 모두 지운다. (다른 동작으로 전환하거나 재시작할 때) */
    fun clearSelection() {
        pointNorm = null
        wallAnchor?.let { runCatching { it.detach() } }
        wallAnchor = null
        wallPlaneJson = null
        planeIsVertical = false
        clearResult()
        capturedObjectBitmap = null
        originalObjectPose = null
        onRemovalCleared()   // 이동된 사물도 함께 정리
    }

    // -------------------------------------------------------------- 1. 탭 → 즉시 삭제 요청 (D5/D7)

    /**
     * 화면 탭 한 번으로 그 지점의 사물을 지운다. [FurnitureController]가 마커/카탈로그 배치가
     * 아닌 탭을 이 함수로 넘긴다. 사물 종류는 서버(MobileSAM)가 몰라도 되므로 항상 `other`로
     * 보낸다 — 예전(D3)엔 사각형 드래그 + 종류 선택 + "삭제 요청" 버튼, 세 단계였다.
     */
    fun onScreenTapped(xPx: Float, yPx: Float) {
        if (busy) {
            Log.d(TAG, "onScreenTapped 무시됨 (이미 처리 중)")
            return
        }
        Log.d(TAG, "onScreenTapped x=$xPx y=$yPx")
        clearResult()
        val vw = sceneView.width.toFloat().coerceAtLeast(1f)
        val vh = sceneView.height.toFloat().coerceAtLeast(1f)
        pointNorm = floatArrayOf((xPx / vw).coerceIn(0f, 1f), (yPx / vh).coerceIn(0f, 1f))
        resolveWallAtPoint(xPx, yPx)
        requestRemoval()
    }

    /** 탭 지점에서 hitTest 해 벽 앵커·평면 정보를 잡는다. 패치 크기는 결과를 받은 뒤 다듬는다. */
    private fun resolveWallAtPoint(xPx: Float, yPx: Float) {
        wallAnchor?.let { runCatching { it.detach() } }
        wallAnchor = null
        wallPlaneJson = null
        planeIsVertical = false
        patchWidthM = DEFAULT_PATCH_WIDTH_M
        patchHeightM = DEFAULT_PATCH_HEIGHT_M

        val hit = space.hitTest(xPx, yPx)
        wallAnchor = hit?.createAnchorOrNull()
        (hit?.trackable as? Plane)?.let { plane ->
            planeIsVertical = plane.type == Plane.Type.VERTICAL
            wallPlaneJson = planeToJson(plane)
        }
    }

    /**
     * 서버가 실제로 바꾼 범위(`changedRect`, MobileSAM 마스크의 바운딩 박스)로 패치 크기를
     * 다시 잰다. 탭 지점 하나만으론 사물의 실제 크기를 몰랐는데, 마스크 결과가 오면 그
     * 사각형의 네 변으로 hitTest 해 실측치에 더 가깝게 만든다.
     */
    private fun refinePatchSizeFromResult(rect: FloatArray) {
        if (rect.size != 4) return
        val left = space.hitTest(rect[0] * sceneView.width, (rect[1] + rect[3] / 2) * sceneView.height)?.hitPose
        val right = space.hitTest(
            (rect[0] + rect[2]) * sceneView.width, (rect[1] + rect[3] / 2) * sceneView.height,
        )?.hitPose
        val top = space.hitTest((rect[0] + rect[2] / 2) * sceneView.width, rect[1] * sceneView.height)?.hitPose
        val bottom = space.hitTest(
            (rect[0] + rect[2] / 2) * sceneView.width, (rect[1] + rect[3]) * sceneView.height,
        )?.hitPose
        if (left != null && right != null) patchWidthM = distance(left, right).coerceIn(0.2f, 4f)
        if (top != null && bottom != null) patchHeightM = distance(top, bottom).coerceIn(0.2f, 4f)
    }

    // ----------------------------------------------- 2·3. 캡처 → 서버 → 폴링 → 적용 (P1-3, P1-8)

    /** 스킴 보정 + 끝 슬래시 제거만 (부수효과 없음). */
    private fun normalizeServerUrl(input: String): String {
        var url = input.trim()
        if (url.isEmpty()) url = DEFAULT_SERVER_URL
        if (!url.startsWith("http://") && !url.startsWith("https://")) url = "http://$url"
        return url.trimEnd('/')
    }

    /** 입력창의 서버 주소를 정규화하고 입력창/prefs 에 반영한다. (삭제 요청 시) */
    private fun currentBaseUrl(): String {
        val url = normalizeServerUrl(binding.serverUrlInput.text?.toString().orEmpty())
        prefs.edit().putString(KEY_SERVER_URL, url).apply()
        binding.serverUrlInput.setText(url)
        return url
    }

    /** 지금 저장된 서버 주소를 부수효과 없이 돌려준다. (이동 배치 저장/복원 API 용) */
    fun serverBaseUrl(): String {
        val raw = binding.serverUrlInput.text?.toString()?.trim().orEmpty()
            .ifEmpty { prefs.getString(KEY_SERVER_URL, DEFAULT_SERVER_URL) ?: DEFAULT_SERVER_URL }
        return normalizeServerUrl(raw)
    }

    /** 탭 지점 중심의 근사 정사각형. 서버가 MobileSAM 마스크의 실제 범위를 돌려주기 전까지
     * 미리보기 크롭/패치 크기에 쓰는 임시값이다(서버의 `mobilesam_fallback_box_frac`과 같은 발상). */
    private fun approxRectAroundPoint(point: FloatArray): FloatArray {
        val side = FALLBACK_BOX_FRACTION
        val x = (point[0] - side / 2).coerceIn(0f, 1f - side)
        val y = (point[1] - side / 2).coerceIn(0f, 1f - side)
        return floatArrayOf(x, y, side, side)
    }

    private fun requestRemoval() {
        val point = pointNorm ?: return
        val client = InteriorApiClient(currentBaseUrl())
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
            // PHASE 4: 삭제 전 사물 모습(근사 크롭)과 원래 위치를 기억해 둔다. 결과가 오면
            // 서버가 계산한 실제 마스크 범위(changedRect)로 더 정확하게 다시 크롭한다.
            val approxRect = approxRectAroundPoint(point)
            capturedObjectBitmap = runCatching {
                BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)?.let { cropNormalized(it, approxRect) }
            }.getOrNull()
            originalObjectPose = wallAnchor?.pose
            val meta = buildMetaJson(imageW, imageH, point)
            scope.launch {
                try {
                    runFlow(client, jpeg, meta, point, approxRect)
                } catch (e: Exception) {
                    status("실패: ${e.message ?: e.javaClass.simpleName} · 서버 주소/같은 Wi-Fi/방화벽 확인")
                } finally {
                    busy = false
                    setControlsEnabled(true)
                }
            }
        }
    }

    private suspend fun runFlow(
        client: InteriorApiClient,
        jpeg: ByteArray,
        metaJson: String,
        point: FloatArray,
        approxRect: FloatArray,
    ) {
        status("세션 생성 중…")
        val sceneId = client.createScene()

        status("키프레임 업로드 중…")
        val keyframeId = client.uploadKeyframe(sceneId, jpeg, metaJson)

        status("사물 인식 + 삭제 요청 전송 중…")
        val jobId = client.requestRemoveObjectAtPoint(sceneId, keyframeId, point[0], point[1], OBJECT_TYPE)

        var job = client.getJob(sceneId, jobId)
        var tries = 0
        while (job.status != "done" && job.status != "failed" && tries < 120) {
            status("AI 처리 중… (${job.status})")
            delay(1000)
            job = client.getJob(sceneId, jobId)
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
        val bytes = client.downloadBytes(url)
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: run {
                status("결과 이미지 디코드 실패")
                return
            }

        // 서버가 MobileSAM 마스크의 실제 바운딩 박스를 돌려주면(항상 bbox 타입) 근사 사각형
        // 대신 그걸로 패치 크기·크롭 미리보기를 다시 맞춘다 — 탭 지점보다 훨씬 정확하다.
        val actualRect = job.changedRect ?: approxRect
        refinePatchSizeFromResult(actualRect)
        capturedObjectBitmap = runCatching {
            BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)?.let { cropNormalized(it, actualRect) }
        }.getOrNull() ?: capturedObjectBitmap

        applyResult(bitmap, actualRect)

        // PHASE 4: 이 사물을 "다른 위치로 이동" + 서버(placements) 저장/복원 할 수 있게 넘긴다.
        onRemovalApplied(
            sceneId, jobId, OBJECT_TYPE,
            capturedObjectBitmap, originalObjectPose, actualRect,
            patchWidthM, patchHeightM,
        )
    }

    /** 결과 이미지를 벽 평면 quad 로 붙인다. 벽 앵커가 없으면 전체화면으로 대체 표시. */
    private fun applyResult(full: Bitmap, region: FloatArray) {
        clearResult()
        // 가장자리를 투명하게 페이드아웃해 quad 경계가 카메라 화면과 자연스럽게 섞이게 한다.
        val patch = EdgeFade.feather(cropNormalized(full, region))
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
                // pose 는 우리가 매 프레임 스무딩해서 직접 넣는다 (onFrame). SceneView 자동 갱신 끔.
                updateAnchorPose = false
                addChildNode(image)
            }
            sceneView.addChildNode(node)
            resultNode = node
            smoothedPos = null
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

    /**
     * 매 프레임 호출: 결과 quad 를 벽 앵커에 스무딩해서 고정한다.
     * - 앵커가 추적 중이 아닐 땐 마지막 위치를 그대로 두어 "미끄러짐"을 막는다.
     * - 위치 값에 이동 평균(EMA)을 걸어 ARCore 재추적 지터를 완화한다.
     * - 회전은 앵커 값을 그대로 쓴다(회전 지터는 상대적으로 작다).
     */
    fun onFrame() {
        val node = resultNode ?: return
        val anchor = wallAnchor ?: return
        val ts = anchor.trackingState
        if (ts == TrackingState.STOPPED) {
            node.isVisible = false
            return
        }
        if (ts != TrackingState.TRACKING) return  // PAUSED: 마지막 위치 유지

        val p = anchor.pose
        val prev = smoothedPos
        val nx: Float
        val ny: Float
        val nz: Float
        if (prev == null) {
            nx = p.tx(); ny = p.ty(); nz = p.tz()
        } else {
            nx = prev[0] + (p.tx() - prev[0]) * SMOOTH_ALPHA
            ny = prev[1] + (p.ty() - prev[1]) * SMOOTH_ALPHA
            nz = prev[2] + (p.tz() - prev[2]) * SMOOTH_ALPHA
        }
        smoothedPos = floatArrayOf(nx, ny, nz)

        val quat = FloatArray(4)
        p.getRotationQuaternion(quat, 0)
        node.pose = Pose(floatArrayOf(nx, ny, nz), quat)
        if (!node.isVisible && showingAfter) node.isVisible = true
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
        smoothedPos = null
        binding.btnToggleRemoval.visibility = View.GONE
        binding.resultOverlay.visibility = View.GONE
        binding.resultOverlay.setImageDrawable(null)
        showingAfter = false
    }

    /**
     * 키프레임 캡처. `ARSceneView` 는 `SurfaceView` 라 카메라·3D 는 별도 서피스에 그려진다.
     * 창(window) 을 캡처하면 UI 위젯만 나오고 카메라가 검게 나오므로,
     * `PixelCopy.request(SurfaceView, ...)` 로 그 서피스만 직접 읽는다(UI 오버레이는 자동 제외).
     */
    private fun captureSceneJpeg(onResult: (ByteArray?, Int, Int) -> Unit) {
        val vw = sceneView.width
        val vh = sceneView.height
        if (vw == 0 || vh == 0 || !sceneView.holder.surface.isValid) {
            onResult(null, 0, 0)
            return
        }
        val bitmap = Bitmap.createBitmap(vw, vh, Bitmap.Config.ARGB_8888)

        // 큐브/결과 quad 는 키프레임에서 뺀다. bboxSelectionView/resultOverlay 는 서피스 밖이라 무관.
        resultNode?.isVisible = false
        onBeforeCapture()

        sceneView.postDelayed({
            PixelCopy.request(sceneView, bitmap, { copyResult ->
                onAfterCapture()
                resultNode?.isVisible = showingAfter
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

    private fun buildMetaJson(imageW: Int, imageH: Int, point: FloatArray): String {
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
                .put("objectType", OBJECT_TYPE)
                .put(
                    "region",
                    JSONObject()
                        .put("type", "point")
                        .put("point", JSONArray(point.map { it.toDouble() })),
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
        Log.d(TAG, "status: $message")
        binding.removalStatusText.text = message
    }

    private fun setControlsEnabled(enabled: Boolean) {
        binding.serverUrlInput.isEnabled = enabled
    }

    private companion object {
        const val TAG = "InteriorRemoval"
        const val DEFAULT_SERVER_URL = "http://192.168.0.2:8000"
        const val KEY_SERVER_URL = "server_url"

        /** 결과 quad 위치 이동 평균 계수(0~1). 작을수록 부드럽지만 반응이 느리다. */
        const val SMOOTH_ALPHA = 0.2f

        /** 탭 지점 하나만으론 실제 사물 크기를 모르므로 쓰는 초기 패치 크기(m). */
        const val DEFAULT_PATCH_WIDTH_M = 1.2f
        const val DEFAULT_PATCH_HEIGHT_M = 0.7f

        /** 결과가 오기 전 미리보기 크롭에 쓰는 탭 지점 중심 정사각형 한 변(이미지 짧은 변 비율). */
        const val FALLBACK_BOX_FRACTION = 0.28f

        /**
         * MobileSAM은 사물 종류를 몰라도 점 위치로 마스크를 잡으므로, 서버에는 항상 `other`
         * (범용)로 보낸다 — 서버는 이 값을 특정 사물 힌트 없이 범용 배경 복원(_DEFAULT_HINT)
         * 으로 처리한다. 예전(D3)엔 스피너로 tv/sofa/table 등을 직접 골라야 했다.
         */
        const val OBJECT_TYPE = "other"

        val IDENTITY_16 = floatArrayOf(
            1f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f,
            0f, 0f, 1f, 0f,
            0f, 0f, 0f, 1f,
        )
    }
}
