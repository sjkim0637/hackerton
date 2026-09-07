package com.project.depthplacement

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DepthPlacementEngineTest {
    private val config = PlacementConfig(
        globalStride = 1,
        roiSizePixels = 11,
        minValidPointCount = 30,
        maxPointCount = 10_000,
        enableDepthJumpFilter = false,
        enableTemporalSmoothing = false,
    )
    private val engine = DepthPlacementEngineFactory.create(config)

    @AfterTest fun close() = engine.release()

    @Test fun `flat synthetic floor returns valid placement`() {
        engine.start()
        engine.updateDepthFrame(floorFrame())
        val result = engine.evaluatePlacement(20f, 20f, PlacementObjectSize(0.2f, 0.2f, 0.5f))
        assertTrue(result.isValid, result.toString())
        assertEquals(SurfaceType.FLOOR, result.surface)
        assertTrue(result.slopeDegrees < 0.2f)
        assertTrue(engine.getLatestPointCloud()!!.pointCount > 1_000)
    }

    @Test fun `raised depth cluster is reported as obstacle`() {
        val depth = ShortArray(40 * 40) { 1000.toShort() }
        for (v in 17..23) for (u in 6..12) depth[v * 40 + u] = 500.toShort()
        engine.start()
        engine.updateDepthFrame(floorFrame(depth))
        val result = engine.evaluatePlacement(20f, 20f, PlacementObjectSize(0.35f, 0.35f, 0.7f))
        assertFalse(result.isValid)
        assertEquals(PlacementFailureReason.OBSTACLE_DETECTED, result.failureReason)
    }

    @Test fun `projection samples are denser than analysis cloud`() {
        val sparseEngine = DepthPlacementEngineFactory.create(config.copy(globalStride = 4))
        try {
            sparseEngine.start()
            sparseEngine.updateDepthFrame(floorFrame())
            val snapshot = sparseEngine.getLatestPointCloud()!!
            assertEquals(100, snapshot.pointCount)
            assertEquals(400, snapshot.imagePointCount)
        } finally {
            sparseEngine.release()
        }
    }

    @Test fun `two depth pixels produce metric 3D length`() {
        engine.start()
        engine.updateDepthFrame(floorFrame())
        val result = engine.measureLength(10f, 20f, 30f, 20f)
        assertTrue(result.isValid, result.toString())
        assertEquals(0.2f, result.lengthMeters, 0.0001f)
        assertEquals(1f, result.startDepthMeters, 0.0001f)
        assertEquals(1f, result.endDepthMeters, 0.0001f)
    }

    @Test fun `vertical depth plane accepts wall object and rejects floor object`() {
        engine.start()
        engine.updateDepthFrame(wallFrame())
        val size = PlacementObjectSize(0.4f, 0.3f, 0.05f)

        val wall = engine.evaluatePlacement(20f, 20f, size, PlacementTarget.WALL)
        assertTrue(wall.isValid, wall.toString())
        assertEquals(SurfaceType.WALL, wall.surface)

        val floor = engine.evaluatePlacement(20f, 20f, size, PlacementTarget.HORIZONTAL)
        assertFalse(floor.isValid)
        assertEquals(PlacementFailureReason.WRONG_SURFACE, floor.failureReason)
    }

    private fun floorFrame(depth: ShortArray = ShortArray(40 * 40) { 1000.toShort() }) = DepthFrameInput(
        width = 40,
        height = 40,
        depthMillimeters = depth,
        intrinsics = CameraIntrinsics(100f, 100f, 20f, 20f),
        cameraPose = CameraPose(floatArrayOf(
            1f, 0f, 0f, 0f,
            0f, 0f, 1f, 1f,
            0f, 1f, 0f, 0f,
            0f, 0f, 0f, 1f,
        )),
        timestampNanos = 1_000_000_000L,
    )

    private fun wallFrame() = DepthFrameInput(
        width = 40,
        height = 40,
        depthMillimeters = ShortArray(40 * 40) { 1000.toShort() },
        intrinsics = CameraIntrinsics(100f, 100f, 20f, 20f),
        cameraPose = CameraPose.identity(),
        timestampNanos = 1_000_000_000L,
    )
}
