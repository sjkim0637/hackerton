package com.geotime.ar.spatial

import org.junit.Assert.assertEquals
import org.junit.Test

class PoiDirectionResolverTest {
    private val cameraPosition = Vector3(0f, 0f, 0f)
    private val cameraForward = Vector3(0f, 0f, -1f)
    private val cameraRight = Vector3(1f, 0f, 0f)
    private val cameraUp = Vector3(0f, 1f, 0f)

    @Test
    fun `classifies offscreen POIs into screen edge directions`() {
        val result = resolve(
            candidate("left", Vector3(-5f, 0f, 0f)),
            candidate("right", Vector3(5f, 0f, 0f)),
            candidate("up", Vector3(0f, 5f, -1f)),
            candidate("down", Vector3(0f, -5f, -1f)),
        )

        assertEquals(PoiDirectionCounts(left = 1, right = 1, up = 1, down = 1), result)
    }

    @Test
    fun `does not guide POIs already visible on screen`() {
        val result = PoiDirectionResolver.resolve(
            cameraPosition,
            cameraForward,
            cameraRight,
            cameraUp,
            listOf(candidate("front", Vector3(0f, 0f, -5f))),
            visibleCandidateIds = setOf("front"),
        )

        assertEquals(PoiDirectionCounts(), result)
    }

    private fun resolve(vararg candidates: SpatialCandidate) = PoiDirectionResolver.resolve(
        cameraPosition,
        cameraForward,
        cameraRight,
        cameraUp,
        candidates.toList(),
        visibleCandidateIds = emptySet(),
    )

    private fun candidate(id: String, position: Vector3) = SpatialCandidate(
        id = id,
        title = id,
        position = position,
        minDistanceM = 0f,
        maxDistanceM = 30f,
        viewConeDegrees = 80f,
    )
}
