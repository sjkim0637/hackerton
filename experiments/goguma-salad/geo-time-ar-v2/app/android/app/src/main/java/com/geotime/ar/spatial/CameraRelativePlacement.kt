package com.geotime.ar.spatial

import kotlin.math.cos
import kotlin.math.sin

object CameraRelativePlacement {
    fun inFront(
        cameraPosition: Vector3,
        cameraYawDegrees: Float,
        cameraPitchDegrees: Float,
        distanceM: Float,
    ): Vector3 {
        require(distanceM > 0f) { "distanceM must be positive" }
        val yaw = Math.toRadians(cameraYawDegrees.toDouble())
        val pitch = Math.toRadians(cameraPitchDegrees.toDouble())
        val horizontalDistance = distanceM * cos(pitch).toFloat()
        return Vector3(
            x = cameraPosition.x + horizontalDistance * sin(yaw).toFloat(),
            y = cameraPosition.y + distanceM * sin(pitch).toFloat(),
            z = cameraPosition.z - horizontalDistance * cos(yaw).toFloat(),
        )
    }
}
