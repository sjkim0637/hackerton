package com.geotime.ar.interaction

import kotlin.math.abs

data class HeadPose(
    val yawDegrees: Float,
    val pitchDegrees: Float,
    val rollDegrees: Float = 0f,
)

enum class HeadMotionAxis {
    YAW,
    PITCH,
}

data class HeadMotion(
    val axis: HeadMotionAxis,
    val direction: Int,
)

class HeadGestureRecognizer(
    private val triggerDegrees: Float = 13f,
    private val returnDegrees: Float = 5f,
    private val maxGestureDurationMs: Long = 1_200L,
) {
    private var baseline: HeadPose? = null
    private var activeAxis: HeadMotionAxis? = null
    private var activeDirection = 0
    private var peakDegrees = 0f
    private var startedAtMs = 0L

    fun reset(pose: HeadPose? = null) {
        baseline = pose
        activeAxis = null
        activeDirection = 0
        peakDegrees = 0f
        startedAtMs = 0L
    }

    fun update(pose: HeadPose, timestampMs: Long): HeadMotion? {
        val origin = baseline ?: run {
            baseline = pose
            return null
        }
        val yawDelta = angleDelta(origin.yawDegrees, pose.yawDegrees)
        val pitchDelta = pose.pitchDegrees - origin.pitchDegrees
        val axis = activeAxis

        if (axis == null) {
            val yawMagnitude = abs(yawDelta)
            val pitchMagnitude = abs(pitchDelta)
            if (maxOf(yawMagnitude, pitchMagnitude) < triggerDegrees) return null
            activeAxis = if (yawMagnitude >= pitchMagnitude) HeadMotionAxis.YAW else HeadMotionAxis.PITCH
            val initialDelta = if (activeAxis == HeadMotionAxis.YAW) yawDelta else pitchDelta
            activeDirection = if (initialDelta >= 0f) 1 else -1
            peakDegrees = abs(initialDelta)
            startedAtMs = timestampMs
            return null
        }

        val selectedDelta = if (axis == HeadMotionAxis.YAW) yawDelta else pitchDelta
        peakDegrees = maxOf(peakDegrees, abs(selectedDelta))
        if (timestampMs - startedAtMs > maxGestureDurationMs) {
            reset(pose)
            return null
        }
        if (abs(selectedDelta) <= returnDegrees && peakDegrees >= triggerDegrees) {
            val motion = HeadMotion(axis, activeDirection)
            reset(pose)
            return motion
        }
        return null
    }

    companion object {
        fun hasReachedRollTilt(
            baseline: HeadPose,
            pose: HeadPose,
            thresholdDegrees: Float,
        ): Boolean = abs(angleDelta(baseline.rollDegrees, pose.rollDegrees)) >= thresholdDegrees

        fun angleDelta(fromDegrees: Float, toDegrees: Float): Float {
            var delta = (toDegrees - fromDegrees) % 360f
            if (delta > 180f) delta -= 360f
            if (delta < -180f) delta += 360f
            return delta
        }
    }
}
