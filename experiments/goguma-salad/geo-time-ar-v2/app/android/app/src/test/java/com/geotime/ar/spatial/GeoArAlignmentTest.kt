package com.geotime.ar.spatial

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoArAlignmentTest {
    private val tower107 = GeographicPosition(37.5648801960179, 126.991228638001)

    @Test
    fun `WGS84 converts nearby longitude and latitude to east and north meters`() {
        val east = Wgs84.toEnu(tower107, tower107.copy(longitude = tower107.longitude + 0.0001))
        val north = Wgs84.toEnu(tower107, tower107.copy(latitude = tower107.latitude + 0.0001))

        assertTrue(east.eastM in 8.7..8.9)
        assertTrue(kotlin.math.abs(east.northM) < 0.01)
        assertTrue(north.northM in 11.0..11.2)
        assertTrue(kotlin.math.abs(north.eastM) < 0.01)
        assertNull(east.upM)
    }

    @Test
    fun `north maps to minus Z when AR yaw and true heading agree`() {
        val alignment = GeoArAlignment(
            reference = tower107,
            referenceAccuracyM = 3f,
            trueHeadingDegrees = 0f,
            headingAccuracyDegrees = 5f,
            arCameraPosition = Vector3(2f, 0f, 4f),
            arCameraYawDegrees = 0f,
        )
        val northPoi = tower107.copy(latitude = tower107.latitude + 0.0001)

        val result = alignment.transform(northPoi, Vector3(0f, 1.4f, 0f))

        assertEquals(2f, result.x, 0.02f)
        assertTrue(result.z < -7f)
        assertEquals(1.4f, result.y, 0.01f)
    }

    @Test
    fun `north appears left when camera faces east at AR yaw zero`() {
        val alignment = GeoArAlignment(
            reference = tower107,
            referenceAccuracyM = 3f,
            trueHeadingDegrees = 90f,
            headingAccuracyDegrees = 5f,
            arCameraPosition = Vector3(0f, 0f, 0f),
            arCameraYawDegrees = 0f,
        )
        val northPoi = tower107.copy(latitude = tower107.latitude + 0.0001)

        val result = alignment.transform(northPoi, Vector3(0f, 1.4f, 0f))

        assertTrue(result.x < -11f)
        assertEquals(0f, result.z, 0.02f)
    }

    @Test
    fun `ellipsoid heights produce optional vertical offset`() {
        val origin = tower107.copy(ellipsoidHeightM = 50.0)
        val target = tower107.copy(ellipsoidHeightM = 57.5)

        val result = Wgs84.toEnu(origin, target)

        assertEquals(7.5, result.upM!!, 0.001)
    }
}
