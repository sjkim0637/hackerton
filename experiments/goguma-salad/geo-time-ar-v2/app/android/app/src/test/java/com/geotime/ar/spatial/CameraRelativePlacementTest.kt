package com.geotime.ar.spatial

import org.junit.Assert.assertEquals
import org.junit.Test

class CameraRelativePlacementTest {
    @Test
    fun `zero yaw places marker directly in front of camera`() {
        val result = CameraRelativePlacement.inFront(
            cameraPosition = Vector3(1f, 2f, 3f),
            cameraYawDegrees = 0f,
            cameraPitchDegrees = 0f,
            distanceM = 4f,
        )

        assertVector(Vector3(1f, 2f, -1f), result)
    }

    @Test
    fun `camera pitch keeps marker on initial view axis`() {
        val result = CameraRelativePlacement.inFront(
            cameraPosition = Vector3(0f, 0f, 0f),
            cameraYawDegrees = 90f,
            cameraPitchDegrees = 30f,
            distanceM = 4f,
        )

        assertEquals(3.464f, result.x, 0.001f)
        assertEquals(2f, result.y, 0.001f)
        assertEquals(0f, result.z, 0.001f)
    }

    private fun assertVector(expected: Vector3, actual: Vector3) {
        assertEquals(expected.x, actual.x, 0.001f)
        assertEquals(expected.y, actual.y, 0.001f)
        assertEquals(expected.z, actual.z, 0.001f)
    }
}
