package com.hackathon.interior.remove

import com.google.ar.core.Pose
import kotlin.math.sqrt

/**
 * 벽(수직 평면) 커버/마커 quad 를 손 떨림·시선 각도와 무관하게 항상 수평·수직으로
 * 세우기 위한 순수 회전 계산 헬퍼. [RemovalController] 와 [MovedObjectController] 가 공유한다.
 *
 * 규약: ARCore 평면 pose 는 **로컬 +Y 가 평면 법선**이다(hitPose·centerPose 동일).
 * 이 규약을 유지한 채(자식 ImageNode 의 `Rotation(x=-90)` 는 그대로 두고) 부모 앵커의
 * in-plane 축(±X/±Z)만 세계 up 에 맞춰 roll(기울어짐)을 없앤다.
 */
internal object PoseMath {

    /** 회전 없음 쿼터니언 (x,y,z,w). */
    val IDENTITY_QUAT = floatArrayOf(0f, 0f, 0f, 1f)

    fun cross(a: FloatArray, b: FloatArray) = floatArrayOf(
        a[1] * b[2] - a[2] * b[1],
        a[2] * b[0] - a[0] * b[2],
        a[0] * b[1] - a[1] * b[0],
    )

    fun normalize(v: FloatArray): FloatArray {
        val l = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])
        return if (l < 1e-6f) floatArrayOf(0f, 0f, 1f) else floatArrayOf(v[0] / l, v[1] / l, v[2] / l)
    }

    /** 정규직교 열벡터 3개(로컬 X/Y/Z 축)로 이뤄진 회전 → 쿼터니언 [x,y,z,w]. */
    fun quatFromBasis(x: FloatArray, y: FloatArray, z: FloatArray): FloatArray {
        val m00 = x[0]; val m10 = x[1]; val m20 = x[2]
        val m01 = y[0]; val m11 = y[1]; val m21 = y[2]
        val m02 = z[0]; val m12 = z[1]; val m22 = z[2]
        val tr = m00 + m11 + m22
        val q = FloatArray(4)
        when {
            tr > 0f -> {
                val s = sqrt(tr + 1f) * 2f
                q[3] = 0.25f * s; q[0] = (m21 - m12) / s; q[1] = (m02 - m20) / s; q[2] = (m10 - m01) / s
            }
            m00 > m11 && m00 > m22 -> {
                val s = sqrt(1f + m00 - m11 - m22) * 2f
                q[3] = (m21 - m12) / s; q[0] = 0.25f * s; q[1] = (m01 + m10) / s; q[2] = (m02 + m20) / s
            }
            m11 > m22 -> {
                val s = sqrt(1f + m11 - m00 - m22) * 2f
                q[3] = (m02 - m20) / s; q[0] = (m01 + m10) / s; q[1] = 0.25f * s; q[2] = (m12 + m21) / s
            }
            else -> {
                val s = sqrt(1f + m22 - m00 - m11) * 2f
                q[3] = (m10 - m01) / s; q[0] = (m02 + m20) / s; q[1] = (m12 + m21) / s; q[2] = 0.25f * s
            }
        }
        val l = sqrt(q[0] * q[0] + q[1] * q[1] + q[2] * q[2] + q[3] * q[3])
        if (l > 1e-6f) { q[0] /= l; q[1] /= l; q[2] /= l; q[3] /= l }
        return q
    }

    /**
     * 수평 법선 [normalHoriz]([x,_,z], 정규화 불필요)을 바라보는, in-plane 축이 세계 up 에
     * 맞춰진 회전 [x,y,z,w]. 자식 `Rotation(x=-90)` 규약과 짝이 맞아 항상 반듯한 직사각형이 된다.
     */
    fun uprightWallQuatFromNormal(normalHoriz: FloatArray): FloatArray {
        val n = normalize(floatArrayOf(normalHoriz[0], 0f, normalHoriz[2]))
        val up = floatArrayOf(0f, 1f, 0f)
        val x = normalize(cross(up, n))       // 수평, in-plane
        val z = normalize(cross(n, x))        // ≈ 세계 up, in-plane
        return quatFromBasis(x, n, z)
    }

    /**
     * 수직 평면 앵커 pose 를 "완전 수직·수평"으로 세우는 회전 [x,y,z,w].
     * 앵커 로컬 +Y(= 평면 법선)를 수평 투영해 방향으로 삼고 roll 을 없앤다.
     * 법선이 거의 수직이면(벽이 아님) 앵커의 원래 회전을 그대로 반환한다.
     * [towardCamera] 를 주면 법선이 그 카메라 위치를 향하도록 부호를 맞춘다.
     */
    fun uprightWallQuat(anchorPose: Pose, towardCamera: Pose? = null): FloatArray {
        val n = FloatArray(3).also { anchorPose.getTransformedAxis(1, 1f, it, 0) }  // +Y = 법선
        n[1] = 0f
        val len = sqrt(n[0] * n[0] + n[2] * n[2])
        if (len < 0.2f) {
            // 법선이 거의 수직(바닥/천장) → 벽이 아니므로 원본 회전 유지.
            return FloatArray(4).also { anchorPose.getRotationQuaternion(it, 0) }
        }
        n[0] /= len; n[2] /= len
        if (towardCamera != null) {
            val p = FloatArray(3).also { anchorPose.getTranslation(it, 0) }
            if (n[0] * (towardCamera.tx() - p[0]) + n[2] * (towardCamera.tz() - p[2]) < 0f) {
                n[0] = -n[0]; n[2] = -n[2]
            }
        }
        return uprightWallQuatFromNormal(n)
    }
}
