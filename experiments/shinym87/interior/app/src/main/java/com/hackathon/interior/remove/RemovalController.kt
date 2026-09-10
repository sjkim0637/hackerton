package com.hackathon.interior.remove

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.PixelCopy
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import com.google.ar.core.Anchor
import com.google.ar.core.HitResult
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
import kotlin.math.abs
import kotlin.math.acos
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
    private val serverBaseUrl: () -> String,
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
    private var selectionMode = false
    private var bboxNorm: FloatArray? = null          // [x, y, w, h] — sceneView 대비 정규화
    private var wallAnchor: Anchor? = null
    private var planeIsVertical = false
    private var wallPlaneJson: JSONObject? = null
    private var patchWidthM = 1.2f
    private var patchHeightM = 0.7f

    private var resultNode: AnchorNode? = null

    /**
     * "삭제 결과 보기" 전체화면 프리뷰(정지 이미지)가 켜져 있는지.
     * **월드 앵커 커버 quad([resultNode]) 의 표시 여부와는 무관하다** — 커버 quad 는
     * 결과가 있는 한 라이브 모드에서도 항상 렌더링되어 실제 사물을 계속 가린다.
     */
    private var showingAfter = false

    /** 선택 시점엔 평면이 없어 커버 quad 를 못 만든 상태. onFrame 에서 계속 재시도한다. */
    private var awaitingCoverAnchor = false
    private var pendingCoverPatch: Bitmap? = null
    private var pendingCoverRegion: FloatArray? = null

    /**
     * 평면 자체를 못 잡아 커버 quad 를 아예 포기하고 전체화면 프리뷰만 쓰는 상태.
     * [buildResultNode] 를 건너뛰고 [showFrozenResult] 를 켠다.
     */
    private var coverQuadDisabled = false

    /**
     * 커버 quad 를 **카메라를 향하는 가림막(빌보드)** 으로 세운 상태 — 바닥/테이블 위 사물 전용.
     * 평면에 납작하게 깐 quad 는 표면 위로 서 있는 실물(컵·의자 등)을 못 가린다. 대신 사물
     * 위치에 카메라 정면을 보는 quad 를 세우고, [onFrame] 에서 매 프레임 카메라로 돌린다.
     */
    private var coverIsBillboard = false

    /** 커버 quad 의 자식 ImageNode. 빌보드 회전을 매 프레임 여기에 건다. */
    private var resultImageNode: ImageNode? = null

    /** TEMP-DIAG(B): 커버 quad 를 만든 시점의 카메라 pose. 지금 카메라와의 차이를 로그로 본다. */
    private var coverCamPoseAtBuild: Pose? = null
    private var coverFrameLog = 0L

    private var busy = false

    /** PHASE 4: 삭제 요청 시점의 "원래 사물" 스냅샷(이동 기능이 재사용). */
    private var capturedObjectBitmap: Bitmap? = null
    private var originalObjectPose: Pose? = null

    /** 결과 quad 위치의 이동 평균값(지터 완화). onFrame 에서 갱신. */
    private var smoothedPos: FloatArray? = null

    init {
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

        // --- 커버 quad 크기 = "화면에서 그린 사각형" 그대로 ---
        // 폭: 선택 사각형 좌·우 변을 평면에 hitTest 한 실제 거리(m). 둘 다 맞아야 신뢰.
        //     한쪽만 맞으면 중심~그쪽 거리의 2배. 둘 다 실패하면 기본값 유지.
        // 높이: 평면 상/하단 hitTest 로 재지 않는다 — 수평면에서 위쪽 레이가 지평선으로
        //     향하면 교점이 4m 로 폭발했고, 일부만 맞으면 종횡비가 깨졌다. 대신 **화면
        //     선택 사각형의 종횡비**를 폭에 곱한다 → 커버 quad 가 항상 내가 그린 박스 모양.
        val left = space.hitTest(rect.left, rect.centerY())?.hitPose
        val right = space.hitTest(rect.right, rect.centerY())?.hitPose
        val centerHit = center?.hitPose
        when {
            left != null && right != null -> patchWidthM = distance(left, right).coerceIn(0.05f, 3f)
            centerHit != null && left != null -> patchWidthM = (distance(centerHit, left) * 2f).coerceIn(0.05f, 3f)
            centerHit != null && right != null -> patchWidthM = (distance(centerHit, right) * 2f).coerceIn(0.05f, 3f)
        }
        val screenAspect = rect.height() / rect.width().coerceAtLeast(1f)
        patchHeightM = (patchWidthM * screenAspect).coerceIn(0.05f, 3f)
        Log.d(
            TAG,
            "resolveWall: patchW=%.3f patchH=%.3f m (edges L=%b R=%b · screenAspect=%.2f · center=%b)".format(
                patchWidthM, patchHeightM, left != null, right != null, screenAspect, center != null,
            ),
        )
    }

    // ----------------------------------------------- 2·3. 캡처 → 서버 → 폴링 → 적용 (P1-3, P1-8)

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
        val client = InteriorApiClient(serverBaseUrl())
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

        // 이동 마커에 쓸 이미지: 서버가 만든 **투명 배경 컷아웃**(RGBA PNG) 을 우선 쓴다.
        // 없거나 실패하면 로컬 bbox 크롭(capturedObjectBitmap) 으로 대체 — 그 경우 네모/흰
        // 배경이 딸려온다.
        var markerBitmap = capturedObjectBitmap
        job.cutoutImageUrl?.let { cutUrl ->
            runCatching {
                val cutBytes = client.downloadBytes(cutUrl)
                BitmapFactory.decodeByteArray(cutBytes, 0, cutBytes.size)
            }.getOrNull()?.let { markerBitmap = it }
        }

        // PHASE 4: 이 사물을 "다른 위치로 이동" + 서버(placements) 저장/복원 할 수 있게 넘긴다.
        Log.d(
            TAG,
            "runFlow done → onRemovalApplied(scene=$sceneId type=$objectType " +
                "hasBmp=${markerBitmap != null} cutout=${job.cutoutImageUrl != null} " +
                "hasPose=${originalObjectPose != null})",
        )
        onRemovalApplied(
            sceneId, jobId, objectType,
            markerBitmap, originalObjectPose, bbox,
            patchWidthM, patchHeightM,
        )
    }

    /**
     * 삭제 결과를 화면에 반영한다. **두 가지가 분리되어 있다:**
     *
     * 1. **월드 앵커 커버 quad** ([resultNode]) — AI 가 복원한 배경 패치를 실제 사물이
     *    있던 위치의 평면 앵커에 고정한다. 라이브 모드에서도 **항상** 렌더링되어 실제
     *    사물(모니터 등)을 계속 가린다. 표시 여부는 [toggleBeforeAfter] 와 무관하며
     *    [onFrame] 이 추적 상태만 보고 관리한다.
     *    선택 시점에 평면을 못 잡았으면 [awaitingCoverAnchor] 로 두고 [onFrame] 에서
     *    사물 영역을 계속 hitTest 해 잡히는 즉시 만든다.
     * 2. **전체화면 프리뷰** ([R.id.resultOverlay]) — 결과 전체 이미지를 정지 화면으로
     *    덮어 크게 확인하는 용도. "삭제 결과 보기" 버튼으로만 켜고 끈다. 기본은 꺼짐
     *    (라이브 유지 — report: docs/handoffs/interior-removal-fallback.md).
     */
    private fun applyResult(full: Bitmap, region: FloatArray) {
        clearResult()
        // 가장자리를 투명하게 페이드아웃해 quad 경계가 카메라 화면과 자연스럽게 섞이게 한다.
        val patch = EdgeFade.feather(cropNormalized(full, region))

        // 전체화면 프리뷰용 이미지는 앵커 유무와 무관하게 항상 준비(기본은 꺼짐).
        binding.resultOverlay.setImageBitmap(full)
        binding.resultOverlay.visibility = View.GONE
        showingAfter = false
        binding.btnToggleRemoval.text = "삭제 결과 보기"
        binding.btnToggleRemoval.visibility = View.VISIBLE

        // 커버 quad 앵커: 선택 시점 것이 있으면 그대로, 없으면 지금(결과 도착 시점) 사물
        // 영역에서 재시도 — 대개 이 무렵엔 평면이 잡혀 있다.
        var anchor = wallAnchor
        var isVertical = planeIsVertical
        if (anchor == null) {
            val hit = hitTestSourceRegion(region)
            val fresh = hit?.let {
                it.createAnchorOrNull()
                    ?: runCatching { sceneView.session?.createAnchor(it.hitPose) }.getOrNull()
            }
            if (fresh != null) {
                anchor = fresh
                isVertical = (hit.trackable as? Plane)?.type == Plane.Type.VERTICAL
                wallAnchor = fresh
                planeIsVertical = isVertical
            }
        }
        // 평면을 전혀 못 잡으면 붙일 자리도, 크기 기준도 없다 → 카메라 앞에 임의로 세우면
        // (예전 시도) 기본값 1.2×0.7 짜리 판이 얼굴 앞 0.8m 에 떠서 화면을 다 덮었다.
        // 그런 경우는 아래 else 에서 전체화면 정지 프리뷰로만 보여준다.

        // 커버 quad 처리:
        // - 벽걸이 사물(수직 평면) → 평면에 납작하게 붙인다 (기존 방식, 라이브 유지).
        // - 바닥/테이블 위 사물(수평 평면) → 평면에 깔면 서 있는 실물을 못 가리므로,
        //   사물 위치에 카메라를 향하는 가림막(빌보드)으로 세운다 (라이브 유지).
        // - 평면 자체를 못 잡음 → 붙일 데가 없어 전체화면 정지 프리뷰로 대체.
        when {
            anchor != null && isVertical -> {
                buildResultNode(anchor, isVertical, patch)
                status("완료 · 라이브 화면에서 삭제 자리가 가려집니다 · '삭제 결과 보기'로 전체 확인")
                Log.d(TAG, "applyResult: 커버 quad 고정 (vertical=true)")
            }
            anchor != null -> {
                coverIsBillboard = true
                buildResultNode(anchor, isVertical = false, patch)
                status("완료 · 삭제 자리를 가림막으로 덮었습니다 · 삭제한 사물을 원하는 곳으로 끌어 옮기세요")
                Log.d(TAG, "applyResult: 빌보드 커버 quad (수평면 사물)")
            }
            isVertical -> {
                // 벽 사물인데 아직 평면을 못 잡음 → onFrame 에서 재시도 (라이브 유지).
                pendingCoverPatch = patch
                pendingCoverRegion = region.copyOf()
                awaitingCoverAnchor = true
                status("완료 · 삭제 자리 평면이 인식되면 결과가 고정됩니다 · 그 방향을 잠깐 비춰주세요")
                Log.d(TAG, "applyResult: 앵커 없음(벽) → onFrame 에서 커버 quad 재시도")
            }
            else -> {
                coverQuadDisabled = true
                showFrozenResult()
                status("완료 · 평면 인식이 안 돼 결과를 전체화면으로 표시합니다 · '결과 닫기 (라이브로)'로 복귀")
                Log.d(TAG, "applyResult: 평면 없음 → 전체화면 프리뷰")
            }
        }
    }

    /** 전체화면 정지 결과 프리뷰([R.id.resultOverlay])를 켠다. 커버 quad 를 못/안 쓸 때. */
    private fun showFrozenResult() {
        if (binding.resultOverlay.drawable == null) return
        showingAfter = true
        binding.resultOverlay.visibility = View.VISIBLE
        binding.btnToggleRemoval.text = "결과 닫기 (라이브로)"
        binding.btnToggleRemoval.visibility = View.VISIBLE
    }

    /** 사물 영역([region] = 정규화 [x,y,w,h]) 중심에서 평면 hitTest. */
    private fun hitTestSourceRegion(region: FloatArray): HitResult? {
        if (sceneView.width == 0 || sceneView.height == 0) return null
        val cx = (region[0] + region[2] / 2f) * sceneView.width
        val cy = (region[1] + region[3] / 2f) * sceneView.height
        // planeIsVertical 은 선택 시점 값이라 힌트로만 쓰고, 없으면 아무 평면이나.
        val hit = space.hitTestPreferring(cx, cy, planeIsVertical)
        // TEMP-DIAG(B): 이 화면 좌표는 "선택 시점" 기준이다. 결과가 온 지금 카메라가 그때와
        // 다르면, 같은 픽셀이 다른 월드 지점을 가리켜 커버 quad 앵커가 엉뚱한 곳에 박힌다.
        Log.d(
            TAG,
            "hitTestSourceRegion: 화면(%.0f,%.0f)px → hitPose=%s · 현재 camPose=%s".format(
                cx, cy, poseStr(hit?.hitPose), poseStr(space.latestFrame?.camera?.pose),
            ),
        )
        return hit
    }

    /** 커버 quad(AnchorNode + ImageNode)를 만들어 씬에 붙인다. */
    private fun buildResultNode(anchor: Anchor, isVertical: Boolean, patch: Bitmap) {
        // 빌보드 가림막은 선택 영역 딱 그 크기면 실물 가장자리가 삐져나온다 → 여유를 더한다.
        // 측정값(patchWidthM/HeightM) 자체는 안 건드린다(마커 크기는 실측 그대로 써야 하므로).
        val coverW = if (coverIsBillboard) patchWidthM * COVER_MARGIN else patchWidthM
        val coverH = if (coverIsBillboard) patchHeightM * COVER_MARGIN else patchHeightM
        val image = ImageNode(
            materialLoader = sceneView.materialLoader,
            bitmap = patch,
            size = Size(coverW, coverH),
        ).apply {
            isTouchable = false
            // 수직 평면(벽): 앵커 로컬 +Y 가 벽 바깥이므로 quad 를 X축 -90° 세운다.
            rotation = if (isVertical) Rotation(x = -90f) else Rotation(0f, 0f, 0f)
        }
        val node = AnchorNode(sceneView.engine, anchor).apply {
            isPositionEditable = false
            // pose 는 매 프레임 스무딩해서 직접 넣는다 (onFrame). SceneView 자동 갱신 끔.
            updateAnchorPose = false
            addChildNode(image)
        }
        sceneView.addChildNode(node)
        resultNode = node
        resultImageNode = image
        smoothedPos = null
        awaitingCoverAnchor = false
        pendingCoverPatch = null
        pendingCoverRegion = null
        coverCamPoseAtBuild = space.latestFrame?.camera?.pose
        Log.d(
            TAG,
            "buildResultNode: node#%d anchorPose=%s vertical=%b billboard=%b patch=%.2fx%.2fm cover=%.2fx%.2fm camAtBuild=%s".format(
                node.hashCode(), poseStr(anchor.pose), isVertical, coverIsBillboard,
                patchWidthM, patchHeightM, coverW, coverH, poseStr(coverCamPoseAtBuild),
            ),
        )
    }

    /**
     * 매 프레임 호출.
     * 1. 커버 quad 를 아직 못 만들었으면 사물 영역을 hitTest 해 잡히는 즉시 만든다.
     * 2. 커버 quad 위치를 벽 앵커에 스무딩해서 고정한다. **[showingAfter](전체화면
     *    프리뷰) 와 무관하게 항상 보이게 한다** — 라이브에서도 실제 사물을 가려야 하므로.
     *    - 앵커가 STOPPED 면 숨긴다(트래킹을 잃음). TRACKING 이 아니면 마지막 위치 유지.
     *    - 위치에 이동 평균(EMA)을 걸어 재추적 지터를 완화한다.
     */
    fun onFrame() {
        if (awaitingCoverAnchor && resultNode == null && !coverQuadDisabled) {
            val region = pendingCoverRegion
            val patch = pendingCoverPatch
            if (region != null && patch != null) {
                val hit = hitTestSourceRegion(region)
                val fresh = hit?.let {
                    it.createAnchorOrNull()
                        ?: runCatching { sceneView.session?.createAnchor(it.hitPose) }.getOrNull()
                }
                if (fresh != null) {
                    val isVertical = (hit.trackable as? Plane)?.type == Plane.Type.VERTICAL
                    wallAnchor = fresh
                    planeIsVertical = isVertical
                    coverIsBillboard = !isVertical
                    buildResultNode(fresh, isVertical, patch)
                    status("삭제 자리에 결과가 고정되었습니다")
                    Log.d(TAG, "onFrame: 커버 quad 앵커 확보 (vertical=$isVertical)")
                }
            }
        }

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
        if (coverIsBillboard) {
            // 빌보드: 부모는 **위치만**(회전 항등), 자식 quad 를 카메라 회전 전체(pitch/roll
            // 포함)에 직접 맞춘다 — FurnitureController.billboard() 의 라벨 처리와 동일.
            // (부모에 회전을 걸면 앵커 pose 갱신과 섞여 45° 어긋났다.)
            node.pose = Pose(floatArrayOf(nx, ny, nz), IDENTITY_QUAT)
            resultImageNode?.worldQuaternion = sceneView.cameraNode.worldQuaternion
        } else {
            node.pose = Pose(floatArrayOf(nx, ny, nz), quat)
        }
        node.isVisible = true   // 라이브/프리뷰 무관하게 항상 — 실제 사물을 계속 가린다.

        // TEMP-DIAG(B): "잔상"이 재투영 어긋남인지 확인. 커버 quad 생성 시점 카메라와 지금
        // 카메라의 위치·회전 차이가 클수록, 평면 이미지 1장으론 시차(parallax)를 못 살려
        // 실제 사물과 quad 가 어긋나 보인다(반투명 잔상). anchorΔ 는 앵커 자체 표류.
        if (++coverFrameLog % 60L == 0L) {
            val camNow = space.latestFrame?.camera?.pose
            val built = coverCamPoseAtBuild
            if (camNow != null && built != null) {
                Log.d(
                    TAG,
                    ("[cover B] 커버 생성시점 대비 카메라 Δ이동=%.2fm Δ회전=%.1f° · " +
                        "현재 카메라→커버앵커=%.2fm · anchorΔ(pose vs 스무딩)=%.3fm · track=%s").format(
                        distance(camNow, built), quatAngleDeg(camNow, built),
                        distance(camNow, anchor.pose),
                        distance(anchor.pose, Pose(floatArrayOf(nx, ny, nz), quat)),
                        ts,
                    ),
                )
            }
        }
    }

    /** 두 pose 회전의 각도 차(도). 커버 quad 재투영 어긋남 진단용. */
    private fun quatAngleDeg(a: Pose, b: Pose): Float {
        val qa = FloatArray(4).also { a.getRotationQuaternion(it, 0) }
        val qb = FloatArray(4).also { b.getRotationQuaternion(it, 0) }
        var dot = qa[0] * qb[0] + qa[1] * qb[1] + qa[2] * qb[2] + qa[3] * qb[3]
        dot = abs(dot).coerceIn(0f, 1f)
        return Math.toDegrees(2.0 * acos(dot.toDouble())).toFloat()
    }

    private fun poseStr(p: Pose?): String =
        if (p == null) "null" else "t=(%.2f,%.2f,%.2f)".format(p.tx(), p.ty(), p.tz())

    // ---------------------------------------------- 전체화면 결과 프리뷰 (P1-9)

    /**
     * "삭제 결과 보기" ↔ "결과 닫기 (라이브로)".
     * **전체화면 프리뷰([R.id.resultOverlay])만** 켜고 끈다. 월드 앵커 커버
     * quad([resultNode])는 여기서 건드리지 않으며 항상 렌더링된다.
     */
    fun toggleBeforeAfter() {
        if (binding.resultOverlay.drawable == null) return
        showingAfter = !showingAfter
        binding.resultOverlay.visibility = if (showingAfter) View.VISIBLE else View.GONE
        binding.btnToggleRemoval.text = if (showingAfter) "결과 닫기 (라이브로)" else "삭제 결과 보기"
    }

    // -------------------------------------------------------------- 내부 유틸

    private fun clearResult() {
        resultNode?.let { node ->
            Log.d(TAG, "clearResult: 커버 quad node#${node.hashCode()} 제거")
            sceneView.removeChildNode(node)
            runCatching { node.destroy() }
        }
        resultNode = null
        resultImageNode = null
        smoothedPos = null
        awaitingCoverAnchor = false
        coverQuadDisabled = false
        coverIsBillboard = false
        pendingCoverPatch = null
        pendingCoverRegion = null
        coverCamPoseAtBuild = null
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
                // 커버 quad 는 캡처 후 다시 항상 보이게 (onFrame 이 재확인하지만 즉시 복구).
                resultNode?.isVisible = true
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
        binding.objectTypeSpinner.isEnabled = enabled
        refreshRequestButton()   // bbox + 사물 종류 조건까지 함께 본다
    }

    private companion object {
        const val TAG = "InteriorAR"

        /** 선택 사각형이 화면 면적의 이 비율 이상이면 "넓다" 경고. */
        const val LARGE_SELECTION_FRACTION = 0.40f

        /** 결과 quad 위치 이동 평균 계수(0~1). 작을수록 부드럽지만 반응이 느리다. */
        const val SMOOTH_ALPHA = 0.2f

        /** 회전 없음 쿼터니언 (x,y,z,w). 빌보드일 때 부모 AnchorNode 에 위치만 주려고 쓴다. */
        val IDENTITY_QUAT = floatArrayOf(0f, 0f, 0f, 1f)

        /** 빌보드 가림막을 선택 영역보다 이 배율만큼 살짝 키운다(하드 엣지 방지, 크게는 안 함). */
        const val COVER_MARGIN = 1.12f

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
