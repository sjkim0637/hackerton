package com.hackathon.interior.rgbd

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.Log
import com.google.ar.core.Coordinates2d
import com.google.ar.core.Frame
import com.project.depthplacement.arcore.ArCoreDepthAdapter
import java.io.File
import java.nio.FloatBuffer
import kotlin.math.max
import kotlin.math.min

/** Result of the fully on-device MobileSAM + ROI LaMa path. */
data class OnDeviceEditResult(
    val snapshot: ObjectCaptureSnapshot,
    val cleanedRgb: Bitmap,
    val changedRegion: Rect,
    val placeableObject: PlaceableObject,
)

/**
 * Keeps inference local to the APK.  The source RGB is deliberately the unrotated
 * ARCore CPU image: camera intrinsics, depth and MobileSAM output use this same
 * coordinate system.
 */
class OnDeviceRgbdEditor(private val context: Context) : AutoCloseable {
    private val environment = OrtEnvironment.getEnvironment()
    private var encoder: OrtSession? = null
    private var decoder: OrtSession? = null
    private var lama: OrtSession? = null

    fun capture(frame: Frame, selectionInView: RectF): ObjectCaptureSnapshot? {
        val depth = ArCoreDepthAdapter.convert(frame) ?: return null
        val rgb = ArCoreDepthAdapter.acquireCameraRgb(frame) ?: return null
        val view = floatArrayOf(selectionInView.centerX(), selectionInView.centerY())
        val imagePoint = FloatArray(2)
        frame.transformCoordinates2d(Coordinates2d.VIEW, view, Coordinates2d.IMAGE_PIXELS, imagePoint)
        // Store the click in the otherwise unused first two mask pixels. It avoids a
        // second transient model object and remains inside the immutable snapshot.
        val clickMask = Bitmap.createBitmap(rgb.bitmap.width, rgb.bitmap.height, Bitmap.Config.ARGB_8888)
        clickMask.setPixel(imagePoint[0].toInt().coerceIn(0, clickMask.width - 1), imagePoint[1].toInt().coerceIn(0, clickMask.height - 1), 0xffffffff.toInt())
        return ObjectCaptureSnapshot(
            rgbFrame = rgb.bitmap,
            mask = clickMask,
            depthFrame = depth.input,
            cameraPose = frame.camera.pose,
            cameraIntrinsics = depth.input.intrinsics,
            timestampNanos = frame.timestamp,
        )
    }

    fun edit(captured: ObjectCaptureSnapshot): OnDeviceEditResult? {
        val click = findClick(captured.mask) ?: return null
        val mask = postProcessMask(segment(captured.rgbFrame, click.first, click.second))
        val snapshot = captured.copy(mask = mask)
        val region = maskBounds(mask) ?: return null
        val cleaned = inpaintRoi(snapshot.rgbFrame, mask, region)
        val object3d = RgbdObjectBuilder.build(snapshot) ?: return null
        return OnDeviceEditResult(snapshot, cleaned, region, object3d)
    }

    private fun segment(bitmap: Bitmap, clickX: Int, clickY: Int): Bitmap {
        val longest = max(bitmap.width, bitmap.height)
        val scale = 1024f / longest
        val inputW = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val inputH = (bitmap.height * scale).toInt().coerceAtLeast(1)
        val resized = Bitmap.createScaledBitmap(bitmap, inputW, inputH, true)
        val pixels = IntArray(inputW * inputH)
        resized.getPixels(pixels, 0, inputW, 0, 0, inputW, inputH)
        val imageInput = FloatArray(inputW * inputH * 3)
        for (i in pixels.indices) {
            imageInput[i * 3] = ((pixels[i] shr 16) and 0xff).toFloat()
            imageInput[i * 3 + 1] = ((pixels[i] shr 8) and 0xff).toFloat()
            imageInput[i * 3 + 2] = (pixels[i] and 0xff).toFloat()
        }
        val embedding = runSession(encoderSession(), mapOf(
            "input_image" to tensor(imageInput, longArrayOf(inputH.toLong(), inputW.toLong(), 3)),
        )).first().value as Array<Array<Array<FloatArray>>>
        val pointX = clickX * scale
        val pointY = clickY * scale
        val outputs = runSession(decoderSession(), mapOf(
            "image_embeddings" to tensor4(embedding),
            "point_coords" to tensor(floatArrayOf(pointX, pointY, 0f, 0f), longArrayOf(1, 2, 2)),
            "point_labels" to tensor(floatArrayOf(1f, -1f), longArrayOf(1, 2)),
            "mask_input" to tensor(FloatArray(256 * 256), longArrayOf(1, 1, 256, 256)),
            "has_mask_input" to tensor(floatArrayOf(0f), longArrayOf(1)),
            "orig_im_size" to tensor(floatArrayOf(bitmap.height.toFloat(), bitmap.width.toFloat()), longArrayOf(2)),
        ))
        val masks = outputs[0].value as Array<Array<Array<FloatArray>>>
        val scores = outputs[1].value as Array<FloatArray>
        val best = scores[0].indices.maxByOrNull { scores[0][it] } ?: 0
        return Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888).also { result ->
            val px = IntArray(bitmap.width * bitmap.height)
            for (y in px.indices step bitmap.width) for (x in 0 until bitmap.width) {
                if (masks[0][best][y / bitmap.width][x] > 0f) px[y + x] = 0xffffffff.toInt()
            }
            result.setPixels(px, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        }
    }

    /** remove isolated noise, fill tiny holes, then make a two-pixel soft alpha edge. */
    private fun postProcessMask(source: Bitmap): Bitmap {
        val w = source.width; val h = source.height
        var current = BooleanArray(w * h) { i -> (source.getPixel(i % w, i / w) ushr 24) > 0 }
        repeat(2) { current = dilate(current, w, h) }
        repeat(2) { current = erode(current, w, h) }
        val alpha = IntArray(w * h)
        for (i in current.indices) if (current[i]) alpha[i] = 255
        // one outer dilation yields a small feather transition for composition.
        val outer = dilate(current, w, h)
        for (i in outer.indices) if (!current[i] && outer[i]) alpha[i] = 90
        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { out ->
            out.setPixels(IntArray(w * h) { i -> (alpha[i] shl 24) or 0x00ffffff }, 0, w, 0, 0, w, h)
        }
    }

    private fun dilate(src: BooleanArray, w: Int, h: Int): BooleanArray = BooleanArray(src.size) { i ->
        val x = i % w; val y = i / w
        (-1..1).any { dy -> (-1..1).any { dx -> src[(y + dy).coerceIn(0, h - 1) * w + (x + dx).coerceIn(0, w - 1)] } }
    }
    private fun erode(src: BooleanArray, w: Int, h: Int): BooleanArray = BooleanArray(src.size) { i ->
        val x = i % w; val y = i / w
        (-1..1).all { dy -> (-1..1).all { dx -> src[(y + dy).coerceIn(0, h - 1) * w + (x + dx).coerceIn(0, w - 1)] } }
    }

    private fun inpaintRoi(source: Bitmap, mask: Bitmap, bounds: Rect): Bitmap {
        val marginX = (bounds.width() * 0.45f).toInt()
        val marginY = (bounds.height() * 0.45f).toInt()
        val roi = Rect((bounds.left - marginX).coerceAtLeast(0), (bounds.top - marginY).coerceAtLeast(0), (bounds.right + marginX).coerceAtMost(source.width), (bounds.bottom + marginY).coerceAtMost(source.height))
        val rgbCrop = Bitmap.createBitmap(source, roi.left, roi.top, roi.width(), roi.height())
        val maskCrop = Bitmap.createBitmap(mask, roi.left, roi.top, roi.width(), roi.height())
        val scaledRgb = Bitmap.createScaledBitmap(rgbCrop, 512, 512, true)
        val scaledMask = Bitmap.createScaledBitmap(maskCrop, 512, 512, true)
        val rgb = FloatArray(3 * 512 * 512)
        val m = FloatArray(512 * 512)
        val px = IntArray(512 * 512); scaledRgb.getPixels(px, 0, 512, 0, 0, 512, 512)
        for (i in px.indices) {
            rgb[i] = ((px[i] shr 16) and 0xff) / 255f
            rgb[512 * 512 + i] = ((px[i] shr 8) and 0xff) / 255f
            rgb[2 * 512 * 512 + i] = (px[i] and 0xff) / 255f
            m[i] = if ((scaledMask.getPixel(i % 512, i / 512) ushr 24) > 0) 1f else 0f
        }
        val output = runSession(lamaSession(), mapOf(
            "image" to tensor(rgb, longArrayOf(1, 3, 512, 512)),
            "mask" to tensor(m, longArrayOf(1, 1, 512, 512)),
        )).first().value as Array<Array<Array<FloatArray>>>
        val inpaintPx = IntArray(512 * 512) { i ->
            val r = output[0][0][i / 512][i % 512].toInt().coerceIn(0, 255)
            val g = output[0][1][i / 512][i % 512].toInt().coerceIn(0, 255)
            val b = output[0][2][i / 512][i % 512].toInt().coerceIn(0, 255)
            0xff000000.toInt() or (r shl 16) or (g shl 8) or b
        }
        val restored = Bitmap.createBitmap(inpaintPx, 512, 512, Bitmap.Config.ARGB_8888)
        val restoredSized = Bitmap.createScaledBitmap(restored, roi.width(), roi.height(), true)
        val out = source.copy(Bitmap.Config.ARGB_8888, true)
        val base = IntArray(roi.width() * roi.height())
        val restoredPixels = IntArray(base.size)
        val maskPixels = IntArray(base.size)
        out.getPixels(base, 0, roi.width(), roi.left, roi.top, roi.width(), roi.height())
        restoredSized.getPixels(restoredPixels, 0, roi.width(), 0, 0, roi.width(), roi.height())
        maskCrop.getPixels(maskPixels, 0, roi.width(), 0, 0, roi.width(), roi.height())
        for (i in base.indices) {
            val alpha = (maskPixels[i] ushr 24) / 255f
            if (alpha == 0f) continue
            val br = (base[i] shr 16) and 0xff; val bg = (base[i] shr 8) and 0xff; val bb = base[i] and 0xff
            val rr = (restoredPixels[i] shr 16) and 0xff; val rg = (restoredPixels[i] shr 8) and 0xff; val rb = restoredPixels[i] and 0xff
            base[i] = 0xff000000.toInt() or
                (((br * (1f - alpha) + rr * alpha).toInt()) shl 16) or
                (((bg * (1f - alpha) + rg * alpha).toInt()) shl 8) or
                ((bb * (1f - alpha) + rb * alpha).toInt())
        }
        out.setPixels(base, 0, roi.width(), roi.left, roi.top, roi.width(), roi.height())
        return out
    }

    private fun maskBounds(mask: Bitmap): Rect? {
        var l = mask.width; var t = mask.height; var r = -1; var b = -1
        for (y in 0 until mask.height) for (x in 0 until mask.width) if ((mask.getPixel(x, y) ushr 24) > 0) {
            l = min(l, x); t = min(t, y); r = max(r, x); b = max(b, y)
        }
        return if (r < l || b < t) null else Rect(l, t, r + 1, b + 1)
    }

    private fun findClick(mask: Bitmap): Pair<Int, Int>? {
        for (y in 0 until mask.height) for (x in 0 until mask.width) if (mask.getPixel(x, y) == 0xffffffff.toInt()) return x to y
        return null
    }

    private fun tensor(values: FloatArray, shape: LongArray): OnnxTensor = OnnxTensor.createTensor(environment, FloatBuffer.wrap(values), shape)
    private fun tensor4(values: Array<Array<Array<FloatArray>>>): OnnxTensor = OnnxTensor.createTensor(environment, values)
    private fun runSession(session: OrtSession, inputs: Map<String, OnnxTensor>): List<OnnxTensor> {
        try {
            return session.run(inputs).map { it.value as OnnxTensor }
        } finally { inputs.values.forEach { it.close() } }
    }
    private fun encoderSession(): OrtSession = encoder ?: create("mobilesam.encoder.onnx").also { encoder = it }
    private fun decoderSession(): OrtSession = decoder ?: create("mobilesam.decoder.onnx").also { decoder = it }
    private fun lamaSession(): OrtSession = lama ?: create("lama_fp32.onnx").also { lama = it }
    private fun create(asset: String): OrtSession {
        val file = File(context.filesDir, "onnx/$asset")
        if (!file.exists() || file.length() == 0L) {
            file.parentFile?.mkdirs()
            context.assets.open(asset).use { input -> file.outputStream().use(input::copyTo) }
        }
        Log.i(TAG, "local ONNX session: $asset (${file.length() / 1024 / 1024}MB)")
        return environment.createSession(file.absolutePath, OrtSession.SessionOptions())
    }
    override fun close() { encoder?.close(); decoder?.close(); lama?.close() }
    private companion object { const val TAG = "InteriorRgbd" }
}
