package com.hackathon.interior.ar

import com.google.ar.core.HitResult
import com.google.ar.core.Plane

/**
 * 인식·배치 대상으로 삼는 평면 종류와 수직/수평 판별 헬퍼.
 *
 * 설계서 3.1 "벽 또는 바닥에 맞춰 배치" 요구사항에 맞춰 수평면(바닥/책상)과
 * 수직면(벽)을 모두 다룬다.
 */
object PlaneKind {

    /** hitTest 및 평면 인식에 사용할 평면 타입 집합. */
    val PLANE_TYPES: Set<Plane.Type> = setOf(
        Plane.Type.HORIZONTAL_UPWARD_FACING,
        Plane.Type.HORIZONTAL_DOWNWARD_FACING,
        Plane.Type.VERTICAL,
    )

    /** 히트한 Trackable 이 수직 평면(벽)인지. */
    fun isVerticalHit(hit: HitResult): Boolean =
        (hit.trackable as? Plane)?.type == Plane.Type.VERTICAL
}
