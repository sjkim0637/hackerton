package com.project.depthplacement

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DepthProjectileSimulatorTest {
    @Test fun `identity camera launches along minus z with zero yaw and pitch`() {
        val simulator = DepthProjectileSimulator()
        val state = simulator.launch(CameraPose.identity(), timestampNanos = 1_000_000_000L)
        assertTrue(state.velocity.z < 0f)
        assertEquals(0f, state.launchYawDegrees, 0.001f)
        assertEquals(0f, state.launchPitchDegrees, 0.001f)
    }

    @Test fun `slingshot offsets camera forward by yaw and pitch`() {
        val state = DepthProjectileSimulator().launch(CameraPose.identity(), yawOffsetDegrees = 20f, pitchOffsetDegrees = 12f, power = 0.8f)
        assertTrue(state.velocity.x > 0f)
        assertTrue(state.velocity.y > 0f)
        assertTrue(state.launchYawDegrees > 15f)
        assertTrue(state.launchPitchDegrees > 10f)
    }

    @Test fun `projectile bounces on depth point floor and records hit position`() {
        val points = ArrayList<Float>()
        for (xi in -10..10) for (zi in -30..0) {
            points += xi / 10f; points += 0f; points += zi / 10f; points += 1f
        }
        val snapshot = PointCloudSnapshot(points.toFloatArray(), pointCount = points.size / 4, timestampNanos = 1L, sourceWidth = 1, sourceHeight = 1)
        val simulator = DepthProjectileSimulator()
        simulator.updateGeometry(snapshot)
        val pose = CameraPose.identity().copy(matrix = CameraPose.identity().matrix.copyOf().also { it[7] = 1f })
        simulator.launch(pose, timestampNanos = 0L)
        var state: ProjectileState? = null
        for (frame in 1..120) state = simulator.step(frame * 16_666_667L)
        assertNotNull(state)
        assertTrue(state.bounceCount >= 1, state.toString())
        assertNotNull(state.lastCollisionPoint)
        assertTrue(kotlin.math.abs(state.lastCollisionPoint.y) < 0.03f)
        assertTrue(state.bounceCount <= 3)
    }

    @Test fun `first collision waits until projectile reaches aimed depth surface`() {
        val points = ArrayList<Float>()
        points += 0f; points += 0f; points += -0.4f; points += 1f // isolated near-depth noise
        for (xi in -10..10) for (yi in -10..10) {
            points += xi / 10f; points += yi / 10f; points += -2f; points += 1f
        }
        val simulator = DepthProjectileSimulator()
        simulator.updateGeometry(PointCloudSnapshot(
            points = points.toFloatArray(),
            pointCount = points.size / 4,
            timestampNanos = 1L,
            sourceWidth = 1,
            sourceHeight = 1,
        ))
        val launched = simulator.launch(CameraPose.identity(), timestampNanos = 0L)
        assertNotNull(launched.targetDepthMeters)
        assertEquals(2f, launched.targetDepthMeters, 0.02f) // ignores the isolated 0.4 m point

        var state = simulator.step(200_000_000L)
        assertEquals(0, state?.bounceCount)
        assertTrue((state?.distanceTraveledMeters ?: 0f) > 0.65f)

        for (frame in 13..45) state = simulator.step(frame * 16_666_667L)
        assertNotNull(state)
        assertTrue(state.bounceCount >= 1, state.toString())
        assertTrue(state.distanceTraveledMeters >= 1.7f)
    }
}
