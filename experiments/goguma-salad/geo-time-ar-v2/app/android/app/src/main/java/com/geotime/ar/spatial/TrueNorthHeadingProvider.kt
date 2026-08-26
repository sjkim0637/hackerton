package com.geotime.ar.spatial

import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import android.view.WindowManager
import java.util.ArrayDeque
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

data class HeadingReading(
    val trueHeadingDegrees: Float,
    val accuracyDegrees: Float?,
    val sampleCount: Int,
)

class TrueNorthHeadingProvider(
    context: Context,
    private val onHeading: (HeadingReading) -> Unit,
) : SensorEventListener {
    private val sensorManager = context.getSystemService(SensorManager::class.java)
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val samples = ArrayDeque<HeadingSample>()
    private var declinationDegrees = 0f
    private var sensorAccuracy = SensorManager.SENSOR_STATUS_UNRELIABLE
    private var locationReady = false

    fun start() {
        rotationSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
        samples.clear()
    }

    fun updateLocation(position: GeographicPosition, timestampMs: Long) {
        samples.clear()
        val altitude = position.ellipsoidHeightM?.toFloat() ?: 0f
        declinationDegrees = GeomagneticField(
            position.latitude.toFloat(),
            position.longitude.toFloat(),
            altitude,
            timestampMs,
        ).declination
        locationReady = true
    }

    fun invalidateLocation() {
        locationReady = false
        samples.clear()
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (!locationReady) return
        val rotation = FloatArray(9)
        val adjusted = FloatArray(9)
        SensorManager.getRotationMatrixFromVector(rotation, event.values)
        val (xAxis, yAxis) = when (windowManager.defaultDisplay.rotation) {
            Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
            Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
            Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
            else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
        }
        SensorManager.remapCoordinateSystem(rotation, xAxis, yAxis, adjusted)
        val orientation = FloatArray(3)
        SensorManager.getOrientation(adjusted, orientation)
        val magneticHeading = Math.toDegrees(orientation[0].toDouble()).toFloat()
        val trueHeading = normalize360(magneticHeading + declinationDegrees)
        val nowMs = android.os.SystemClock.elapsedRealtime()
        samples.addLast(HeadingSample(trueHeading, nowMs))
        while (samples.firstOrNull()?.timestampMs?.let { nowMs - it > SAMPLE_WINDOW_MS } == true) {
            samples.removeFirst()
        }
        val radians = samples.map { Math.toRadians(it.degrees.toDouble()) }
        val mean = normalize360(
            Math.toDegrees(
                atan2(
                    radians.sumOf(::sin),
                    radians.sumOf(::cos),
                )
            ).toFloat()
        )
        onHeading(
            HeadingReading(
                trueHeadingDegrees = mean,
                accuracyDegrees = sensorAccuracy.takeIf {
                    it != SensorManager.SENSOR_STATUS_UNRELIABLE
                }?.let(::accuracyEstimateDegrees),
                sampleCount = samples.size,
            )
        )
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        sensorAccuracy = accuracy
    }

    private fun accuracyEstimateDegrees(accuracy: Int): Float = when (accuracy) {
        SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> 5f
        SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> 15f
        else -> 30f
    }

    private fun normalize360(value: Float): Float = ((value % 360f) + 360f) % 360f

    private data class HeadingSample(val degrees: Float, val timestampMs: Long)

    companion object {
        private const val SAMPLE_WINDOW_MS = 1_000L
    }
}
