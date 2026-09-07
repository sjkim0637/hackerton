package com.project.depthplacement

import kotlin.math.abs
import kotlin.math.tan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GeometryTest {
    @Test fun `flat floor normal points upward`() {
        val points = buildList {
            for (x in -5..5) for (z in -5..5) add(PointSample(Vec3(x / 10f, 0f, z / 10f), x, z, 1f))
        }
        val fit = requireNotNull(LocalSurfaceEstimator.fit(points))
        assertTrue(fit.normal.y > 0.999f)
        assertTrue(fit.meanError < 0.0001f)
        assertTrue(slopeDegrees(fit.normal) < 0.1f)
    }

    @Test fun `twenty degree plane is too steep for default config`() {
        val rise = tan(Math.toRadians(20.0)).toFloat()
        val points = buildList {
            for (x in -5..5) for (z in -5..5) add(PointSample(Vec3(x / 10f, rise * z / 10f, z / 10f), x, z, 1f))
        }
        val slope = slopeDegrees(requireNotNull(LocalSurfaceEstimator.fit(points)).normal)
        assertTrue(abs(slope - 20f) < 0.2f)
        assertTrue(slope > PlacementConfig.default().maxSurfaceSlopeDegrees)
    }

    @Test fun `ransac rejects depth outliers before plane refinement`() {
        val points = buildList {
            for (x in -5..5) for (z in -5..5) add(PointSample(Vec3(x / 10f, 0f, z / 10f), x, z, 1f))
            for (index in 0 until 30) {
                add(PointSample(Vec3((index % 6) / 10f, 0.3f + index / 100f, (index / 6) / 10f), index, index, 1f))
            }
        }
        val fit = requireNotNull(RobustSurfaceEstimator.fit(points, thresholdMeters = 0.025f))
        assertTrue(fit.normal.y > 0.995f, fit.toString())
        assertTrue(fit.meanError < 0.005f, fit.toString())
    }
}
