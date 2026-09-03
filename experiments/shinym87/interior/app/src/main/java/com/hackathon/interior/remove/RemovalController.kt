package com.hackathon.interior.remove

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import com.google.ar.core.Anchor
import com.google.ar.core.Plane
import com.google.ar.core.Pose
import com.google.ar.core.TrackingState
import com.hackathon.interior.R
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
 * 사물(TV 등) 제거 흐름: 영역 지정 → 키프레임 캡처 → 서버 호출 → job 폴링 →
 * 결과 이미지를 벽 평면에 붙이기 → "삭제 전/후" 전환.
 *
 * PHASE 1 목표는 흐름 연결이다. 3D 배치의 방향/스케일은 대략치이며 실기기에서 다듬는다.
 */
class RemovalController(
    private val activity: Activity,
    private val scope: CoroutineScope,
    private val sceneView: ARSceneView,
    private val space: ArSpaceController,
    private val binding: ActivityMainBinding,
    private val onBeforeCapture: () -> Unit = {},
    private val onAfterCapture: () -> Unit = {},
    /** 삭제 완료 시: (종류, 캡처한 사물 이미지, 원래 위치, 폭 m, 높이 m) — PHASE 4 이동용. */
    private val onRemovalApplied: (String, Bitmap?, Pose?, Float, Float) -> Unit = { _, _, _, _, _ -> },
    /** 선택 취소 등으로 삭제 결과를 물릴 때. 이동된 사물도 함께 정리하라는 신호. */
    private val onRemovalCleared: () -> Unit = {},
) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val prefs = activity.getSharedPreferences("interior", Context.MODE_PRIVATE)

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

    /** PHASE 4: 삭제 요청 시점의 "원래 사물" 스냅샷(이동 기능이 재사용). */
    private var capturedObjectBitmap: Bitmap? = null
    private var originalObjectPose: Pose? = null

    /** 결과 quad 위치의 이동 평균값(지터 완화). onFrame 에서 갱신. */
    private var smoothedPos: FloatArray? = null

    init {
        binding.serverUrlInput.setText(prefs.getString(KEY_SERVER_URL, DEFAULT_SERVER_URL))

        // 0번은 "선택 안 함" 안내 항목. 사용자가 실제 종류를 고르기 전엔 삭제 요청을 막는다.
        binding.objectTypeSpinner.adapter = ArrayAdapter(
            activity,
            R.layout.spinner_item_light,
            listOf(SPINNER_PROMPT) + OBJECT_TYPES.map { it.second },
        ).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        binding.objectTypeSpinner.setSelection(0)
        binding.objectTypeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) = refreshRequestButton()
            override fun onNothingSelected(p: AdapterView<*>?) = refreshRequestButton()
        }

        binding.bboxSelectionView.onRectFinalized = ::onRectSelected
        binding.btnTvSelectMode.setOnClickListener { toggleSelectionMode() }
        binding.btnClearSelection.setOnClickListener { clearSelection() }
        binding.btnRequestRemove.setOnClickListener { requestRemoval() }
        binding.btnToggleRemoval.setOnClickListener { toggleBeforeAfter() }
        refreshRequestButton()
    }

    /** 스피너에서 고른 사물의 서버 키(tv / sofa / table / other …). 미선택이면 null. */
    private fun selectedObjectTypeOrNull(): String? {
        val pos = binding.objectTypeSpinner.selectedItemPosition
        return OBJECT_TYPES.getOrNull(pos - 1)?.first   // pos 0 = SPINNER_PROMPT
    }

    /** bbox 도 있고 사물 종류도 골랐을 때만 "삭제 요청" 을 활성화한다. */
    private fun refreshRequestButton() {
        binding.btnRequestRemove.isEnabled =
            !busy && bboxNorm != null && selectedObjectTypeOrNull() != null
    }

    // -------------------------------------------------------------- 1. 영역 지정 (P1-2)

    fun toggleSelectionMode() {
        if (!selectionMode) {
            clearSelection(announce = false)   // 새로 그리기 전에 이전 선택 정리
            selectionMode = true
            binding.bboxSelectionView.isSelecting = true
            binding.bboxSelectionView.visibility = View.VISIBLE
            binding.btnTvSelectMode.text = "선택 모드 끄기"
            binding.btnClearSelection.visibility = View.VISIBLE
            status(
                "지우고 싶은 사물에 딱 맞게 사각형을 그리면,\n" +
                    "결과 품질과 크기 측정 정확도가 모두 좋아집니다."
            )
        } else {
            selectionMode = false
            binding.bboxSelectionView.isSelecting = false
            val hasSelection = bboxNorm != null
            binding.bboxSelectionView.visibility = if (hasSelection) View.VISIBLE else View.GONE
            binding.btnTvSelectMode.text = "영역 선택 모드"
            binding.btnClearSelection.visibility = if (hasSelection) View.VISIBLE else View.GONE
            status(if (hasSelection) "영역 지정됨 · '삭제 요청'을 누르세요" else "")
        }
    }

    /** 지정한 영역/그린 사각형/결과를 모두 지운다. (선택 취소 버튼 + 모드 재진입 시) */
    fun clearSelection(announce: Boolean = true) {
        bboxNorm = null
        wallAnchor?.let { runCatching { it.detach() } }
        wallAnchor = null
        wallPlaneJson = null
        planeIsVertical = false

        selectionMode = false
        binding.bboxSelectionView.isSelecting = false
        binding.bboxSelectionView.clear()
        binding.bboxSelectionView.visibility = View.GONE
        binding.btnTvSelectMode.text = "영역 선택 모드"
        binding.btnClearSelection.visibility = View.GONE
        refreshRequestButton()
        clearResult()
        capturedObjectBitmap = null
        originalObjectPose = null
        onRemovalCleared()   // 이동된 사물도 함께 정리
        if (announce) status("선택을 취소했습니다")
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
        binding.bboxSelectionView.measurementText = measureSelectionLabel(rect)

        selectionMode = false
        binding.bboxSelectionView.isSelecting = false
        binding.bboxSelectionView.visibility = View.VISIBLE   // 그린 사각형은 확인용으로 유지
        binding.btnTvSelectMode.text = "영역 선택 모드"
        binding.btnClearSelection.visibility = View.VISIBLE
        refreshRequestButton()
        status(
            if (selectedObjectTypeOrNull() == null)
                "영역 지정됨 · 위에서 '지울 사물' 종류를 고르면 삭제 요청이 활성화됩니다"
            else
                "영역 지정됨 · '삭제 요청'을 누르세요 (다시 그리려면 '영역 선택 모드')"
        )

        // 선택 영역이 화면의 큰 비율을 덮으면 겹친 가구가 포함됐을 수 있다.
        // 삭제를 막지는 않고 경고만 잠깐 띄운다 (진단 실험: 겹침 시 결과 불안정).
        val areaFraction = (rect.width() / vw) * (rect.height() / vh)
        if (areaFraction >= LARGE_SELECTION_FRACTION) {
            Toast.makeText(
                activity,
                "선택 영역이 넓습니다. 다른 가구가 포함되지 않았는지 확인해주세요",
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    /**
     * 그린 사각형의 실제 가로/세로를 잰다.
     * 좌상단-우상단(가로), 좌상단-좌하단(세로) 지점에서 ARCore hitTest 를 하고,
     * 두 3D 좌표 사이 거리를 cm 로 계산한다.
     *
     * 이 수치는 **내가 그린 박스**의 크기이지 가구 자체의 정확한 치수가 아니다.
     * 문구도 "선택 영역"이라고만 표현한다.
     */
    private fun measureSelectionLabel(rect: RectF): String {
        val topLeft = space.hitTest(rect.left, rect.top)?.hitPose
        val topRight = space.hitTest(rect.right, rect.top)?.hitPose
        val bottomLeft = space.hitTest(rect.left, rect.bottom)?.hitPose

        if (topLeft == null || topRight == null || bottomLeft == null) {
            return "선택 영역: 정확한 측정 어려움\n(평면이 인식되지 않았어요)"
        }
        val widthCm = distance(topLeft, topRight) * 100f
        val heightCm = distance(topLeft, bottomLeft) * 100f
        return "선택 영역: 약 %.0fcm × %.0fcm\n(내가 그린 박스 기준 · 가구 실측 아님)"
            .format(widthCm, heightCm)
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

    /** 입력창의 서버 주소를 정규화(스킴 보정, 끝 슬래시 제거)하고 저장한다. */
    private fun currentBaseUrl(): String {
        var url = binding.serverUrlInput.text?.toString()?.trim().orEmpty()
        if (url.isEmpty()) url = DEFAULT_SERVER_URL
        if (!url.startsWith("http://") && !url.startsWith("https://")) url = "http://$url"
        url = url.trimEnd('/')
        prefs.edit().putString(KEY_SERVER_URL, url).apply()
        binding.serverUrlInput.setText(url)
        return url
    }

    private fun requestRemoval() {
        if (busy) return
        val bbox = bboxNorm ?: run {
            status("먼저 '영역 선택 모드'로 지울 영역을 지정하세요")
            return
        }
        val objectType = selectedObjectTypeOrNull() ?: run {
            status("지울 사물 종류를 먼저 선택하세요 (목록에 없으면 '기타/소품')")
            return
        }
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
            // PHASE 4: 삭제 전 사물 모습(키프레임의 bbox 크롭)과 원래 위치를 기억해 둔다.
            capturedObjectBitmap = runCatching {
                BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)?.let { cropNormalized(it, bbox) }
            }.getOrNull()
            originalObjectPose = wallAnchor?.pose
            val meta = buildMetaJson(imageW, imageH, bbox, objectType)
            scope.launch {
                try {
                    runFlow(client, jpeg, meta, bbox, objectType)
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
        bbox: FloatArray,
        objectType: String,
    ) {
        status("세션 생성 중…")
        val sceneId = client.createScene()

        status("키프레임 업로드 중…")
        val keyframeId = client.uploadKeyframe(sceneId, jpeg, metaJson)

        status("삭제 요청 전송 중…")
        val jobId = client.requestRemoveObject(sceneId, keyframeId, bbox, objectType)

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
        applyResult(bitmap, job.changedRect ?: bbox)

        // PHASE 4: 이제 이 사물을 "다른 위치로 이동" 할 수 있게 이동 컨트롤러에 넘긴다.
        onRemovalApplied(
            objectType, capturedObjectBitmap, originalObjectPose, patchWidthM, patchHeightM,
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

    private fun buildMetaJson(imageW: Int, imageH: Int, bbox: FloatArray, objectType: String): String {
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
                .put("objectType", objectType)
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
        binding.btnClearSelection.isEnabled = enabled
        binding.serverUrlInput.isEnabled = enabled
        binding.objectTypeSpinner.isEnabled = enabled
        refreshRequestButton()   // bbox + 사물 종류 조건까지 함께 본다
    }

    private companion object {
        const val DEFAULT_SERVER_URL = "http://192.168.0.2:8000"
        const val KEY_SERVER_URL = "server_url"

        /** 선택 사각형이 화면 면적의 이 비율 이상이면 "넓다" 경고. */
        const val LARGE_SELECTION_FRACTION = 0.40f

        /** 결과 quad 위치 이동 평균 계수(0~1). 작을수록 부드럽지만 반응이 느리다. */
        const val SMOOTH_ALPHA = 0.2f

        /** 스피너 0번 안내 항목(실제 종류 아님). 이 상태에선 '삭제 요청'이 비활성화된다. */
        const val SPINNER_PROMPT = "사물 종류 선택…"

        /**
         * 서버 키 → 화면 표시 라벨. 앞 5개는 server/catalog/furniture.json 의 category 와 맞춘다.
         * "other"(기타/소품)는 목록에 없는 작은 물건(컵 등)용이며, 서버는 이 값을 특정 사물
         * 힌트 없이 범용 배경 복원(_DEFAULT_HINT)으로 처리한다.
         */
        val OBJECT_TYPES = listOf(
            "tv" to "TV",
            "sofa" to "소파",
            "table" to "테이블",
            "chair" to "의자",
            "shelf" to "선반",
            "other" to "기타/소품",
        )

        val IDENTITY_16 = floatArrayOf(
            1f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f,
            0f, 0f, 1f, 0f,
            0f, 0f, 0f, 1f,
        )
    }
}
