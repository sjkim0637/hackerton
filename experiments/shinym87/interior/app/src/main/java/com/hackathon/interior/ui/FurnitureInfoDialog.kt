package com.hackathon.interior.ui

import android.app.Activity
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import com.hackathon.interior.R
import io.github.sceneview.math.Size

/**
 * 가구 생성 시 이름 + 실물 크기(cm)를 입력받는 팝업.
 *
 * 확인 시 [onCreate] 로 (이름, 실물 크기[m]) 를 넘긴다. 취소/닫기 시 [onCancel].
 */
object FurnitureInfoDialog {

    fun show(
        activity: Activity,
        onCreate: (name: String, baseSize: Size) -> Unit,
        onCancel: () -> Unit,
    ) {
        val view = activity.layoutInflater.inflate(R.layout.dialog_furniture_info, null)
        val nameField = view.findViewById<EditText>(R.id.editName)
        val widthField = view.findViewById<EditText>(R.id.editWidth)
        val heightField = view.findViewById<EditText>(R.id.editHeight)
        val depthField = view.findViewById<EditText>(R.id.editDepth)

        AlertDialog.Builder(activity)
            .setTitle("가구 정보 입력")
            .setView(view)
            .setCancelable(true)
            .setPositiveButton("생성") { _, _ ->
                val name = nameField.text.toString().trim().ifEmpty { "가구" }
                val baseSize = Size(
                    widthField.readCm() / 100f,
                    heightField.readCm() / 100f,
                    depthField.readCm() / 100f,
                )
                onCreate(name, baseSize)
            }
            .setNegativeButton("취소") { _, _ -> onCancel() }
            .setOnCancelListener { onCancel() }
            .show()
    }

    /** 입력값(cm)을 2~200 범위로 읽는다. 비었거나 잘못되면 10cm. */
    private fun EditText.readCm(): Float =
        text.toString().toFloatOrNull()?.coerceIn(2f, 200f) ?: 10f
}
