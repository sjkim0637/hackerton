package com.hackathon.interior.furniture

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.hackathon.interior.databinding.ActivityMainBinding
import com.hackathon.interior.remove.InteriorApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 앱 첫 화면에 노출되는 샘플 가구 브로셔.
 *
 * 서버 Catalog를 우선 사용하되 서버가 꺼져 있어도 내장 샘플 5종을 보여 준다. 사용자는
 * 브로셔를 선택하고 "우리 집에 적용"을 누른 뒤 AR 벽/바닥을 탭해 3D 가구를 배치한다.
 */
class CatalogController(
    private val activity: Activity,
    private val scope: CoroutineScope,
    private val binding: ActivityMainBinding,
    private val serverBaseUrl: () -> String,
    private val onOpen: () -> Unit = {},
    private val onPick: (InteriorApiClient.CatalogItem) -> Unit,
) {
    private var selected: InteriorApiClient.CatalogItem? = null
    private var selectedCard: View? = null

    init {
        binding.btnAddFurniture.setOnClickListener { toggle() }
        binding.btnCatalogClose.setOnClickListener { hide() }
        binding.btnCatalogApply.setOnClickListener { applySelected() }

        // 첫 화면의 핵심 진입점: 카메라 위에 브로셔를 바로 보여 준다.
        show()
    }

    private fun toggle() {
        if (binding.catalogPanel.visibility == View.VISIBLE) hide() else show()
    }

    private fun show() {
        binding.catalogPanel.visibility = View.VISIBLE
        onOpen()
        fetchAndRender()
    }

    private fun hide() {
        binding.catalogPanel.visibility = View.GONE
    }

    private fun fetchAndRender() {
        render(BUILT_IN_SAMPLES)
        val base = serverBaseUrl()
        scope.launch {
            val serverItems = runCatching { InteriorApiClient(base).getCatalog() }.getOrNull()
            // 사용자가 이미 고른 카드가 늦은 서버 응답 때문에 초기화되지 않게 한다.
            if (!serverItems.isNullOrEmpty() && selected == null &&
                binding.catalogPanel.visibility == View.VISIBLE
            ) {
                render(serverItems)
            }
        }
    }

    private fun render(items: List<InteriorApiClient.CatalogItem>) {
        selected = null
        selectedCard = null
        binding.btnCatalogApply.isEnabled = false
        binding.catalogSelectedText.text = "가구를 선택해 크기와 배치 위치를 확인하세요"
        binding.catalogList.removeAllViews()
        items.forEach { item -> binding.catalogList.addView(brochureCard(item)) }
    }

    private fun brochureCard(item: InteriorApiClient.CatalogItem): View {
        val pad = dp(12)
        return LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(pad, pad, pad, pad)
            background = cardBackground(false)
            layoutParams = LinearLayout.LayoutParams(dp(172), dp(178)).apply {
                marginEnd = dp(10)
            }

            addView(TextView(activity).apply {
                text = categoryEmoji(item.category)
                textSize = 36f
                gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(56)))

            addView(TextView(activity).apply {
                text = item.name
                setTextColor(Color.WHITE)
                textSize = 15f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                maxLines = 2
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(44)))

            addView(TextView(activity).apply {
                val cm = { m: Float -> (m * 100f).toInt() }
                text = "${categoryLabel(item.category)} · ${cm(item.widthM)}×${cm(item.heightM)}×${cm(item.depthM)}cm"
                setTextColor(0xFFCFD8DC.toInt())
                textSize = 12f
                gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(36)))

            addView(TextView(activity).apply {
                text = if (item.anchorHint == "wall") "벽면 배치" else "바닥 배치"
                setTextColor(0xFF9BE7A5.toInt())
                textSize = 12f
                gravity = Gravity.CENTER
            })

            setOnClickListener {
                selectedCard?.background = cardBackground(false)
                selectedCard = this
                background = cardBackground(true)
                selected = item
                binding.btnCatalogApply.isEnabled = true
                binding.catalogSelectedText.text =
                    "${item.name} 선택 · ${if (item.anchorHint == "wall") "벽" else "바닥"}에 3D로 배치됩니다"
            }
            isFocusable = true
            contentDescription = "${item.name}, ${categoryLabel(item.category)}, " +
                (if (item.anchorHint == "wall") "벽면 배치" else "바닥 배치")
        }
    }

    private fun applySelected() {
        val item = selected ?: return
        hide()
        onPick(item)
    }

    private fun cardBackground(selected: Boolean) = GradientDrawable().apply {
        cornerRadius = dp(16).toFloat()
        setColor(if (selected) 0xFF176B3A.toInt() else 0xFF263238.toInt())
        setStroke(dp(if (selected) 3 else 1), if (selected) 0xFF9BE7A5.toInt() else 0xFF546E7A.toInt())
    }

    private fun dp(value: Int): Int = (value * activity.resources.displayMetrics.density).toInt()

    private companion object {
        val BUILT_IN_SAMPLES = listOf(
            sample("cat_tv_wall-55", "55인치 벽걸이 TV", "tv", 1.24f, 0.72f, 0.06f, "wall"),
            sample("cat_sofa_nordic-3seat", "노르딕 3인 소파", "sofa", 2.10f, 0.85f, 0.95f, "floor"),
            sample("cat_table_low-oak", "오크 로우 테이블", "table", 1.10f, 0.40f, 0.60f, "floor"),
            sample("cat_chair_dining-basic", "기본 식탁 의자", "chair", 0.46f, 0.90f, 0.50f, "floor"),
            sample("cat_shelf_wall-3tier", "3단 벽 선반", "shelf", 0.80f, 0.90f, 0.25f, "wall"),
        )

        fun sample(id: String, name: String, category: String, w: Float, h: Float, d: Float, anchor: String) =
            InteriorApiClient.CatalogItem(id, name, category, w, h, d, null, anchor)

        fun categoryLabel(category: String) = when (category) {
            "tv" -> "TV"
            "sofa" -> "소파"
            "table" -> "테이블"
            "chair" -> "의자"
            "shelf" -> "선반"
            else -> "가구"
        }

        fun categoryEmoji(category: String) = when (category) {
            "tv" -> "📺"
            "sofa" -> "🛋️"
            "table" -> "▰"
            "chair" -> "🪑"
            "shelf" -> "▤"
            else -> "🏠"
        }
    }
}
