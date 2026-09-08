package com.hackathon.interior

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.hackathon.interior.databinding.ActivityCatalogBinding
import com.hackathon.interior.magazine.MagazineFeedProvider
import com.hackathon.interior.magazine.MagazineObject
import com.hackathon.interior.magazine.MagazinePage
import com.hackathon.interior.magazine.MockMagazineFeedProvider
import com.hackathon.interior.settings.AppSettings
import kotlinx.coroutines.launch

/** 세로로 넘기는 인테리어 잡지. 사진 속 객체 자체가 AR 진입점이다. */
class CatalogActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCatalogBinding
    private lateinit var settings: AppSettings
    private val provider: MagazineFeedProvider = MockMagazineFeedProvider()
    private var pages: List<MagazinePage> = emptyList()
    private var pageIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCatalogBinding.inflate(layoutInflater)
        setContentView(binding.root)
        settings = AppSettings(this)

        setupGestures()
        setupSettings()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.catalogSettingsScreen.visibility == View.VISIBLE) {
                    binding.catalogSettingsScreen.visibility = View.GONE
                } else {
                    finish()
                }
            }
        })

        lifecycleScope.launch {
            pages = provider.pages()
            if (pages.isNotEmpty()) renderPage()
        }
    }

    private fun setupGestures() {
        val detector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float,
            ): Boolean {
                val start = e1 ?: return false
                val distance = e2.y - start.y
                if (kotlin.math.abs(distance) < dp(72f) || kotlin.math.abs(velocityY) < 300f) return false
                changePage(if (distance < 0) 1 else -1)
                return true
            }
        })
        val listener = View.OnTouchListener { _, event -> detector.onTouchEvent(event) }
        binding.magazineImage.setOnTouchListener(listener)
        binding.hotspotLayer.setOnTouchListener(listener)
    }

    private fun changePage(delta: Int) {
        if (pages.isEmpty()) return
        val next = (pageIndex + delta).coerceIn(0, pages.lastIndex)
        if (next == pageIndex) return
        pageIndex = next
        binding.magazineImage.animate().alpha(0.25f).setDuration(90).withEndAction {
            renderPage()
            binding.magazineImage.animate().alpha(1f).setDuration(180).start()
        }.start()
    }

    private fun renderPage() {
        val page = pages[pageIndex]
        with(page.crop) {
            binding.magazineImage.setAssetCrop(
                "interior_asset_BG.png", left, top, right, bottom,
                scrimAlpha = 20, fitCenter = true,
            )
        }
        binding.issueText.text = page.issue
        binding.pageTitleText.text = page.title
        binding.pageDescriptionText.text = page.description
        binding.pageIndicatorText.text = "%02d / %02d   ↑↓ 넘기기".format(pageIndex + 1, pages.size)
        binding.hotspotLayer.post { renderHotspots(page) }
    }

    private fun renderHotspots(page: MagazinePage) {
        binding.hotspotLayer.removeAllViews()
        page.objects.forEach { item ->
            val button = Button(this).apply {
                text = "+  ${item.name}"
                textSize = 11f
                setTextColor(Color.WHITE)
                isAllCaps = false
                setPadding(dp(10f).toInt(), 0, dp(10f).toInt(), 0)
                setBackgroundResource(com.hackathon.interior.R.drawable.bg_magazine_hotspot)
                setOnClickListener { openObjectInAr(item) }
            }
            val size = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, dp(42f).toInt())
            binding.hotspotLayer.addView(button, size)
            button.post {
                val point = binding.magazineImage.mapCropPoint(item.x, item.y)
                button.x = (point.x - button.width / 2f).coerceIn(0f, binding.hotspotLayer.width - button.width.toFloat())
                button.y = (point.y - button.height / 2f).coerceIn(0f, binding.hotspotLayer.height - button.height.toFloat())
            }
        }
    }

    private fun openObjectInAr(item: MagazineObject) {
        startActivity(Intent(this, MainActivity::class.java).apply {
            putExtra(EXTRA_OBJECT_ID, item.id)
            putExtra(EXTRA_OBJECT_NAME, item.name)
            putExtra(EXTRA_OBJECT_CATEGORY, item.category)
            putExtra(EXTRA_OBJECT_WIDTH_M, item.widthM)
            putExtra(EXTRA_OBJECT_HEIGHT_M, item.heightM)
            putExtra(EXTRA_OBJECT_DEPTH_M, item.depthM)
            putExtra(EXTRA_OBJECT_ANCHOR, item.anchorHint)
        })
    }

    private fun setupSettings() {
        binding.btnCatalogSettings.setOnClickListener {
            binding.catalogServerUrlInput.setText(settings.serverBaseUrl)
            binding.catalogSettingsScreen.visibility = View.VISIBLE
        }
        binding.btnCatalogSettingsSave.setOnClickListener {
            settings.serverBaseUrl = binding.catalogServerUrlInput.text?.toString().orEmpty()
            Toast.makeText(this, "서버 주소를 저장했습니다.", Toast.LENGTH_SHORT).show()
            binding.catalogSettingsScreen.visibility = View.GONE
        }
        binding.btnCatalogSettingsClose.setOnClickListener {
            binding.catalogSettingsScreen.visibility = View.GONE
        }
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    companion object {
        const val EXTRA_OBJECT_ID = "catalog_object_id"
        const val EXTRA_OBJECT_NAME = "catalog_object_name"
        const val EXTRA_OBJECT_CATEGORY = "catalog_object_category"
        const val EXTRA_OBJECT_WIDTH_M = "catalog_object_width_m"
        const val EXTRA_OBJECT_HEIGHT_M = "catalog_object_height_m"
        const val EXTRA_OBJECT_DEPTH_M = "catalog_object_depth_m"
        const val EXTRA_OBJECT_ANCHOR = "catalog_object_anchor"
    }
}
