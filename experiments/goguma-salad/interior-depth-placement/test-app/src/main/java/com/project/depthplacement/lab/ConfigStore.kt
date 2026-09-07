package com.project.depthplacement.lab

import android.content.Context
import com.project.depthplacement.PlacementConfig

class ConfigStore(context: Context) {
    private val preferences = context.getSharedPreferences("depth-placement-config", Context.MODE_PRIVATE)

    fun load(): PlacementConfig = PlacementConfig(
        globalStride = preferences.getInt("stride", 4),
        roiStride = preferences.getInt("roiStride", 1),
        minDepthMeters = preferences.getFloat("minDepth", 0.2f),
        maxDepthMeters = preferences.getFloat("maxDepth", 5f),
        depthConfidenceThreshold = preferences.getFloat("confidence", 0.5f),
        temporalSmoothingAlpha = preferences.getFloat("smoothing", 0.35f),
        maxPointCount = preferences.getInt("maxPoints", 20_000),
        roiSizePixels = preferences.getInt("roi", 15),
        minValidPointCount = preferences.getInt("minPoints", 20),
        maxSurfaceSlopeDegrees = preferences.getFloat("slope", 15f),
        obstacleHeightThresholdMeters = preferences.getFloat("obstacle", 0.05f),
        planeDistanceThresholdMeters = preferences.getFloat("planeDistance", 0.025f),
        minimumSurfaceConfidence = preferences.getFloat("surfaceConfidence", 0.55f),
        processingFpsLimit = preferences.getInt("fps", 30),
        enableTemporalSmoothing = preferences.getBoolean("smoothingEnabled", true),
    )

    fun save(value: PlacementConfig) {
        preferences.edit()
            .putInt("stride", value.globalStride)
            .putInt("roiStride", value.roiStride)
            .putFloat("minDepth", value.minDepthMeters)
            .putFloat("maxDepth", value.maxDepthMeters)
            .putFloat("confidence", value.depthConfidenceThreshold)
            .putFloat("smoothing", value.temporalSmoothingAlpha)
            .putInt("maxPoints", value.maxPointCount)
            .putInt("roi", value.roiSizePixels)
            .putInt("minPoints", value.minValidPointCount)
            .putFloat("slope", value.maxSurfaceSlopeDegrees)
            .putFloat("obstacle", value.obstacleHeightThresholdMeters)
            .putFloat("planeDistance", value.planeDistanceThresholdMeters)
            .putFloat("surfaceConfidence", value.minimumSurfaceConfidence)
            .putInt("fps", value.processingFpsLimit)
            .putBoolean("smoothingEnabled", value.enableTemporalSmoothing)
            .apply()
    }

    fun reset(): PlacementConfig { preferences.edit().clear().apply(); return PlacementConfig.default() }
}
