package com.geotime.ar.spatial

import kotlin.math.acos
import kotlin.math.sqrt

data class Vector3(val x: Float, val y: Float, val z: Float) {
    operator fun minus(other: Vector3) = Vector3(x - other.x, y - other.y, z - other.z)
    fun length(): Float = sqrt(x * x + y * y + z * z)
    fun normalized(): Vector3 {
        val length = length()
        require(length > 1e-6f) { "cameraForward must be non-zero" }
        return Vector3(x / length, y / length, z / length)
    }
    fun dot(other: Vector3): Float = x * other.x + y * other.y + z * other.z
}

data class SpatialCandidate(
    val id: String,
    val title: String,
    val position: Vector3,
    val minDistanceM: Float,
    val maxDistanceM: Float,
    val viewConeDegrees: Float,
)

data class VisibleCandidate(
    val candidate: SpatialCandidate,
    val distanceM: Float,
    val angleDegrees: Float,
)

object SpatialVisibilitySelector {
    fun select(
        cameraPosition: Vector3,
        cameraForward: Vector3,
        candidates: List<SpatialCandidate>,
    ): List<VisibleCandidate> {
        val forward = cameraForward.normalized()
        return candidates.mapNotNull { candidate ->
            val offset = candidate.position - cameraPosition
            val distance = offset.length()
            if (distance < candidate.minDistanceM || distance > candidate.maxDistanceM) {
                return@mapNotNull null
            }
            val angle = if (distance <= 1e-6f) {
                0f
            } else {
                Math.toDegrees(acos((forward.dot(offset) / distance).coerceIn(-1f, 1f)).toDouble())
                    .toFloat()
            }
            if (angle <= candidate.viewConeDegrees / 2f) {
                VisibleCandidate(candidate, distance, angle)
            } else {
                null
            }
        }.sortedWith(compareBy<VisibleCandidate> { it.angleDegrees }.thenBy { it.distanceM })
    }
}

