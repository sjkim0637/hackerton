package com.hackathon.interior.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import java.lang.ref.WeakReference

/** 제공된 원본 Asset atlas에서 지정 영역만 비율을 유지해 그리는 View. 원본 파일을 재가공하지 않는다. */
class AtlasCropView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    private var bitmap: Bitmap? = null
    private var normalizedCrop = RectF(0f, 0f, 1f, 1f)
    private var scrimAlpha = 0
    private var fitCenter = false
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val scrimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF000000.toInt() }
    private val clipPath = Path()

    fun setAssetCrop(
        assetName: String,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        scrimAlpha: Int = 0,
        fitCenter: Boolean = false,
    ) {
        bitmap = synchronized(bitmapCache) {
            bitmapCache[assetName]?.get() ?: context.assets.open(assetName)
                .use(BitmapFactory::decodeStream)
                .also { bitmapCache[assetName] = WeakReference(it) }
        }
        normalizedCrop = RectF(left, top, right, bottom)
        this.scrimAlpha = scrimAlpha.coerceIn(0, 255)
        this.fitCenter = fitCenter
        invalidate()
    }

    /** 현재 crop 안의 상대 좌표를 View 좌표로 바꾼다. 잡지 사진의 객체 hotspot 배치에 사용한다. */
    fun mapCropPoint(x: Float, y: Float): PointF {
        val image = bitmap ?: return PointF(0f, 0f)
        val cropWidth = normalizedCrop.width() * image.width
        val cropHeight = normalizedCrop.height() * image.height
        val destination = destinationRect(cropWidth, cropHeight)
        return PointF(
            destination.left + destination.width() * x.coerceIn(0f, 1f),
            destination.top + destination.height() * y.coerceIn(0f, 1f),
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val image = bitmap ?: return
        if (width <= 0 || height <= 0) return
        val crop = RectF(
            normalizedCrop.left * image.width,
            normalizedCrop.top * image.height,
            normalizedCrop.right * image.width,
            normalizedCrop.bottom * image.height,
        )
        val destination = destinationRect(crop.width(), crop.height())
        if (!fitCenter) {
            val destinationRatio = width.toFloat() / height
            val cropRatio = crop.width() / crop.height()
            if (cropRatio > destinationRatio) {
                val targetWidth = crop.height() * destinationRatio
                crop.inset((crop.width() - targetWidth) / 2f, 0f)
            } else {
                val targetHeight = crop.width() / destinationRatio
                crop.inset(0f, (crop.height() - targetHeight) / 2f)
            }
        }
        clipPath.reset()
        clipPath.addRoundRect(RectF(0f, 0f, width.toFloat(), height.toFloat()), dp(28f), dp(28f), Path.Direction.CW)
        canvas.save()
        canvas.clipPath(clipPath)
        canvas.drawBitmap(
            image,
            Rect(crop.left.toInt(), crop.top.toInt(), crop.right.toInt(), crop.bottom.toInt()),
            Rect(
                destination.left.toInt(), destination.top.toInt(),
                destination.right.toInt(), destination.bottom.toInt(),
            ),
            bitmapPaint,
        )
        if (scrimAlpha > 0) {
            scrimPaint.alpha = scrimAlpha
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), scrimPaint)
        }
        canvas.restore()
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    private fun destinationRect(cropWidth: Float, cropHeight: Float): RectF {
        if (!fitCenter || width <= 0 || height <= 0) {
            return RectF(0f, 0f, width.toFloat(), height.toFloat())
        }
        val scale = minOf(width / cropWidth, height / cropHeight)
        val drawWidth = cropWidth * scale
        val drawHeight = cropHeight * scale
        val left = (width - drawWidth) / 2f
        val top = (height - drawHeight) / 2f
        return RectF(left, top, left + drawWidth, top + drawHeight)
    }

    private companion object {
        /** Hero와 카테고리 View가 같은 6MB atlas bitmap을 한 번만 decode한다. */
        val bitmapCache = mutableMapOf<String, WeakReference<Bitmap>>()
    }
}
