package com.hackathon.interior.remove

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import org.opencv.photo.Photo
import java.util.concurrent.Executors

/**
 * 선택한 화면 영역을 기기 안에서 OpenCV Telea로 복원한다.
 *
 * ML Kit Subject Segmentation은 일부 기기에서 Play services 네이티브 크래시를 일으켜
 * 사용하지 않는다. 이 경로는 네트워크나 생성형 AI 없이 실제 픽셀 복원을 수행한다.
 */
class LocalRemovalProcessor {
    data class Result(
        val bitmap: Bitmap,
        val elapsedMs: Long,
    )

    private val mainHandler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()

    fun remove(
        source: Bitmap,
        bbox: FloatArray,
        onSuccess: (Result) -> Unit,
        onFailure: (Throwable) -> Unit,
    ) {
        val startedAt = System.nanoTime()
        worker.execute {
            runCatching {
                check(OpenCVLoader.initLocal()) { "OpenCV 초기화에 실패했습니다" }
                val bounds = pixelBounds(source.width, source.height, bbox)
                val sourceRgba = Mat()
                val sourceRgb = Mat()
                val mask = Mat.zeros(source.height, source.width, CvType.CV_8UC1)
                val outputRgb = Mat()
                val outputRgba = Mat()
                try {
                    Utils.bitmapToMat(source, sourceRgba)
                    Imgproc.cvtColor(sourceRgba, sourceRgb, Imgproc.COLOR_RGBA2RGB)
                    Imgproc.rectangle(
                        mask,
                        Point(bounds[0].toDouble(), bounds[1].toDouble()),
                        Point(bounds[2].toDouble(), bounds[3].toDouble()),
                        Scalar(255.0),
                        -1,
                    )
                    // 경계의 원본 윤곽이 남지 않도록 선택 영역만 아주 조금 확장한다.
                    Imgproc.dilate(
                        mask,
                        mask,
                        Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(7.0, 7.0)),
                    )
                    Photo.inpaint(sourceRgb, mask, outputRgb, 5.0, Photo.INPAINT_TELEA)
                    Imgproc.cvtColor(outputRgb, outputRgba, Imgproc.COLOR_RGB2RGBA)
                    val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
                    Utils.matToBitmap(outputRgba, output)
                    Result(output, (System.nanoTime() - startedAt) / 1_000_000)
                } finally {
                    sourceRgba.release()
                    sourceRgb.release()
                    mask.release()
                    outputRgb.release()
                    outputRgba.release()
                }
            }.onSuccess { result -> mainHandler.post { onSuccess(result) } }
                .onFailure { error -> mainHandler.post { onFailure(error) } }
        }
    }

    fun close() {
        worker.shutdown()
    }

    private fun pixelBounds(width: Int, height: Int, bbox: FloatArray): IntArray {
        val left = (bbox[0] * width).toInt().coerceIn(0, width - 1)
        val top = (bbox[1] * height).toInt().coerceIn(0, height - 1)
        val right = ((bbox[0] + bbox[2]) * width).toInt().coerceIn(left + 1, width)
        val bottom = ((bbox[1] + bbox[3]) * height).toInt().coerceIn(top + 1, height)
        return intArrayOf(left, top, right, bottom)
    }
}
