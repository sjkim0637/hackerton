package com.hackathon.interior

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
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
     * 화보 위에는 텍스트 버튼을 바로 노출하지 않는다. 은은하게 숨쉬는 점 마커를 누르면
     * 해당 가구를 AR에 배치하거나 구매하는 두 가지 다음 행동을 고르게 한다.
     */
    private fun renderHotspots(page: MagazinePage) {
        binding.hotspotLayer.removeAllViews()
        pulseAnimators.forEach { it.cancel() }
        pulseAnimators.clear()

        page.objects.forEach { item ->
            val markerSize = dp(48f).toInt()
            val dotSize = dp(13f).toInt()
            val ringSize = dp(26f).toInt()

            val marker = FrameLayout(this).apply {
                isClickable = true
                isFocusable = true
                contentDescription = "${item.name}, 가구 정보 보기"
            }

            val ring = View(this).apply {
                setBackgroundResource(com.hackathon.interior.R.drawable.bg_hotspot_ring)
            }
            marker.addView(ring, FrameLayout.LayoutParams(ringSize, ringSize, android.view.Gravity.CENTER))

            val dot = View(this).apply {
                setBackgroundResource(com.hackathon.interior.R.drawable.bg_hotspot_dot)
            }
            marker.addView(dot, FrameLayout.LayoutParams(dotSize, dotSize, android.view.Gravity.CENTER))

            binding.hotspotLayer.addView(marker, FrameLayout.LayoutParams(markerSize, markerSize))
            marker.setOnClickListener {
                marker.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                showObjectActions(item)
            }

            marker.post {
                val point = binding.magazineImage.mapCropPoint(item.x, item.y)
                marker.x = (point.x - marker.width / 2f)
                    .coerceIn(0f, (binding.hotspotLayer.width - marker.width).coerceAtLeast(0).toFloat())
                marker.y = (point.y - marker.height / 2f)
                    .coerceIn(0f, (binding.hotspotLayer.height - marker.height).coerceAtLeast(0).toFloat())
                startPulse(ring)

            }
        }
    }

    private fun showObjectActions(item: MagazineObject) {
        AlertDialog.Builder(this)
            .setTitle(item.name)
            .setMessage("내 공간에 먼저 배치해 보거나, 바로 구매할 수 있어요.")
            .setNegativeButton("AR로 배치") { _, _ -> openObjectInAr(item) }
            .setPositiveButton("구매하기") { _, _ -> showPurchaseDialog(item) }
            .show()
    }

    private fun showPurchaseDialog(item: MagazineObject) {
        AlertDialog.Builder(this)
            .setTitle("주문 확인")
            .setMessage("${item.name}\n${mockPrice(item)}\n\n배송지와 결제는 데모에서 처리되지 않습니다.")
            .setNegativeButton("취소", null)
            .setPositiveButton("주문하기") { _, _ ->
                Toast.makeText(this, "${item.name} 주문이 접수되었습니다. (Mock)", Toast.LENGTH_LONG).show()
            }
            .show()
    }

    private fun mockPrice(item: MagazineObject): String = when (item.category) {
        "sofa" -> "₩ 1,290,000"
        "table" -> "₩ 349,000"
        "chair" -> "₩ 189,000"
        "tv" -> "₩ 1,890,000"
        "shelf" -> "₩ 429,000"
        else -> "₩ 129,000"
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
