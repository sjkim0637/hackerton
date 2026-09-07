package com.project.depthplacement

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sqrt

data class Vec3(val x: Float, val y: Float, val z: Float) {
    operator fun plus(other: Vec3) = Vec3(x + other.x, y + other.y, z + other.z)
    operator fun minus(other: Vec3) = Vec3(x - other.x, y - other.y, z - other.z)
    operator fun times(scale: Float) = Vec3(x * scale, y * scale, z * scale)
    fun dot(other: Vec3) = x * other.x + y * other.y + z * other.z
    fun cross(other: Vec3) = Vec3(y * other.z - z * other.y, z * other.x - x * other.z, x * other.y - y * other.x)
    fun length() = sqrt(dot(this))
    fun normalized(): Vec3 = length().let { if (it < 1e-7f) UP else this * (1f / it) }
    companion object { val UP = Vec3(0f, 1f, 0f) }
}

internal data class PointSample(val position: Vec3, val u: Int, val v: Int, val confidence: Float, val depthMeters: Float = position.length())
internal data class PlaneFit(val center: Vec3, val normal: Vec3, val meanError: Float)

internal object LocalSurfaceEstimator {
    fun fit(points: List<PointSample>): PlaneFit? {
        if (points.size < 3) return null
        val center = points.fold(Vec3(0f, 0f, 0f)) { acc, p -> acc + p.position } * (1f / points.size)
        val covariance = Array(3) { FloatArray(3) }
        for (point in points) {
            val d = point.position - center
            val a = floatArrayOf(d.x, d.y, d.z)
            for (r in 0..2) for (c in 0..2) covariance[r][c] += a[r] * a[c]
        }
        val normal = smallestEigenvector(covariance).let { if (it.y < 0f) it * -1f else it }
        val error = points.sumOf { abs((it.position - center).dot(normal)).toDouble() }.toFloat() / points.size
        return PlaneFit(center, normal, error)
    }

    /** Jacobi rotations for a real symmetric 3x3 matrix. */
    private fun smallestEigenvector(source: Array<FloatArray>): Vec3 {
        val a = Array(3) { r -> DoubleArray(3) { c -> source[r][c].toDouble() } }
        val v = Array(3) { r -> DoubleArray(3) { c -> if (r == c) 1.0 else 0.0 } }
        repeat(12) {
            var p = 0; var q = 1
            if (abs(a[0][2]) > abs(a[p][q])) { p = 0; q = 2 }
            if (abs(a[1][2]) > abs(a[p][q])) { p = 1; q = 2 }
            if (abs(a[p][q]) < 1e-10) return@repeat
            val phi = 0.5 * kotlin.math.atan2(2.0 * a[p][q], a[q][q] - a[p][p])
            val c = cos(phi); val s = kotlin.math.sin(phi)
            for (i in 0..2) {
                val aip = a[i][p]; val aiq = a[i][q]
                a[i][p] = c * aip - s * aiq; a[i][q] = s * aip + c * aiq
            }
            for (i in 0..2) {
                val api = a[p][i]; val aqi = a[q][i]
                a[p][i] = c * api - s * aqi; a[q][i] = s * api + c * aqi
                val vip = v[i][p]; val viq = v[i][q]
                v[i][p] = c * vip - s * viq; v[i][q] = s * vip + c * viq
            }
        }
        val index = (0..2).minBy { a[it][it] }
        return Vec3(v[0][index].toFloat(), v[1][index].toFloat(), v[2][index].toFloat()).normalized()
    }
}

/** Deterministic RANSAC followed by PCA refinement on the best inlier set. */
internal object RobustSurfaceEstimator {
    fun fit(points: List<PointSample>, thresholdMeters: Float, iterations: Int = 48): PlaneFit? {
        if (points.size < 3) return null
        var seed = 0x13579BDF
        fun nextIndex(): Int {
            seed = seed * 1103515245 + 12345
            return (seed ushr 1) % points.size
        }

        var bestInliers: List<PointSample> = emptyList()
        var bestError = Float.POSITIVE_INFINITY
        repeat(iterations) {
            val ia = nextIndex()
            var ib = nextIndex()
            var ic = nextIndex()
            if (ib == ia) ib = (ib + 1) % points.size
            if (ic == ia || ic == ib) ic = (ic + 1 + (if (ic == ia || ic == ib) 1 else 0)) % points.size
            if (ic == ia || ic == ib) return@repeat
            val a = points[ia].position
            val candidate = (points[ib].position - a).cross(points[ic].position - a)
            if (candidate.length() < 1e-5f) return@repeat
            val normal = candidate.normalized()
            val inliers = points.filter { abs((it.position - a).dot(normal)) <= thresholdMeters }
            if (inliers.size < 3) return@repeat
            val error = inliers.sumOf { abs((it.position - a).dot(normal)).toDouble() }.toFloat() / inliers.size
            if (inliers.size > bestInliers.size || (inliers.size == bestInliers.size && error < bestError)) {
                bestInliers = inliers
                bestError = error
            }
        }
        return LocalSurfaceEstimator.fit(bestInliers.takeIf { it.size >= 3 } ?: points)
    }
}

internal fun slopeDegrees(normal: Vec3): Float = Math.toDegrees(acos(abs(normal.normalized().dot(Vec3.UP)).coerceIn(-1f, 1f)).toDouble()).toFloat()

internal fun rotationFromUp(normal: Vec3): FloatArray {
    val n = normal.normalized()
    val dot = Vec3.UP.dot(n).coerceIn(-1f, 1f)
    if (dot > 0.9999f) return floatArrayOf(0f, 0f, 0f, 1f)
    if (dot < -0.9999f) return floatArrayOf(1f, 0f, 0f, 0f)
    val axis = Vec3.UP.cross(n).normalized()
    val half = acos(dot) / 2f
    val sin = kotlin.math.sin(half)
    return floatArrayOf(axis.x * sin, axis.y * sin, axis.z * sin, cos(half))
}
