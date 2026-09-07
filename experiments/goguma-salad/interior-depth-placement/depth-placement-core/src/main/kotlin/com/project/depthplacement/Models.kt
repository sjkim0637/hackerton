package com.project.depthplacement

/** Row-major camera-to-world transform. Translation lives at indices 3, 7 and 11. */
data class CameraPose(val matrix: FloatArray) {
    init { require(matrix.size == 16) { "CameraPose requires a 4x4 matrix" } }

    fun transform(x: Float, y: Float, z: Float): Vec3 = Vec3(
        matrix[0] * x + matrix[1] * y + matrix[2] * z + matrix[3],
        matrix[4] * x + matrix[5] * y + matrix[6] * z + matrix[7],
        matrix[8] * x + matrix[9] * y + matrix[10] * z + matrix[11],
    )

    companion object {
        fun identity() = CameraPose(floatArrayOf(
            1f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f,
            0f, 0f, 1f, 0f,
            0f, 0f, 0f, 1f,
        ))
    }
}

data class CameraIntrinsics(
    val fx: Float,
    val fy: Float,
    val cx: Float,
    val cy: Float,
)

/** Depth values are millimetres. Confidence is 0..1 and may be omitted. */
data class DepthFrameInput(
    val width: Int,
    val height: Int,
    val depthMillimeters: ShortArray,
    val confidence: FloatArray? = null,
    val intrinsics: CameraIntrinsics,
    val cameraPose: CameraPose,
    val timestampNanos: Long,
) {
    init {
        require(width > 0 && height > 0)
        require(depthMillimeters.size == width * height)
        require(confidence == null || confidence.size == width * height)
        require(intrinsics.fx > 0f && intrinsics.fy > 0f)
    }
}

data class PlacementObjectSize(
    val widthMeters: Float,
    val depthMeters: Float,
    val heightMeters: Float,
) {
    init { require(widthMeters > 0f && depthMeters > 0f && heightMeters > 0f) }
}

enum class CoordinateSystem { ARCORE_WORLD_METERS }
enum class SurfaceType { FLOOR, HORIZONTAL_SURFACE, WALL, UNKNOWN }
enum class PlacementFailureReason {
    NO_DEPTH_FRAME,
    OUTSIDE_DEPTH_IMAGE,
    INSUFFICIENT_POINTS,
    SURFACE_TOO_STEEP,
    INSUFFICIENT_SURFACE,
    OBSTACLE_DETECTED,
}

data class PlacementPose(
    val position: Vec3,
    /** Quaternion in x, y, z, w order. */
    val rotation: FloatArray,
    val surfaceNormal: Vec3,
)

data class PlacementResult(
    val isValid: Boolean,
    val confidence: Float,
    val surface: SurfaceType,
    val pose: PlacementPose?,
    val failureReason: PlacementFailureReason?,
    val depthMeters: Float,
    val slopeDegrees: Float,
    val validPointCount: Int,
    val obstaclePointCount: Int,
    val evaluationTimeMillis: Double,
)

/** XYZ points followed by confidence: [x, y, z, confidence, ...]. */
class PointCloudSnapshot(
    points: FloatArray,
    imagePoints: FloatArray = FloatArray(0),
    val pointCount: Int,
    val timestampNanos: Long,
    val coordinateSystem: CoordinateSystem = CoordinateSystem.ARCORE_WORLD_METERS,
    val sourceWidth: Int,
    val sourceHeight: Int,
) {
    private val immutablePoints = points.copyOf()
    /** Depth-image samples: [uPx, vPx, depthMeters, confidence, ...]. */
    private val immutableImagePoints = imagePoints.copyOf()
    val imagePointCount: Int = immutableImagePoints.size / 4
    fun copyPoints(): FloatArray = immutablePoints.copyOf()
    fun copyImagePoints(): FloatArray = immutableImagePoints.copyOf()
}

data class ProcessingMetrics(
    val depthFps: Double = 0.0,
    val pointCloudFps: Double = 0.0,
    val pointGenerationMillis: Double = 0.0,
    val placementEvaluationMillis: Double = 0.0,
    val pointCount: Int = 0,
    val validPointRatio: Double = 0.0,
    val invalidPointRatio: Double = 1.0,
)
