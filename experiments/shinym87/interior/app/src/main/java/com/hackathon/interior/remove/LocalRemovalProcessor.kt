package com.hackathon.interior.remove

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
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
 * 외부 요청 없이 한 장의 카메라 이미지에서 빠르게 삭제 결과를 만든다.
 *
 * ML Kit은 선택 bbox 안 전경을 마스크로 좁히고, OpenCV Telea는 그 마스크 영역을 주변
 * 픽셀로 메운다. 마스크가 준비되지 않았거나 신뢰하기 어려우면 동일한 Telea 처리에 bbox를
 * 사용한다. 즉, 이 클래스에는 Gemini·서버 fallback이 없다.
 */
class LocalRemovalProcessor {
    data class Result(
        val bitmap: Bitmap,
        val usedSubjectMask: Boolean,
        val elapsedMs: Long,
    )

    private val mainHandler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val segmenter = SubjectSegmentation.getClient(
        SubjectSegmenterOptions.Builder()
            .enableForegroundConfidenceMask()
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
            .addOnSuccessListener { result ->
                runInpaint(source, bbox, result.foregroundConfidenceMask, startedAt, onSuccess, onFailure)
            }
            .addOnFailureListener {
                // Play services 모델이 아직 준비되지 않은 첫 실행도 bbox Telea로 바로 처리한다.
                runInpaint(source, bbox, null, startedAt, onSuccess, onFailure)
            }
    }

    private fun runInpaint(
        source: Bitmap,
        bbox: FloatArray,
        confidence: FloatBuffer?,
        startedAt: Long,
        onSuccess: (Result) -> Unit,
        onFailure: (Throwable) -> Unit,
    ) {
        worker.execute {
            runCatching {
                check(OpenCVLoader.initLocal()) { "OpenCV 초기화에 실패했습니다" }
                val mask = buildMask(source.width, source.height, bbox, confidence)
                val usedSubjectMask = mask.second
                val sourceRgba = Mat()
                val sourceRgb = Mat()
                val outputRgb = Mat()
                val outputRgba = Mat()
                val inpaintMask = Mat(source.height, source.width, CvType.CV_8UC1).apply { put(0, 0, mask.first) }
                try {
                    Utils.bitmapToMat(source, sourceRgba)
                    Imgproc.cvtColor(sourceRgba, sourceRgb, Imgproc.COLOR_RGBA2RGB)
                    // 외곽의 그림자·경계까지 함께 메우도록 작은 타원형 확장을 적용한다.
                    Imgproc.dilate(inpaintMask, inpaintMask, Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(9.0, 9.0)))
                    Photo.inpaint(sourceRgb, inpaintMask, outputRgb, 5.0, Photo.INPAINT_TELEA)
                    Imgproc.cvtColor(outputRgb, outputRgba, Imgproc.COLOR_RGB2RGBA)
                    val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
                    Utils.matToBitmap(outputRgba, output)
                    Result(output, usedSubjectMask, (System.nanoTime() - startedAt) / 1_000_000)
                } finally {
                    sourceRgba.release()
                    sourceRgb.release()
                    outputRgb.release()
                    outputRgba.release()
                    inpaintMask.release()
                }
            }.onSuccess { result -> mainHandler.post { onSuccess(result) } }
                .onFailure { error -> mainHandler.post { onFailure(error) } }
        }
    }

    /** foreground mask를 bbox와 교차한다. 너무 작거나 거의 전부인 마스크는 bbox 대체로 본다. */
    private fun buildMask(
        width: Int,
        height: Int,
        bbox: FloatArray,
        confidence: FloatBuffer?,
    ): Pair<ByteArray, Boolean> {
        val mask = ByteArray(width * height)
        val left = (bbox[0] * width).toInt().coerceIn(0, width - 1)
        val top = (bbox[1] * height).toInt().coerceIn(0, height - 1)
        val right = ((bbox[0] + bbox[2]) * width).toInt().coerceIn(left + 1, width)
        val bottom = ((bbox[1] + bbox[3]) * height).toInt().coerceIn(top + 1, height)
        val area = (right - left) * (bottom - top)
        var selected = 0

        confidence?.duplicate()?.apply {
            rewind()
            if (remaining() >= width * height) {
                for (y in top until bottom) for (x in left until right) {
                    if (get(y * width + x) >= SUBJECT_THRESHOLD) {
                        mask[y * width + x] = 0xFF.toByte()
                        selected++
                    }
                }
            }
        }

        val coverage = selected.toFloat() / area.coerceAtLeast(1)
        val useSubjectMask = coverage in MIN_MASK_COVERAGE..MAX_MASK_COVERAGE
        if (!useSubjectMask) {
            for (y in top until bottom) for (x in left until right) mask[y * width + x] = 0xFF.toByte()
        }
        return mask to useSubjectMask
    }

    fun close() {
        segmenter.close()
        worker.shutdown()
    }

    private companion object {
        const val SUBJECT_THRESHOLD = 0.55f
        const val MIN_MASK_COVERAGE = 0.015f
        const val MAX_MASK_COVERAGE = 0.92f
    }
}
