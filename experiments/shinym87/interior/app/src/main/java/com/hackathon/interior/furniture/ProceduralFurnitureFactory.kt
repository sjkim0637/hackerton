package com.hackathon.interior.furniture

import com.google.android.filament.MaterialInstance
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.material.setReflectance
import io.github.sceneview.material.setRoughness
import io.github.sceneview.math.Position
import io.github.sceneview.math.Size
import io.github.sceneview.math.colorOf
import io.github.sceneview.node.CubeNode
import io.github.sceneview.node.Node

/**
 * 외부 GLB 다운로드 없이도 첫 실행부터 보이는 샘플 저폴리 3D 가구를 만든다.
 * 모든 Part는 실물 [size]의 중심을 원점으로 공유하고 [root] 아래에서 함께 움직인다.
 */
object ProceduralFurnitureFactory {

    data class Result(
        val root: Node,
        val primaryMaterial: MaterialInstance,
    )

    fun create(sceneView: ARSceneView, category: String, size: Size): Result {
        val root = Node(sceneView.engine)
        val primary = material(sceneView, category, roughness = 0.55f)

        fun part(partSize: Size, center: Position, material: MaterialInstance = primary) {
            root.addChildNode(
                CubeNode(
                    engine = sceneView.engine,
                    size = partSize,
                    center = Position(0f),
                    materialInstance = material,
                ).apply { position = center },
            )
        }

        val w = size.x
        val h = size.y
        val d = size.z
        when (category) {
            "tv" -> {
                val screen = material(sceneView, "screen", 0.16f, 0.75f)
                part(Size(w, h, d), Position(0f), primary)
                part(Size(w * 0.92f, h * 0.84f, d * 0.12f), Position(0f, 0f, d * 0.51f), screen)
            }

            "sofa" -> {
                val cushion = material(sceneView, "cushion", 0.82f)
                val leg = material(sceneView, "dark-leg", 0.72f)
                part(Size(w * 0.92f, h * 0.28f, d * 0.78f), Position(0f, -h * 0.18f, d * 0.02f))
                part(Size(w * 0.88f, h * 0.48f, d * 0.18f), Position(0f, h * 0.18f, -d * 0.37f))
                part(Size(w * 0.10f, h * 0.48f, d * 0.82f), Position(-w * 0.45f, -h * 0.02f, 0f))
                part(Size(w * 0.10f, h * 0.48f, d * 0.82f), Position(w * 0.45f, -h * 0.02f, 0f))
                part(Size(w * 0.41f, h * 0.12f, d * 0.62f), Position(-w * 0.22f, h * 0.02f, d * 0.06f), cushion)
                part(Size(w * 0.41f, h * 0.12f, d * 0.62f), Position(w * 0.22f, h * 0.02f, d * 0.06f), cushion)
                for (x in listOf(-0.40f, 0.40f)) for (z in listOf(-0.30f, 0.30f)) {
                    part(Size(w * 0.055f, h * 0.16f, d * 0.055f), Position(w * x, -h * 0.42f, d * z), leg)
                }
            }

            "table" -> {
                val leg = material(sceneView, "wood-leg", 0.66f)
                part(Size(w, h * 0.16f, d), Position(0f, h * 0.40f, 0f))
                for (x in listOf(-0.40f, 0.40f)) for (z in listOf(-0.36f, 0.36f)) {
                    part(Size(w * 0.07f, h * 0.84f, d * 0.08f), Position(w * x, -h * 0.08f, d * z), leg)
                }
            }

            "chair" -> {
                val leg = material(sceneView, "wood-leg", 0.70f)
                part(Size(w * 0.90f, h * 0.12f, d * 0.82f), Position(0f, -h * 0.06f, d * 0.02f))
                part(Size(w * 0.90f, h * 0.48f, d * 0.10f), Position(0f, h * 0.24f, -d * 0.38f))
                for (x in listOf(-0.36f, 0.36f)) for (z in listOf(-0.32f, 0.32f)) {
                    part(Size(w * 0.10f, h * 0.44f, d * 0.10f), Position(w * x, -h * 0.28f, d * z), leg)
                }
            }

            "shelf" -> {
                val dark = material(sceneView, "dark-wood", 0.64f)
                part(Size(w * 0.08f, h, d), Position(-w * 0.46f, 0f, 0f), dark)
                part(Size(w * 0.08f, h, d), Position(w * 0.46f, 0f, 0f), dark)
                for (y in listOf(-0.44f, 0f, 0.44f)) {
                    part(Size(w, h * 0.08f, d), Position(0f, h * y, 0f))
                }
            }

            else -> part(size, Position(0f))
        }
        return Result(root, primary)
    }

    private fun material(
        sceneView: ARSceneView,
        palette: String,
        roughness: Float,
        reflectance: Float = 0.35f,
    ): MaterialInstance = sceneView.materialLoader.createColorInstance(
        color = when (palette) {
            "screen" -> colorOf(0.05f, 0.16f, 0.25f, 1f)
            "cushion" -> colorOf(0.34f, 0.64f, 0.86f, 1f)
            "dark-leg" -> colorOf(0.16f, 0.11f, 0.08f, 1f)
            "wood-leg" -> colorOf(0.35f, 0.19f, 0.09f, 1f)
            "dark-wood" -> colorOf(0.27f, 0.15f, 0.08f, 1f)
            else -> FurnitureItem.colorFor(palette)
        },
    ).apply {
        setRoughness(roughness)
        setReflectance(reflectance)
    }
}
