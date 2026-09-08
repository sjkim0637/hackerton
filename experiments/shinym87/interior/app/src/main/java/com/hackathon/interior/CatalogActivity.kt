package com.hackathon.interior

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.TextView
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
    private var openTag: View? = null
    private val pulseAnimators = mutableListOf<AnimatorSet>()

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
            if (pages.isEmpty()) return@launch
            renderPage()
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

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                // 마커나 태그가 아닌 화보 여백을 눌렀을 때는 열려 있던 태그만 닫는다.
                closeOpenTag()
                return false
            }
        })
        // 가장 바깥 View 한 곳에서만 받는다. 여러 View에 같은 detector를 달면 터치 하나가
        // 부모로 올라오며 여러 번 전달되어, 탭 한 번에 페이지가 넘어가는 오작동이 생긴다.
        // 점 marker와 태그는 자기 클릭을 먼저 소비하므로 여기까지 올라오지 않는다.
        binding.catalogRoot.setOnTouchListener { _, event -> detector.onTouchEvent(event) }
    }

    private fun changePage(delta: Int) {
        if (pages.isEmpty()) return
        val next = (pageIndex + delta).coerceIn(0, pages.lastIndex)
        if (next == pageIndex) return
        pageIndex = next
        val direction = if (delta > 0) -dp(18f) else dp(18f)
        binding.magazinePage.animate().alpha(0.2f).translationY(direction).setDuration(110).withEndAction {
            renderPage()
            binding.magazinePage.translationY = -direction
            binding.magazinePage.animate().alpha(1f).translationY(0f).setDuration(200).start()
        }.start()
    }

    private fun renderPage() {
        val page = pages[pageIndex]
        with(page.crop) {
            // 화보 사진을 잘리는 부분 없이 화면에 맞춰 보여 준다. 사진 안에 이미 제목과 설명이 있다.
            binding.magazineImage.setAssetCrop(page.asset, left, top, right, bottom, fitCenter = true)
        }
        binding.hotspotLayer.post { renderHotspots(page) }
    }

    /**
     * 화보 위에는 텍스트 버튼을 바로 노출하지 않는다. 대신 은은하게 숨쉬는 점 마커만 두고,
     * 마커를 누르면 그 옆에 가구 이름과 "AR로 보기" 태그가 떠오른다. 태그를 다시 누르면 AR로 이동한다.
     */
    private fun renderHotspots(page: MagazinePage) {
        binding.hotspotLayer.removeAllViews()
        pulseAnimators.forEach { it.cancel() }
        pulseAnimators.clear()
        openTag = null

        page.objects.forEach { item ->
            val markerSize = dp(42f).toInt()
            val dotSize = dp(9f).toInt()
            val ringSize = dp(20f).toInt()

            val marker = FrameLayout(this).apply {
                isClickable = true
                isFocusable = true
            }

            val ring = View(this).apply {
                setBackgroundResource(com.hackathon.interior.R.drawable.bg_hotspot_ring)
            }
            marker.addView(ring, FrameLayout.LayoutParams(ringSize, ringSize, android.view.Gravity.CENTER))

            val dot = View(this).apply {
                setBackgroundResource(com.hackathon.interior.R.drawable.bg_hotspot_dot)
            }
            marker.addView(dot, FrameLayout.LayoutParams(dotSize, dotSize, android.view.Gravity.CENTER))

            val tag = TextView(this).apply {
                text = "${item.name}  ·  AR로 보기 ›"
                textSize = 12f
                setTextColor(Color.WHITE)
                setPadding(dp(14f).toInt(), dp(10f).toInt(), dp(14f).toInt(), dp(10f).toInt())
                setBackgroundResource(com.hackathon.interior.R.drawable.bg_magazine_hotspot)
                alpha = 0f
                visibility = View.GONE
                setOnClickListener { openObjectInAr(item) }
            }

            binding.hotspotLayer.addView(
                tag,
                FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT),
            )
            binding.hotspotLayer.addView(marker, FrameLayout.LayoutParams(markerSize, markerSize))

            marker.setOnClickListener {
                marker.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                if (openTag === tag && tag.visibility == View.VISIBLE) {
                    closeOpenTag()
                } else {
                    closeOpenTag()
                    showTag(tag)
                }
            }

            marker.post {
                val point = binding.magazineImage.mapCropPoint(item.x, item.y)
                marker.x = (point.x - marker.width / 2f)
                    .coerceIn(0f, (binding.hotspotLayer.width - marker.width).coerceAtLeast(0).toFloat())
                marker.y = (point.y - marker.height / 2f)
                    .coerceIn(0f, (binding.hotspotLayer.height - marker.height).coerceAtLeast(0).toFloat())
                startPulse(ring)

                tag.post {
                    val tagX = (marker.x + marker.width / 2f - tag.width / 2f)
                        .coerceIn(0f, (binding.hotspotLayer.width - tag.width).coerceAtLeast(0).toFloat())
                    val spaceAbove = marker.y
                    val tagY = if (spaceAbove > tag.height + dp(12f)) {
                        marker.y - tag.height - dp(10f)
                    } else {
                        marker.y + marker.height + dp(10f)
                    }
                    tag.x = tagX
                    tag.y = tagY.coerceIn(0f, (binding.hotspotLayer.height - tag.height).coerceAtLeast(0).toFloat())
                }
            }
        }
    }

    private fun showTag(tag: View) {
        tag.visibility = View.VISIBLE
        tag.alpha = 0f
        tag.scaleX = 0.9f
        tag.scaleY = 0.9f
        tag.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(160).start()
        openTag = tag
    }

    private fun closeOpenTag() {
        val tag = openTag ?: return
        tag.animate().alpha(0f).setDuration(120).withEndAction { tag.visibility = View.GONE }.start()
        openTag = null
    }

    private fun startPulse(ring: View) {
        val scaleX = ObjectAnimator.ofFloat(ring, View.SCALE_X, 1f, 1.9f).apply { repeatCount = ObjectAnimator.INFINITE }
        val scaleY = ObjectAnimator.ofFloat(ring, View.SCALE_Y, 1f, 1.9f).apply { repeatCount = ObjectAnimator.INFINITE }
        val alpha = ObjectAnimator.ofFloat(ring, View.ALPHA, 0.75f, 0f).apply { repeatCount = ObjectAnimator.INFINITE }
        val set = AnimatorSet().apply {
            playTogether(scaleX, scaleY, alpha)
            duration = 1500
            interpolator = LinearInterpolator()
            startDelay = (200..900).random().toLong()
        }
        set.start()
        pulseAnimators.add(set)
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

    override fun onDestroy() {
        pulseAnimators.forEach { it.cancel() }
        pulseAnimators.clear()
        super.onDestroy()
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
