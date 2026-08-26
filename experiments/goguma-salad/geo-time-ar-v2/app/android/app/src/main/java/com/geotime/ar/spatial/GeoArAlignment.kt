package com.geotime.ar.spatial

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class GeographicPosition(
    val latitude: Double,
    val longitude: Double,
    val ellipsoidHeightM: Double? = null,
)

data class EnuOffset(
    val eastM: Double,
    val northM: Double,
    val upM: Double?,
)

data class GeoArAlignment(
    val reference: GeographicPosition,
    val referenceAccuracyM: Float,
    val trueHeadingDegrees: Float,
    val headingAccuracyDegrees: Float?,
    val arCameraPosition: Vector3,
    val arCameraYawDegrees: Float,
) {
    private val yawOffsetRadians = Math.toRadians(
        (arCameraYawDegrees - trueHeadingDegrees).toDouble()
    )

    fun transform(
        poi: GeographicPosition,
        poiLocalPosition: Vector3,
    ): Vector3 {
        val poiOffset = Wgs84.toEnu(reference, poi)
        val east = poiOffset.eastM + poiLocalPosition.x
        val north = poiOffset.northM - poiLocalPosition.z
        val cosYaw = cos(yawOffsetRadians)
        val sinYaw = sin(yawOffsetRadians)
        val verticalOffset = poiOffset.upM ?: 0.0
        return Vector3(
            x = (arCameraPosition.x + east * cosYaw + north * sinYaw).toFloat(),
            y = (arCameraPosition.y + verticalOffset + poiLocalPosition.y).toFloat(),
            z = (arCameraPosition.z + east * sinYaw - north * cosYaw).toFloat(),
        )
    }

    val yawOffsetDegrees: Float
        get() = normalizeDegrees(arCameraYawDegrees - trueHeadingDegrees)

    companion object {
        fun normalizeDegrees(value: Float): Float {
            var normalized = value % 360f
            if (normalized > 180f) normalized -= 360f
            if (normalized <= -180f) normalized += 360f
            return normalized
        }
    }
}

object Wgs84 {
    private const val SEMI_MAJOR_AXIS_M = 6_378_137.0
    private const val FIRST_ECCENTRICITY_SQUARED = 6.69437999014e-3

    fun toEnu(origin: GeographicPosition, target: GeographicPosition): EnuOffset {
        val originEcef = toEcef(origin)
        val targetEcef = toEcef(target)
        val dx = targetEcef[0] - originEcef[0]
        val dy = targetEcef[1] - originEcef[1]
        val dz = targetEcef[2] - originEcef[2]
        val latitude = Math.toRadians(origin.latitude)
        val longitude = Math.toRadians(origin.longitude)
        val sinLat = sin(latitude)
        val cosLat = cos(latitude)
        val sinLon = sin(longitude)
        val cosLon = cos(longitude)
        val east = -sinLon * dx + cosLon * dy
        val north = -sinLat * cosLon * dx - sinLat * sinLon * dy + cosLat * dz
        val hasVerticalReference = origin.ellipsoidHeightM != null && target.ellipsoidHeightM != null
        val up = if (hasVerticalReference) {
            cosLat * cosLon * dx + cosLat * sinLon * dy + sinLat * dz
        } else {
            null
        }
        return EnuOffset(east, north, up)
    }

    fun horizontalDistanceM(origin: GeographicPosition, target: GeographicPosition): Double {
        val offset = toEnu(origin, target)
        return sqrt(offset.eastM * offset.eastM + offset.northM * offset.northM)
    }

    fun bearingDegrees(origin: GeographicPosition, target: GeographicPosition): Double {
        val offset = toEnu(origin, target)
        val degrees = Math.toDegrees(atan2(offset.eastM, offset.northM))
        return (degrees + 360.0) % 360.0
    }

    private fun toEcef(position: GeographicPosition): DoubleArray {
        val latitude = Math.toRadians(position.latitude)
        val longitude = Math.toRadians(position.longitude)
        val height = position.ellipsoidHeightM ?: 0.0
        val sinLat = sin(latitude)
        val cosLat = cos(latitude)
        val primeVerticalRadius = SEMI_MAJOR_AXIS_M /
            sqrt(1.0 - FIRST_ECCENTRICITY_SQUARED * sinLat * sinLat)
        return doubleArrayOf(
            (primeVerticalRadius + height) * cosLat * cos(longitude),
            (primeVerticalRadius + height) * cosLat * sin(longitude),
            (primeVerticalRadius * (1.0 - FIRST_ECCENTRICITY_SQUARED) + height) * sinLat,
        )
    }
}
