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

object ArCoreDepthAdapter {
    fun isDepthSupported(session: Session): Boolean =
        session.isDepthModeSupported(Config.DepthMode.RAW_DEPTH_ONLY) ||
            session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)

    /** Prefers raw depth so confidence can be consumed by the core filters. */
    fun configure(session: Session, config: Config = session.config): Config {
        config.depthMode = when {
            session.isDepthModeSupported(Config.DepthMode.RAW_DEPTH_ONLY) -> Config.DepthMode.RAW_DEPTH_ONLY
            session.isDepthModeSupported(Config.DepthMode.AUTOMATIC) -> Config.DepthMode.AUTOMATIC
            else -> Config.DepthMode.DISABLED
        }
        session.configure(config)
        return config
    }

    fun convert(frame: Frame): ArCoreDepthFrame? {
        val pair = try {
            val depth = frame.acquireRawDepthImage16Bits()
            val confidence = runCatching { frame.acquireRawDepthConfidenceImage() }.getOrNull()
            depth to confidence
        } catch (_: NotYetAvailableException) {
            try { frame.acquireDepthImage16Bits() to null } catch (_: NotYetAvailableException) { return null }
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
