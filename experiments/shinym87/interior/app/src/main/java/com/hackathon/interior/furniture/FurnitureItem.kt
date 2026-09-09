package com.hackathon.interior.furniture

import com.google.android.filament.MaterialInstance
import io.github.sceneview.ar.node.AnchorNode
import io.github.sceneview.node.Node
import io.github.sceneview.math.Size
import io.github.sceneview.math.colorOf
import io.github.sceneview.node.ImageNode

/**
 * 화면에 배치된 가구 하나에 딸린 노드/상태 묶음.
 *
 * 카탈로그 가구는 여러 CubeNode를 조합한 저폴리 3D 모델로 표현한다. 모델 전체의 이동·회전·
 * 크기 조절은 [modelRoot]에 적용하므로 구성 Part가 하나의 가구처럼 움직인다.
 */
class FurnitureItem(
    val anchorNode: AnchorNode,
    val modelRoot: Node,
    val labelNode: ImageNode,
    /** 실물 크기(미터). +/- 또는 핀치로 조절하는 배율의 기준. */
    val baseSize: Size,
    var scaleFactor: Float,
    var name: String,
    /** 수직 평면(벽)에 붙어 있으면 true. 모델 방향/오프셋이 달라진다. */
    var onVerticalPlane: Boolean,
    /** 절차형 모델의 선택 강조에 사용하는 대표 Material.
     * GLB는 자체 텍스처·여러 Material을 보존해야 하므로 null이다. */
    val primaryMaterial: MaterialInstance?,
    /** 회전 버튼으로 누적되는 평면 내 회전각(도). 모델 전체에 적용. */
    var rotationDeg: Float = 0f,
    /** PHASE 5: 카탈로그에서 온 가구면 그 항목 id (`GET /catalog` 의 id). 서버 저장/복원 키. */
    var catalogItemId: String? = null,
    /** 서버 저장용 사물 종류 (카탈로그 category: tv|sofa|table|chair|shelf). 기본 모델은 "other". */
    var objectType: String = "other",
) {
    companion object {
        const val MIN_SCALE = 0.3f
        const val MAX_SCALE = 3.0f

        /** +/- 버튼 한 번에 곱해지는 배율. */
        const val SCALE_STEP = 1.15f

        /** 이름표 가로 폭(미터). 가구 크기와 무관하게 고정. */
        const val LABEL_WIDTH_METERS = 0.1f

        /** 이름표를 가구 윗면에서 얼마나 더 띄울지(미터). */
        const val LABEL_GAP_METERS = 0.05f

        // 반투명 = "아직 실제로 없는, 제안된 배치" 느낌.
        // 실내 조명에서도 형태가 보이도록 채도/알파를 조금 높게 잡는다.
        val COLOR_NORMAL = colorOf(r = 0.25f, g = 0.65f, b = 1.0f, a = 0.72f)
        val COLOR_SELECTED = colorOf(r = 0.5f, g = 1.0f, b = 1.0f, a = 0.82f)

        fun colorFor(category: String) = when (category) {
            "tv" -> colorOf(r = 0.08f, g = 0.10f, b = 0.14f, a = 0.96f)
            "sofa" -> colorOf(r = 0.22f, g = 0.48f, b = 0.72f, a = 0.95f)
            "table" -> colorOf(r = 0.62f, g = 0.38f, b = 0.18f, a = 0.96f)
            "chair" -> colorOf(r = 0.78f, g = 0.56f, b = 0.30f, a = 0.96f)
            "shelf" -> colorOf(r = 0.45f, g = 0.28f, b = 0.16f, a = 0.96f)
            else -> COLOR_NORMAL
        }
    }
}
