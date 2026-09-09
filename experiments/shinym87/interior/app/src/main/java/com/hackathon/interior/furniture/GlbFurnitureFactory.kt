package com.hackathon.interior.furniture

import com.hackathon.interior.R
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.math.Size
import io.github.sceneview.node.ModelNode
import io.github.sceneview.node.Node

/** APK에 내장한 GLB 가구를 SceneView 노드로 만든다.
 *
 * 모델마다 원본 단위가 달라 가장 긴 축을 카탈로그의 실물 크기에 맞춘다. 따라서 화면에서
 * 지나치게 크거나 작은 에셋 없이도 기존의 배치·이동·핀치 크기 조절 흐름을 그대로 쓴다.
 */
object GlbFurnitureFactory {

    private fun resourceFor(category: String) = when (category) {
        "sofa" -> R.raw.glam_velvet_sofa
        "chair" -> R.raw.sheen_chair
        "table" -> R.raw.table_coffee
        "tv" -> R.raw.television_modern
        "shelf" -> R.raw.bookcase_open
        else -> null
    }

    fun createOrNull(sceneView: ARSceneView, category: String, size: Size): Node? {
        val resource = resourceFor(category) ?: return null
        val instance = runCatching {
            sceneView.modelLoader.createModelInstance(resource)
        }.getOrNull() ?: return null

        val model = ModelNode(instance).apply {
            // 원본 GLB의 좌표계를 유지하면서 실제 가구 크기 범위에 맞춘다.
            scaleToUnitCube(maxOf(size.x, size.y, size.z))
            isShadowCaster = true
            isShadowReceiver = true
        }
        return Node(sceneView.engine).apply { addChildNode(model) }
    }
}
