package com.hackathon.interior.rgbd

import android.graphics.Bitmap
import com.google.ar.core.Pose
import com.project.depthplacement.CameraIntrinsics
import com.project.depthplacement.DepthFrameInput

/** 선택 순간에만 유효한 RGB-D 원본. 이후 카메라 이동과 분리된 공간 편집의 기준 데이터다. */
data class ObjectCaptureSnapshot(
    val rgbFrame: Bitmap,
    val mask: Bitmap,
    val depthFrame: DepthFrameInput,
    val cameraPose: Pose,
    val cameraIntrinsics: CameraIntrinsics,
    val timestampNanos: Long,
)

/** GLB와 캡처 객체가 기존 배치 엔진에 공통으로 넘기는 공간 객체 계약. */
data class PlaceableObject(
    val id: String,
    val sourceType: SourceType,
    val mesh: RgbdMesh,
    val texture: Bitmap,
    val widthMeters: Float,
    val heightMeters: Float,
    val depthMeters: Float,
    val pivotMeters: FloatArray,
    val groundPointMeters: FloatArray?,
    val canPlaceOnFloor: Boolean,
    val canPlaceOnWall: Boolean,
) {
    enum class SourceType { GLB, RGBD }
}

/** 카메라 1시점에서 얻는 2.5D surface. 보이지 않는 뒷면을 생성하지 않는다. */
data class RgbdMesh(
    val positions: FloatArray,
    val uvs: FloatArray,
    val indices: IntArray,
)
