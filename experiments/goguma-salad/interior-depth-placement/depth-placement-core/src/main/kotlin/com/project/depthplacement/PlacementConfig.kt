package com.project.depthplacement

data class PlacementConfig(
    val globalStride: Int = 4,
    val roiStride: Int = 1,
    val minDepthMeters: Float = 0.2f,
    val maxDepthMeters: Float = 5.0f,
    val depthConfidenceThreshold: Float = 0.5f,
    val temporalSmoothingAlpha: Float = 0.35f,
    val maxPointCount: Int = 20_000,
    val roiSizePixels: Int = 15,
    val minValidPointCount: Int = 20,
    val maxSurfaceSlopeDegrees: Float = 15f,
    val obstacleHeightThresholdMeters: Float = 0.05f,
    val planeDistanceThresholdMeters: Float = 0.025f,
    val placementDepthContinuityMeters: Float = 0.12f,
    val minimumSurfaceConfidence: Float = 0.55f,
    val enableInvalidDepthFilter: Boolean = true,
    val enableDepthJumpFilter: Boolean = true,
    val depthJumpThresholdMeters: Float = 0.15f,
    val enableTemporalSmoothing: Boolean = true,
    val enablePlaneFitting: Boolean = true,
    val enableRansac: Boolean = true,
    val processingFpsLimit: Int = 30,
) {
    init {
        require(globalStride >= 1 && roiStride >= 1)
        require(minDepthMeters >= 0f && maxDepthMeters > minDepthMeters)
        require(depthConfidenceThreshold in 0f..1f)
        require(temporalSmoothingAlpha in 0f..1f)
        require(maxPointCount > 0 && roiSizePixels >= 3 && minValidPointCount >= 3)
        require(placementDepthContinuityMeters > 0f)
        require(maxSurfaceSlopeDegrees in 0f..90f)
        require(processingFpsLimit >= 1)
    }

    companion object { fun default() = PlacementConfig() }
}

enum class SensitivityPreset { LOW, NORMAL, HIGH, CUSTOM }

fun SensitivityPreset.applyTo(base: PlacementConfig): PlacementConfig = when (this) {
    SensitivityPreset.LOW -> base.copy(
        depthConfidenceThreshold = 0.7f,
        minValidPointCount = 35,
        maxSurfaceSlopeDegrees = 10f,
        placementDepthContinuityMeters = 0.08f,
        minimumSurfaceConfidence = 0.72f,
        enableRansac = true,
    )
    SensitivityPreset.NORMAL -> PlacementConfig.default().copy(
        maxPointCount = base.maxPointCount,
        processingFpsLimit = base.processingFpsLimit,
    )
    SensitivityPreset.HIGH -> base.copy(
        depthConfidenceThreshold = 0.25f,
        minValidPointCount = 10,
        maxSurfaceSlopeDegrees = 22f,
        placementDepthContinuityMeters = 0.18f,
        minimumSurfaceConfidence = 0.35f,
        enableRansac = false,
    )
    SensitivityPreset.CUSTOM -> base
}
