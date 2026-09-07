package com.project.depthplacement.arcore

import android.media.Image
import com.google.ar.core.Config
import com.google.ar.core.Coordinates2d
import com.google.ar.core.Frame
import com.google.ar.core.exceptions.NotYetAvailableException
import com.google.ar.core.Session
import com.project.depthplacement.CameraIntrinsics
import com.project.depthplacement.CameraPose
import com.project.depthplacement.DepthFrameInput
import java.nio.ByteOrder

data class ArCoreDepthFrame(
    val input: DepthFrameInput,
    val cameraTimestampNanos: Long,
) {
    val timestampDeltaNanos: Long get() = input.timestampNanos - cameraTimestampNanos
}

data class CameraPreviewFrame(
    val width: Int,
    val height: Int,
    val argb: IntArray,
    val timestampNanos: Long,
)

object ArCoreDepthAdapter {
    fun isDepthSupported(session: Session): Boolean =
        session.isDepthModeSupported(Config.DepthMode.RAW_DEPTH_ONLY) ||
            session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)

    /** Uses dense, motion-completed depth when available so room contours remain recognizable. */
    fun configure(session: Session, config: Config = session.config): Config {
        prepareConfig(session, config)
        session.configure(config)
        return config
    }

    /** Applies the supported Depth mode when the host framework configures the Session itself. */
    fun prepareConfig(session: Session, config: Config): Config {
        config.depthMode = when {
            session.isDepthModeSupported(Config.DepthMode.AUTOMATIC) -> Config.DepthMode.AUTOMATIC
            session.isDepthModeSupported(Config.DepthMode.RAW_DEPTH_ONLY) -> Config.DepthMode.RAW_DEPTH_ONLY
            else -> Config.DepthMode.DISABLED
        }
        return config
    }

    fun convert(frame: Frame): ArCoreDepthFrame? {
        val pair = try {
            frame.acquireDepthImage16Bits() to null
        } catch (_: NotYetAvailableException) {
            try {
                val depth = frame.acquireRawDepthImage16Bits()
                val confidence = runCatching { frame.acquireRawDepthConfidenceImage() }.getOrNull()
                depth to confidence
            } catch (_: NotYetAvailableException) { return null }
        }
        val depthImage = pair.first
        val confidenceImage = pair.second
        try {
            val width = depthImage.width
            val height = depthImage.height
            val depth = readDepth16(depthImage)
            val confidence = confidenceImage?.let(::readConfidence)
            val sourceIntrinsics = frame.camera.imageIntrinsics
            val dimensions = sourceIntrinsics.imageDimensions
            val focal = sourceIntrinsics.focalLength
            val principal = sourceIntrinsics.principalPoint
            val scaleX = width.toFloat() / dimensions[0]
            val scaleY = height.toFloat() / dimensions[1]
            return ArCoreDepthFrame(
                DepthFrameInput(
                    width = width,
                    height = height,
                    depthMillimeters = depth,
                    confidence = confidence,
                    intrinsics = CameraIntrinsics(focal[0] * scaleX, focal[1] * scaleY, principal[0] * scaleX, principal[1] * scaleY),
                    cameraPose = frame.camera.pose.toCorePose(),
                    timestampNanos = depthImage.timestamp,
                ),
                cameraTimestampNanos = frame.timestamp,
            )
        } finally {
            depthImage.close()
            confidenceImage?.close()
        }
    }

    /**
     * Copies a small portrait preview from ARCore's YUV camera image.
     * It is intentionally capped and throttled by the caller; this is a visual diagnostic, not a recorder.
     */
    fun acquireCameraPreview(frame: Frame, maxLongEdge: Int = 480): CameraPreviewFrame? {
        val image = try { frame.acquireCameraImage() } catch (_: NotYetAvailableException) { return null }
        try {
            val sourceWidth = image.width
            val sourceHeight = image.height
            val step = maxOf(1, maxOf(sourceWidth, sourceHeight) / maxLongEdge)
            // ARCore CPU camera images are sensor-landscape on the supported portrait lab flow.
            val outputWidth = sourceHeight / step
            val outputHeight = sourceWidth / step
            val argb = IntArray(outputWidth * outputHeight)
            val yPlane = image.planes[0]
            val uPlane = image.planes[1]
            val vPlane = image.planes[2]
            val yBuffer = yPlane.buffer.duplicate()
            val uBuffer = uPlane.buffer.duplicate()
            val vBuffer = vPlane.buffer.duplicate()
            for (outY in 0 until outputHeight) {
                for (outX in 0 until outputWidth) {
                    // Rotate the sensor image 90 degrees clockwise for the portrait-only test app.
                    val sourceX = (outY * step).coerceAtMost(sourceWidth - 1)
                    val sourceY = (sourceHeight - 1 - outX * step).coerceAtLeast(0)
                    val y = yBuffer.get(sourceY * yPlane.rowStride + sourceX * yPlane.pixelStride).toInt() and 0xff
                    val chromaX = sourceX / 2
                    val chromaY = sourceY / 2
                    val u = (uBuffer.get(chromaY * uPlane.rowStride + chromaX * uPlane.pixelStride).toInt() and 0xff) - 128
                    val v = (vBuffer.get(chromaY * vPlane.rowStride + chromaX * vPlane.pixelStride).toInt() and 0xff) - 128
                    val r = (y + 1.402f * v).toInt().coerceIn(0, 255)
                    val g = (y - 0.344136f * u - 0.714136f * v).toInt().coerceIn(0, 255)
                    val b = (y + 1.772f * u).toInt().coerceIn(0, 255)
                    argb[outY * outputWidth + outX] = (0xff shl 24) or (r shl 16) or (g shl 8) or b
                }
            }
            return CameraPreviewFrame(outputWidth, outputHeight, argb, image.timestamp)
        } finally {
            image.close()
        }
    }

    /** Converts Android view pixels to the depth image pixel coordinate used by evaluatePlacement. */
    fun viewToDepth(frame: Frame, viewX: Float, viewY: Float, depthWidth: Int, depthHeight: Int): Pair<Float, Float> {
        val view = floatArrayOf(viewX, viewY)
        val image = FloatArray(2)
        frame.transformCoordinates2d(Coordinates2d.VIEW, view, Coordinates2d.IMAGE_PIXELS, image)
        val dimensions = frame.camera.imageIntrinsics.imageDimensions
        return image[0] * depthWidth / dimensions[0] to image[1] * depthHeight / dimensions[1]
    }

    private fun readDepth16(image: Image): ShortArray {
        val plane = image.planes[0]
        val buffer = plane.buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        return ShortArray(image.width * image.height).also { result ->
            for (y in 0 until image.height) for (x in 0 until image.width) {
                result[y * image.width + x] = buffer.getShort(y * plane.rowStride + x * plane.pixelStride)
            }
        }
    }

    private fun readConfidence(image: Image): FloatArray {
        val plane = image.planes[0]
        val buffer = plane.buffer.duplicate()
        return FloatArray(image.width * image.height).also { result ->
            for (y in 0 until image.height) for (x in 0 until image.width) {
                result[y * image.width + x] = (buffer.get(y * plane.rowStride + x * plane.pixelStride).toInt() and 0xff) / 255f
            }
        }
    }

    private fun com.google.ar.core.Pose.toCorePose(): CameraPose {
        val columnMajor = FloatArray(16)
        toMatrix(columnMajor, 0)
        val rowMajor = FloatArray(16)
        for (row in 0..3) for (column in 0..3) rowMajor[row * 4 + column] = columnMajor[column * 4 + row]
        return CameraPose(rowMajor)
    }
}
