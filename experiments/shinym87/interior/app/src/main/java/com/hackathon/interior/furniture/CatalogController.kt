package com.hackathon.interior.furniture

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.View
import android.widget.Button
import android.widget.TextView
import com.hackathon.interior.databinding.ActivityMainBinding
import com.hackathon.interior.remove.InteriorApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * PHASE 5 — 서버 가구 카탈로그(`GET /catalog`) 목록 패널.
 *
 * "가구 추가" 버튼으로 패널을 열고, 한 항목을 고르면 [onPick] 으로 이름/크기/평면종류/썸네일을
 * 넘긴다. 실제 배치는 [FurnitureController.beginCatalogPlacement] 가 맡는다(큐브와 같은 로직).
 * 이 흐름은 "삭제 후 재배치"(PHASE 4)와 완전히 분리된 진입점이다.
 */
class CatalogController(
    private val activity: Activity,
    private val scope: CoroutineScope,
    private val binding: ActivityMainBinding,
    private val serverBaseUrl: () -> String,
    /** 패널을 열 때 호출 (서버 scene 확보 + 이전 세션 카탈로그 배치 복원). */
    private val onOpen: () -> Unit = {},
    /** 패널을 닫을 때 호출. [onOpen]과 함께 평면 격자를 "가구 추가" 중에만 보이게 하는 데 쓴다. */
    private val onClose: () -> Unit = {},
    private val onPick: (InteriorApiClient.CatalogItem, Bitmap?) -> Unit,
    /** 목록 맨 끝의 "직접 만들기(테스트 블록)" 항목을 골랐을 때. */
    private val onCreateTestBlock: () -> Unit = {},
) {

    init {
        binding.btnAddFurniture.setOnClickListener { toggle() }
        binding.btnCatalogClose.setOnClickListener { hide() }
    }

    private fun toggle() {
        if (binding.catalogPanel.visibility == View.VISIBLE) {
            hide()
        } else {
            binding.catalogPanel.visibility = View.VISIBLE
            onOpen()
            fetchAndRender()
        }
    }

    private fun hide() {
        binding.catalogPanel.visibility = View.GONE
        onClose()
    }

    private fun fetchAndRender() {
        setRows(infoRow("불러오는 중…"))
        val base = serverBaseUrl()
        scope.launch {
            val items = try {
                InteriorApiClient(base).getCatalog()
            } catch (e: Exception) {
                setRows(infoRow("카탈로그 불러오기 실패: ${e.message ?: e.javaClass.simpleName}"))
                binding.catalogList.addView(testBlockRow())
                return@launch
            }
            binding.catalogList.removeAllViews()
            items.forEach { binding.catalogList.addView(itemRow(it)) }
            binding.catalogList.addView(testBlockRow())
        }
    }

    /** 실제 3D 모델이 아직 없거나 빠르게 테스트만 해보고 싶을 때 쓰는 반투명 큐브 생성 항목. */
    private fun testBlockRow(): View = Button(activity).apply {
        isAllCaps = false
        text = "직접 만들기 (테스트 블록)"
        setOnClickListener {
            hide()
            onCreateTestBlock()
        }
    }

    private fun itemRow(item: InteriorApiClient.CatalogItem): View {
        val cm = { m: Float -> (m * 100f).toInt() }
        return Button(activity).apply {
            isAllCaps = false
            text = "${item.name}\n${categoryLabel(item.category)} · " +
                "${cm(item.widthM)}×${cm(item.heightM)}×${cm(item.depthM)}cm · " +
                if (item.anchorHint == "wall") "벽" else "바닥"
            setOnClickListener { pick(item) }
        }
    }

    private fun pick(item: InteriorApiClient.CatalogItem) {
        hide()
        val base = serverBaseUrl()
        scope.launch {
            // 썸네일이 있으면 받아서 표시에 쓰고, 없거나 실패하면 null → 큐브+이름표로 폴백.
            val thumb: Bitmap? = item.thumbnailUrl?.let { url ->
                try {
                    val bytes = InteriorApiClient(base).downloadBytes(url)
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                } catch (_: Exception) {
                    null
                }
            }
            onPick(item, thumb)
        }
    }

    private fun setRows(vararg views: View) {
        binding.catalogList.removeAllViews()
        views.forEach { binding.catalogList.addView(it) }
    }

    private fun infoRow(text: String): View = TextView(activity).apply {
        this.text = text
        setTextColor(0xFFFFFFFF.toInt())
        textSize = 13f
        setPadding(8, 16, 8, 16)
    }

    private companion object {
        fun categoryLabel(category: String): String = when (category) {
            "tv" -> "TV"
            "sofa" -> "소파"
            "table" -> "테이블"
            "chair" -> "의자"
            "shelf" -> "선반"
            else -> category
        }
    }
}
