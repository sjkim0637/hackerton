package com.hackathon.interior.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
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
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val clipPath = Path()

    fun setAssetCrop(
        assetName: String,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        scrimAlpha: Int = 0,
    ) {
        bitmap = synchronized(bitmapCache) {
            bitmapCache[assetName]?.get() ?: context.assets.open(assetName)
                .use(BitmapFactory::decodeStream)
                .also { bitmapCache[assetName] = WeakReference(it) }
        }
        normalizedCrop = RectF(left, top, right, bottom)
        this.scrimAlpha = scrimAlpha.coerceIn(0, 255)
        invalidate()
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
        val destinationRatio = width.toFloat() / height
        val cropRatio = crop.width() / crop.height()
        if (cropRatio > destinationRatio) {
            val targetWidth = crop.height() * destinationRatio
            val inset = (crop.width() - targetWidth) / 2f
            crop.inset(inset, 0f)
        } else {
            val targetHeight = crop.width() / destinationRatio
            val inset = (crop.height() - targetHeight) / 2f
            crop.inset(0f, inset)
        }
        clipPath.reset()
        clipPath.addRoundRect(RectF(0f, 0f, width.toFloat(), height.toFloat()), dp(28f), dp(28f), Path.Direction.CW)
        canvas.save()
        canvas.clipPath(clipPath)
        canvas.drawBitmap(
            image,
            Rect(crop.left.toInt(), crop.top.toInt(), crop.right.toInt(), crop.bottom.toInt()),
            Rect(0, 0, width, height),
            paint,
        )
        if (scrimAlpha > 0) {
            paint.color = (scrimAlpha shl 24)
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
            paint.color = 0
        }
        canvas.restore()
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    private companion object {
        /** Hero와 카테고리 View가 같은 6MB atlas bitmap을 한 번만 decode한다. */
        val bitmapCache = mutableMapOf<String, WeakReference<Bitmap>>()
    }
}
