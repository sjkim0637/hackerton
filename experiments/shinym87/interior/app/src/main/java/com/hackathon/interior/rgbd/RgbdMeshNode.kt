package com.hackathon.interior.rgbd

import com.google.android.filament.RenderableManager
import dev.romainguy.kotlin.math.Float2
import dev.romainguy.kotlin.math.Float3
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.geometries.Geometry
import io.github.sceneview.node.GeometryNode
import io.github.sceneview.texture.ImageTexture
import io.github.sceneview.texture.TextureSampler2D

/** Filament renderable for the captured object's depth surface (not an image quad). */
class RgbdMeshNode(sceneView: ARSceneView, object3d: PlaceableObject) : GeometryNode(
    sceneView.engine,
    Geometry.Builder(RenderableManager.PrimitiveType.TRIANGLES)
        .vertices(object3d.mesh.positions.indices.step(3).map { i ->
            val vertex = i / 3
            Geometry.Vertex(
                position = Float3(object3d.mesh.positions[i], object3d.mesh.positions[i + 1], object3d.mesh.positions[i + 2]),
                normal = Float3(0f, 0f, 1f),
                uvCoordinate = Float2(object3d.mesh.uvs[vertex * 2], object3d.mesh.uvs[vertex * 2 + 1]),
            )
        })
        .indices(object3d.mesh.indices.toList())
        .build(sceneView.engine),
    sceneView.materialLoader.createImageInstance(
        ImageTexture.Builder().bitmap(object3d.texture).build(sceneView.engine),
        TextureSampler2D(),
    ),
) {
    init { isTouchable = false }
}
