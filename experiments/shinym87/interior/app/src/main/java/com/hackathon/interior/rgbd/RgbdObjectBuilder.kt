package com.hackathon.interior.rgbd

import android.graphics.Bitmap
import kotlin.math.max
import kotlin.math.min

/** Mask 내부의 실제 ARCore depth만 사용해 단일 시점 2.5D mesh를 만든다. */
object RgbdObjectBuilder {
    fun build(snapshot: ObjectCaptureSnapshot, sampleStep: Int = 4): PlaceableObject? {
        val depth = snapshot.depthFrame
        val step = sampleStep.coerceAtLeast(2)
        val gridW = (depth.width + step - 1) / step
        val gridH = (depth.height + step - 1) / step
        val vertexIndex = IntArray(gridW * gridH) { -1 }
        val positions = ArrayList<Float>()
        val uvs = ArrayList<Float>()

        for (gy in 0 until gridH) for (gx in 0 until gridW) {
            val x = min(depth.width - 1, gx * step)
            val y = min(depth.height - 1, gy * step)
            if (!insideMask(snapshot.mask, x, y, depth.width, depth.height)) continue
            val zMeters = (depth.depthMillimeters[y * depth.width + x].toInt() and 0xffff) / 1000f
            if (zMeters !in 0.15f..8f) continue
            val intrinsics = depth.intrinsics
            // ARCore camera forward axis is -Z. Local object coordinates stay camera-relative.
            positions += (x - intrinsics.cx) * zMeters / intrinsics.fx
            positions += -(y - intrinsics.cy) * zMeters / intrinsics.fy
            positions += -zMeters
            uvs += x.toFloat() / (depth.width - 1).coerceAtLeast(1)
            uvs += y.toFloat() / (depth.height - 1).coerceAtLeast(1)
            vertexIndex[gy * gridW + gx] = positions.size / 3 - 1
        }

        if (positions.size < 9) return null
        val indices = ArrayList<Int>()
        for (gy in 0 until gridH - 1) for (gx in 0 until gridW - 1) {
            val a = vertexIndex[gy * gridW + gx]
            val b = vertexIndex[gy * gridW + gx + 1]
            val c = vertexIndex[(gy + 1) * gridW + gx]
            val d = vertexIndex[(gy + 1) * gridW + gx + 1]
            if (a >= 0 && b >= 0 && c >= 0 && d >= 0) {
                indices += a; indices += c; indices += b
                indices += b; indices += c; indices += d
            }
        }
        if (indices.isEmpty()) return null

        val bounds = bounds(positions)
        return PlaceableObject(
            id = "rgbd-${snapshot.timestampNanos}",
            sourceType = PlaceableObject.SourceType.RGBD,
            mesh = RgbdMesh(positions.toFloatArray(), uvs.toFloatArray(), indices.toIntArray()),
            texture = maskedTexture(snapshot.rgbFrame, snapshot.mask),
            widthMeters = max(0.02f, bounds[3] - bounds[0]),
            heightMeters = max(0.02f, bounds[4] - bounds[1]),
            depthMeters = max(0.02f, bounds[5] - bounds[2]),
            pivotMeters = floatArrayOf((bounds[0] + bounds[3]) / 2f, bounds[1], (bounds[2] + bounds[5]) / 2f),
            groundPointMeters = floatArrayOf((bounds[0] + bounds[3]) / 2f, bounds[1], (bounds[2] + bounds[5]) / 2f),
            canPlaceOnFloor = true,
            canPlaceOnWall = false,
        )
    }

    private fun insideMask(mask: Bitmap, x: Int, y: Int, depthW: Int, depthH: Int): Boolean {
        val mx = (x.toLong() * mask.width / depthW).toInt().coerceIn(0, mask.width - 1)
        val my = (y.toLong() * mask.height / depthH).toInt().coerceIn(0, mask.height - 1)
        return (mask.getPixel(mx, my) ushr 24) > 96 || (mask.getPixel(mx, my) and 0x00ffffff) != 0
    }

    private fun maskedTexture(rgb: Bitmap, mask: Bitmap): Bitmap {
        val pixels = IntArray(rgb.width * rgb.height)
        rgb.getPixels(pixels, 0, rgb.width, 0, 0, rgb.width, rgb.height)
        for (y in 0 until rgb.height) for (x in 0 until rgb.width) {
            val alpha = mask.getPixel(
                (x.toLong() * mask.width / rgb.width).toInt().coerceIn(0, mask.width - 1),
                (y.toLong() * mask.height / rgb.height).toInt().coerceIn(0, mask.height - 1),
            ) ushr 24
            pixels[y * rgb.width + x] = (alpha shl 24) or (pixels[y * rgb.width + x] and 0x00ffffff)
        }
        return Bitmap.createBitmap(pixels, rgb.width, rgb.height, Bitmap.Config.ARGB_8888)
    }

    private fun bounds(p: List<Float>): FloatArray {
        var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE; var minZ = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE; var maxZ = -Float.MAX_VALUE
        for (i in p.indices step 3) {
            minX = min(minX, p[i]); minY = min(minY, p[i + 1]); minZ = min(minZ, p[i + 2])
            maxX = max(maxX, p[i]); maxY = max(maxY, p[i + 1]); maxZ = max(maxZ, p[i + 2])
        }
        return floatArrayOf(minX, minY, minZ, maxX, maxY, maxZ)
    }
}
