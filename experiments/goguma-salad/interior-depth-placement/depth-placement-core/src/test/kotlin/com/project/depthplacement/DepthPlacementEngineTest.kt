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

    @Test fun `stable preset placement uses dense ROI independent of sparse display cloud`() {
        val stable = DepthPlacementEngineFactory.create(
            SensitivityPreset.LOW.applyTo(config).copy(globalStride = 6, maxPointCount = 5_000),
        )
        try {
            stable.start()
            stable.updateDepthFrame(floorFrame())
            assertTrue(stable.getLatestPointCloud()!!.pointCount < 100)
            val result = stable.evaluatePlacement(20f, 20f, PlacementObjectSize(0.2f, 0.2f, 0.5f))
            assertTrue(result.isValid, result.toString())
            assertTrue(result.validPointCount >= 35)
        } finally {
            stable.release()
        }
    }

    @Test fun `placement near depth edge fits tapped surface and keeps anchor at tap`() {
        val depth = ShortArray(40 * 40) { index -> if (index % 40 < 20) 1000.toShort() else 2000.toShort() }
        engine.start()
        engine.updateDepthFrame(wallFrame(depth))
        val result = engine.evaluatePlacement(18f, 20f, PlacementObjectSize(0.04f, 0.2f, 0.05f), PlacementTarget.WALL)
        assertTrue(result.isValid, result.toString())
        assertEquals(1f, result.depthMeters, 0.03f)
        assertEquals(-0.02f, result.pose!!.position.x, 0.015f)
    }

    @Test fun `dense central island cannot support a larger footprint`() {
        val depth = ShortArray(40 * 40)
        for (v in 14..26) for (u in 14..26) depth[v * 40 + u] = 1000
        engine.start()
        engine.updateDepthFrame(floorFrame(depth))
        val result = engine.evaluatePlacement(20f, 20f, PlacementObjectSize(0.35f, 0.35f, 0.5f))
        assertFalse(result.isValid)
        assertEquals(PlacementFailureReason.INSUFFICIENT_SURFACE, result.failureReason)
    }

    @Test fun `footprint extending beyond observed floor is rejected`() {
        engine.start()
        engine.updateDepthFrame(floorFrame())
        val result = engine.evaluatePlacement(20f, 20f, PlacementObjectSize(2f, 2f, 0.5f))
        assertFalse(result.isValid)
        assertEquals(PlacementFailureReason.INSUFFICIENT_SURFACE, result.failureReason)
    }

    @Test fun `missing outer support rejects an otherwise dense floor`() {
        val depth = ShortArray(40 * 40) { index -> if (index % 40 < 30) 1000 else 0 }
        engine.start()
        engine.updateDepthFrame(floorFrame(depth))
        val result = engine.evaluatePlacement(20f, 20f, PlacementObjectSize(0.4f, 0.3f, 0.5f))
        assertFalse(result.isValid)
        assertEquals(PlacementFailureReason.INSUFFICIENT_SURFACE, result.failureReason)
    }

    @Test fun `scattered missing depth still permits supported floor`() {
        val depth = ShortArray(40 * 40) { index -> if (index % 7 == 0) 0 else 1000 }
        engine.start()
        engine.updateDepthFrame(floorFrame(depth))
        assertTrue(engine.evaluatePlacement(20f, 20f, PlacementObjectSize(0.3f, 0.3f, 0.5f)).isValid)
    }

    @Test fun `small wall patch cannot support a large wall object`() {
        engine.start()
        engine.updateDepthFrame(wallFrame())
        val result = engine.evaluatePlacement(20f, 20f, PlacementObjectSize(1f, 1f, 0.05f), PlacementTarget.WALL)
        assertFalse(result.isValid)
        assertEquals(PlacementFailureReason.INSUFFICIENT_SURFACE, result.failureReason)
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

    private fun wallFrame(depth: ShortArray = ShortArray(40 * 40) { 1000.toShort() }) = DepthFrameInput(
        width = 40,
        height = 40,
        depthMillimeters = depth,
        intrinsics = CameraIntrinsics(100f, 100f, 20f, 20f),
        cameraPose = CameraPose.identity(),
        timestampNanos = 1_000_000_000L,
    )
}
