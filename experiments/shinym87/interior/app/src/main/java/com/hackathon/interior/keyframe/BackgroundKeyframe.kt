package com.hackathon.interior.keyframe

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.PixelCopy
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.Toast
import io.github.sceneview.ar.ARSceneView
import java.io.File
import java.io.FileOutputStream

/**
 * "빈 배경" 대표 이미지(키프레임) 캡처와 오버레이.
 *
 * 설계서 6.4 원칙("편집 명령 시점의 대표 이미지를 저장")과 시연 시나리오의
 * "변경 전 / 변경 후 비교"를 위한 최소 구현이다. 지금은 현재 카메라 화면(가구 제외)을
 * 저장해 반투명으로 겹쳐, 같은 각도에서 "가구가 없어진 것처럼" 보이게 한다.
 */
class BackgroundKeyframe(
    private val activity: Activity,
    private val sceneView: ARSceneView,
    private val overlay: ImageView,
    private val captureButton: Button,
    private val toggleButton: Button,
    private val opacityBar: SeekBar,
    /** 캡처 직전 호출 — 가구 노드를 숨긴다. */
    private val beforeCapture: () -> Unit,
    /** 캡처 완료(성공/실패) 후 호출 — 가구 노드를 다시 보이게 한다. */
    private val afterCapture: () -> Unit,
) {

    private var backgroundBitmap: Bitmap? = null
    private val backgroundFile = File(activity.filesDir, "empty_background.png")
    private val mainHandler = Handler(Looper.getMainLooper())

    init {
        captureButton.setOnClickListener { capture() }
        toggleButton.setOnClickListener { toggle() }
        opacityBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) {
                overlay.alpha = (progress / 100f).coerceIn(0.05f, 1f)
            }
            override fun onStartTrackingTouch(bar: SeekBar) {}
            override fun onStopTrackingTouch(bar: SeekBar) {}
        })

        // 지난 실행에서 저장한 배경이 있으면 불러온다 (표시는 꺼둔 상태).
        if (backgroundFile.exists()) {
            runCatching { BitmapFactory.decodeFile(backgroundFile.absolutePath) }.getOrNull()?.let {
                backgroundBitmap = it
                overlay.setImageBitmap(it)
                toggleButton.isEnabled = true
            }
        }
    }

    /**
     * 지금 카메라 화면(가구·UI 제외)을 사진으로 찍어 저장한다.
     * PixelCopy 로 윈도우 표면을 읽되, 캡처 순간에는 가구와 오버레이를 잠깐 숨긴다.
     */
    private fun capture() {
        if (sceneView.width == 0 || sceneView.height == 0) return

        val bitmap = Bitmap.createBitmap(sceneView.width, sceneView.height, Bitmap.Config.ARGB_8888)
        val location = IntArray(2)
        sceneView.getLocationInWindow(location)
        val rect = Rect(
            location[0], location[1],
            location[0] + sceneView.width, location[1] + sceneView.height,
        )

        val overlayWasVisible = overlay.visibility == View.VISIBLE
        overlay.visibility = View.GONE
        beforeCapture()

        // 가구 숨김이 AR 렌더 스레드에 반영될 시간을 준 뒤 캡처.
        sceneView.postDelayed({
            PixelCopy.request(activity.window, rect, bitmap, { result ->
                afterCapture()
                if (result == PixelCopy.SUCCESS) {
                    backgroundBitmap = bitmap
                    overlay.setImageBitmap(bitmap)
                    toggleButton.isEnabled = true
                    saveBitmap(bitmap)
                    show(true)
                    Toast.makeText(activity, "빈 배경을 저장했습니다", Toast.LENGTH_SHORT).show()
                } else {
                    if (overlayWasVisible) overlay.visibility = View.VISIBLE
                    Toast.makeText(activity, "촬영 실패 (코드 $result)", Toast.LENGTH_SHORT).show()
                }
            }, mainHandler)
        }, 120L)
    }

    private fun saveBitmap(bitmap: Bitmap) {
        runCatching {
            FileOutputStream(backgroundFile).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }.onFailure { Log.w(TAG, "배경 저장 실패", it) }
    }

    private fun toggle() {
        show(overlay.visibility != View.VISIBLE)
    }

    private fun show(visible: Boolean) {
        if (visible && backgroundBitmap == null) return
        overlay.visibility = if (visible) View.VISIBLE else View.GONE
        opacityBar.visibility = if (visible) View.VISIBLE else View.GONE
        toggleButton.text = if (visible) "배경 숨김" else "배경 표시"
    }

    private companion object {
        const val TAG = "InteriorAR"
    }
}
