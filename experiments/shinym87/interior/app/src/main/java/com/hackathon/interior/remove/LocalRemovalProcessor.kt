package com.hackathon.interior.remove

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.Subject
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentationResult
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import org.opencv.photo.Photo
import java.nio.FloatBuffer
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.min

/**
 * 서버 요청 없이 현재 카메라 이미지에서 선택한 사물만 마스킹해 Telea로 복원한다.
 *
 * 전체 전경 마스크 대신 ML Kit의 개별 Subject 마스크 중 선택 사각형과 가장 많이
 * 겹치는 하나를 고른다. 따라서 사람·가구가 함께 보여도 선택한 사물 외 배경을
 * 불필요하게 지우지 않는다. 모델을 받지 못한 첫 실행 등에는 bbox Telea만 사용한다.
 */
class LocalRemovalProcessor {
    data class Result(
        val bitmap: Bitmap,
        val removedObjectBitmap: Bitmap?,
        val usedSubjectMask: Boolean,
        val elapsedMs: Long,
    )

    private val mainHandler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val segmenter = SubjectSegmentation.getClient(
        SubjectSegmenterOptions.Builder()
            .enableForegroundConfidenceMask()
            .enableMultipleSubjects(
                SubjectSegmenterOptions.SubjectResultOptions.Builder()
                    .enableConfidenceMask()
                    .build(),
            )
            .build(),
    )

    fun remove(
        source: Bitmap,
        bbox: FloatArray,
        onSuccess: (Result) -> Unit,
        onFailure: (Throwable) -> Unit,
    ) {
        val startedAt = System.nanoTime()
        segmenter.process(InputImage.fromBitmap(source, 0))
            .addOnSuccessListener { result -> runInpaint(source, bbox, result, startedAt, onSuccess, onFailure) }
            .addOnFailureListener { runInpaint(source, bbox, null, startedAt, onSuccess, onFailure) }
    }

    private fun runInpaint(
        source: Bitmap,
        bbox: FloatArray,
        segmentation: SubjectSegmentationResult?,
        startedAt: Long,
        onSuccess: (Result) -> Unit,
        onFailure: (Throwable) -> Unit,
    ) {
        worker.execute {
            runCatching {
                check(OpenCVLoader.initLocal()) { "OpenCV 초기화에 실패했습니다" }
                val selected = buildMask(source.width, source.height, bbox, segmentation)
                val exactMask = selected.mask
                val sourceRgba = Mat()
                val sourceRgb = Mat()
                val outputRgb = Mat()
                val outputRgba = Mat()
                val inpaintMask = Mat(source.height, source.width, CvType.CV_8UC1).apply { put(0, 0, exactMask) }
                try {
                    Utils.bitmapToMat(source, sourceRgba)
                    Imgproc.cvtColor(sourceRgba, sourceRgb, Imgproc.COLOR_RGBA2RGB)
                    // 윤곽선의 원본 픽셀만 남지 않게 아주 작게 확장한다. 이동용 cutout은 확장 전 마스크를 쓴다.
                    Imgproc.dilate(inpaintMask, inpaintMask, Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(7.0, 7.0)))
                    Photo.inpaint(sourceRgb, inpaintMask, outputRgb, 5.0, Photo.INPAINT_TELEA)
                    Imgproc.cvtColor(outputRgb, outputRgba, Imgproc.COLOR_RGB2RGBA)
                    val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
                    Utils.matToBitmap(outputRgba, output)
                    Result(
                        bitmap = output,
                        removedObjectBitmap = if (selected.usedSubjectMask) createCutout(source, exactMask) else null,
                        usedSubjectMask = selected.usedSubjectMask,
                        elapsedMs = (System.nanoTime() - startedAt) / 1_000_000,
                    )
                } finally {
                    sourceRgba.release(); sourceRgb.release(); outputRgb.release(); outputRgba.release(); inpaintMask.release()
                }
            }.onSuccess { result -> mainHandler.post { onSuccess(result) } }
                .onFailure { error -> mainHandler.post { onFailure(error) } }
        }
    }

    private data class SelectedMask(val mask: ByteArray, val usedSubjectMask: Boolean)

    private fun buildMask(
        width: Int,
        height: Int,
        bbox: FloatArray,
        segmentation: SubjectSegmentationResult?,
    ): SelectedMask {
        val bounds = pixelBounds(width, height, bbox)
        val candidates = segmentation?.subjects.orEmpty()
            .mapNotNull { subjectMask(width, height, bounds, it) }
        val best = candidates.maxByOrNull { it.score }
        if (best != null && best.coverage in MIN_MASK_COVERAGE..MAX_MASK_COVERAGE) {
            return SelectedMask(best.mask, true)
        }

        // 여러 사물로 나뉘지 않은 프레임에서도 전경 마스크를 한 번 더 사용한다.
        val foreground = maskFromConfidence(width, height, bounds, segmentation?.foregroundConfidenceMask)
        if (foreground != null && foreground.coverage in MIN_MASK_COVERAGE..MAX_MASK_COVERAGE) {
            return SelectedMask(foreground.mask, true)
        }

        return SelectedMask(boxMask(width, height, bounds), false)
    }

    private data class MaskCandidate(val mask: ByteArray, val coverage: Float, val score: Float)

    private fun subjectMask(width: Int, height: Int, bounds: IntArray, subject: Subject): MaskCandidate? {
        val confidence = subject.confidenceMask ?: return null
        val mask = ByteArray(width * height)
        val startX = subject.startX.coerceIn(0, width)
        val startY = subject.startY.coerceIn(0, height)
        val endX = min(width, startX + subject.width)
        val endY = min(height, startY + subject.height)
        val localWidth = endX - startX
        val localHeight = endY - startY
        if (localWidth <= 0 || localHeight <= 0) return null
        val buffer = confidence.duplicate().apply { rewind() }
        if (buffer.remaining() < subject.width * subject.height) return null
        var selected = 0
        var centerConfidence = 0f
        val centerX = ((bounds[0] + bounds[2]) / 2).coerceIn(0, width - 1)
        val centerY = ((bounds[1] + bounds[3]) / 2).coerceIn(0, height - 1)
        for (y in 0 until subject.height) for (x in 0 until subject.width) {
            val value = buffer.get()
            val imageX = startX + x
            val imageY = startY + y
            if (imageX !in bounds[0] until bounds[2] || imageY !in bounds[1] until bounds[3]) continue
            if (imageX == centerX && imageY == centerY) centerConfidence = value
            if (value >= SUBJECT_THRESHOLD) { mask[imageY * width + imageX] = 0xFF.toByte(); selected++ }
        }
        val coverage = selected.toFloat() / area(bounds).coerceAtLeast(1)
        return MaskCandidate(mask, coverage, selected + centerConfidence * area(bounds))
    }

    private fun maskFromConfidence(width: Int, height: Int, bounds: IntArray, confidence: FloatBuffer?): MaskCandidate? {
        confidence ?: return null
        val buffer = confidence.duplicate().apply { rewind() }
        if (buffer.remaining() < width * height) return null
        val mask = ByteArray(width * height)
        var selected = 0
        for (y in bounds[1] until bounds[3]) for (x in bounds[0] until bounds[2]) {
            if (buffer.get(y * width + x) >= SUBJECT_THRESHOLD) { mask[y * width + x] = 0xFF.toByte(); selected++ }
        }
        val coverage = selected.toFloat() / area(bounds).coerceAtLeast(1)
        return MaskCandidate(mask, coverage, selected.toFloat())
    }

    private fun createCutout(source: Bitmap, mask: ByteArray): Bitmap? {
        var left = source.width; var top = source.height; var right = -1; var bottom = -1
        for (y in 0 until source.height) for (x in 0 until source.width) if (mask[y * source.width + x].toInt() != 0) {
            left = min(left, x); top = min(top, y); right = max(right, x); bottom = max(bottom, y)
        }
        if (right < left || bottom < top) return null
        val cutout = Bitmap.createBitmap(right - left + 1, bottom - top + 1, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(cutout.width * cutout.height)
        source.getPixels(pixels, 0, cutout.width, left, top, cutout.width, cutout.height)
        for (y in 0 until cutout.height) for (x in 0 until cutout.width) {
            val alpha = mask[(top + y) * source.width + left + x].toInt() and 0xFF
            pixels[y * cutout.width + x] = (alpha shl 24) or (pixels[y * cutout.width + x] and 0x00FFFFFF)
        }
        cutout.setPixels(pixels, 0, cutout.width, 0, 0, cutout.width, cutout.height)
        return cutout
    }

    private fun pixelBounds(width: Int, height: Int, bbox: FloatArray): IntArray {
        val left = (bbox[0] * width).toInt().coerceIn(0, width - 1)
        val top = (bbox[1] * height).toInt().coerceIn(0, height - 1)
        val right = ((bbox[0] + bbox[2]) * width).toInt().coerceIn(left + 1, width)
        val bottom = ((bbox[1] + bbox[3]) * height).toInt().coerceIn(top + 1, height)
        return intArrayOf(left, top, right, bottom)
    }

    private fun boxMask(width: Int, height: Int, bounds: IntArray) = ByteArray(width * height).also { mask ->
        for (y in bounds[1] until bounds[3]) for (x in bounds[0] until bounds[2]) mask[y * width + x] = 0xFF.toByte()
    }
    private fun area(bounds: IntArray) = (bounds[2] - bounds[0]) * (bounds[3] - bounds[1])

    fun close() { segmenter.close(); worker.shutdown() }

    private companion object {
        const val SUBJECT_THRESHOLD = 0.55f
        const val MIN_MASK_COVERAGE = 0.015f
        const val MAX_MASK_COVERAGE = 0.92f
    }
}
