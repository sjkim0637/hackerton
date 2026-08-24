package com.geotime.ar.spatial

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpatialVisibilitySelectorTest {
    @Test
    fun selectsCandidateInFrontAndRejectsCandidateBehind() {
        val inFront = candidate("front", Vector3(0f, 0f, -5f))
        val behind = candidate("behind", Vector3(0f, 0f, 5f))
        val result = SpatialVisibilitySelector.select(
            cameraPosition = Vector3(0f, 0f, 0f),
            cameraForward = Vector3(0f, 0f, -1f),
            candidates = listOf(behind, inFront),
        )
        assertEquals(listOf("front"), result.map { it.candidate.id })
        assertEquals(5f, result.single().distanceM, 0.001f)
    }

    @Test
    fun rejectsCandidateOutsideDistance() {
        val result = SpatialVisibilitySelector.select(
            cameraPosition = Vector3(0f, 0f, 0f),
            cameraForward = Vector3(0f, 0f, -1f),
            candidates = listOf(candidate("far", Vector3(0f, 0f, -31f))),
        )
        assertTrue(result.isEmpty())
    }

    private fun candidate(id: String, position: Vector3) = SpatialCandidate(
        id = id,
        title = id,
        position = position,
        minDistanceM = 0f,
        maxDistanceM = 30f,
        viewConeDegrees = 70f,
    )
}

