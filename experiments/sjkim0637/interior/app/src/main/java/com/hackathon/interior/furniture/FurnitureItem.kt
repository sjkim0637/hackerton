package com.hackathon.interior.furniture

import com.google.android.filament.MaterialInstance
import io.github.sceneview.ar.node.AnchorNode
import io.github.sceneview.math.Size
import io.github.sceneview.math.colorOf
import io.github.sceneview.node.CubeNode
import io.github.sceneview.node.ImageNode

/**
 * 화면에 배치된 가구 하나에 딸린 노드/상태 묶음.
 *
 * 현재 3D 모델(glTF) 대신 반투명 큐브로 부피만 표현한다. "아직 실재하지 않는,
 * 제안된 배치"라는 의미를 살리기 위한 임시 표현이며, 이후 실제 가구 모델로 교체한다.
 */
class FurnitureItem(
    val anchorNode: AnchorNode,
    val cubeNode: CubeNode,
    val labelNode: ImageNode,
    /** 실물 크기(미터). +/- 또는 핀치로 조절하는 배율의 기준. */
    val baseSize: Size,
    var scaleFactor: Float,
    var name: String,
    val material: MaterialInstance,
    /** 수직 평면(벽)에 붙어 있으면 true. 큐브 방향/오프셋이 달라진다. */
    var onVerticalPlane: Boolean,
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
        val COLOR_NORMAL = colorOf(r = 0.25f, g = 0.65f, b = 1.0f, a = 0.6f)
        val COLOR_SELECTED = colorOf(r = 0.5f, g = 1.0f, b = 1.0f, a = 0.82f)
    }
}
