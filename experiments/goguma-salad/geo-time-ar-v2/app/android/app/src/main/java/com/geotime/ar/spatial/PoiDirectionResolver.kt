package com.geotime.ar.spatial

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

data class PoiDirectionCounts(
    val left: Int = 0,
    val right: Int = 0,
    val up: Int = 0,
    val down: Int = 0,
)

object PoiDirectionResolver {
    fun resolve(
        cameraPosition: Vector3,
        cameraForward: Vector3,
        cameraRight: Vector3,
        cameraUp: Vector3,
        candidates: List<SpatialCandidate>,
        visibleCandidateIds: Set<String>,
    ): PoiDirectionCounts {
        var left = 0
        var right = 0
        var up = 0
        var down = 0

        candidates.forEach { candidate ->
            if (candidate.id in visibleCandidateIds) return@forEach
            val offset = candidate.position - cameraPosition
            val distance = offset.length()
            if (distance < candidate.minDistanceM || distance > candidate.maxDistanceM) {
                return@forEach
            }
            val forward = cameraForward.dot(offset)
            val horizontal = cameraRight.dot(offset)
            val vertical = cameraUp.dot(offset)
            val horizontalAngle = atan2(horizontal, forward)
            val verticalAngle = atan2(vertical, sqrt(forward * forward + horizontal * horizontal))
            if (abs(verticalAngle) > abs(horizontalAngle)) {
                if (vertical >= 0f) up++ else down++
            } else {
                if (horizontalAngle >= 0f) right++ else left++
            }
        }
        return PoiDirectionCounts(left = left, right = right, up = up, down = down)
    }
}
