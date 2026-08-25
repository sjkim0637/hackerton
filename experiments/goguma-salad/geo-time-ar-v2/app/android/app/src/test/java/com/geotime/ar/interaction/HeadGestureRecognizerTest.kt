package com.geotime.ar.interaction

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HeadGestureRecognizerTest {
    @Test
    fun `끄덕였다 돌아오면 pitch 동작으로 인식한다`() {
        val recognizer = HeadGestureRecognizer()

        assertNull(recognizer.update(HeadPose(0f, 0f), 0L))
        assertNull(recognizer.update(HeadPose(1f, 16f), 200L))
        val motion = recognizer.update(HeadPose(0f, 2f), 500L)

        assertEquals(HeadMotionAxis.PITCH, motion?.axis)
    }

    @Test
    fun `좌우로 돌렸다 돌아오면 yaw 방향까지 인식한다`() {
        val recognizer = HeadGestureRecognizer()

        recognizer.update(HeadPose(0f, 0f), 0L)
        recognizer.update(HeadPose(18f, 1f), 150L)
        val motion = recognizer.update(HeadPose(3f, 0f), 450L)

        assertEquals(HeadMotionAxis.YAW, motion?.axis)
        assertEquals(1, motion?.direction)
    }

    @Test
    fun `느린 방향 전환은 제스처로 확정하지 않는다`() {
        val recognizer = HeadGestureRecognizer(maxGestureDurationMs = 600L)

        recognizer.update(HeadPose(0f, 0f), 0L)
        recognizer.update(HeadPose(-15f, 0f), 100L)
        val motion = recognizer.update(HeadPose(-15f, 0f), 800L)

        assertNull(motion)
    }

    @Test
    fun `각도 경계에서도 짧은 차이를 계산한다`() {
        assertEquals(2f, HeadGestureRecognizer.angleDelta(179f, -179f), 0.001f)
    }

    @Test
    fun `전체 재생은 기준 자세에서 상하 15도 기울이면 종료 조건을 만족한다`() {
        val baseline = HeadPose(yawDegrees = 20f, pitchDegrees = 3f)

        assertEquals(
            true,
            HeadGestureRecognizer.hasReachedPitchTilt(
                baseline,
                HeadPose(yawDegrees = 20f, pitchDegrees = 18f),
                thresholdDegrees = 15f,
            ),
        )
        assertEquals(
            true,
            HeadGestureRecognizer.hasReachedPitchTilt(
                baseline,
                HeadPose(yawDegrees = 20f, pitchDegrees = -12f),
                thresholdDegrees = 15f,
            ),
        )
        assertEquals(
            false,
            HeadGestureRecognizer.hasReachedPitchTilt(
                baseline,
                HeadPose(yawDegrees = 20f, pitchDegrees = 17.9f),
                thresholdDegrees = 15f,
            ),
        )
    }
}
