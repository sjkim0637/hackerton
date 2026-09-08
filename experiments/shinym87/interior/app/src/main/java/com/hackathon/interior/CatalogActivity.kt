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
import android.widget.LinearLayout
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
        // 사진 판 크기가 확정된 뒤에 marker 위치를 다시 계산한다.
        binding.photoPlate.addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            val sizeChanged = (right - left) != (oldRight - oldLeft) || (bottom - top) != (oldBottom - oldTop)
            val page = pages.getOrNull(pageIndex) ?: return@addOnLayoutChangeListener
            if (sizeChanged || binding.hotspotLayer.childCount == 0) {
                binding.hotspotLayer.post { renderHotspots(page) }
            }
        }
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
            binding.photoPlate.post {
                pages.getOrNull(pageIndex)?.let { renderHotspots(it) }
            }
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
        val listener = View.OnTouchListener { _, event -> detector.onTouchEvent(event) }
        // 사진 판 바깥의 종이 여백에서도 넘길 수 있도록 페이지 전체가 Swipe를 받는다.
        binding.catalogRoot.setOnTouchListener(listener)
        binding.magazinePage.setOnTouchListener(listener)
        binding.magazineImage.setOnTouchListener(listener)
        binding.hotspotLayer.setOnTouchListener(listener)
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
        val customPhoto = findCustomPhotoAsset(page.id)
        if (customPhoto != null) {
            // 화보 전용 세로 사진이 준비되면 atlas crop 대신 이 원본을 화면 전체에 꽉 채운다.
            binding.magazineImage.setAssetCrop(
                customPhoto, 0f, 0f, 1f, 1f,
                scrimAlpha = 20, fitCenter = false,
            )
        } else {
            with(page.crop) {
                // 실제 화보 사진이 준비되기 전까지 쓰는 임시 Mock이다. 사진 판이 사진 비율에 맞춰지므로
                // 잘리는 양은 거의 없다. Known Issues에 실제 콘텐츠 공급 필요성을 기록해 두었다.
                binding.magazineImage.setAssetCrop(
                    "interior_asset_BG.png", left, top, right, bottom,
                    scrimAlpha = 12, fitCenter = false,
                )
            }
        }
        binding.issueText.text = page.issue
        binding.pageTitleText.text = page.title
        binding.pageDescriptionText.text = page.description
        binding.pageIndicatorText.text = "%02d / %02d".format(pageIndex + 1, pages.size)
        renderCredits(page)
        binding.photoPlate.post { resizePhotoPlate() }
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

    /** 잡지 뒷단의 제품 크레딧처럼 화보에 등장한 가구와 실제 크기를 적는다. 누르는 곳은 사진 속 점이다. */
    private fun renderCredits(page: MagazinePage) {
        val rows = binding.creditsRows
        rows.removeAllViews()
        page.objects.forEachIndexed { index, item ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(11f).toInt(), 0, dp(11f).toInt())
            }
            row.addView(
                TextView(this).apply {
                    text = "%02d".format(index + 1)
                    textSize = 11f
                    letterSpacing = 0.1f
                    setTextColor(Color.parseColor("#B9AC98"))
                },
                LinearLayout.LayoutParams(dp(34f).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT),
            )
            row.addView(
                TextView(this).apply {
                    text = item.name
                    textSize = 13f
                    setTextColor(Color.parseColor("#443D36"))
                },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
            )
            row.addView(
                TextView(this).apply {
                    text = "%d × %d × %d cm".format(
                        (item.widthM * 100).toInt(),
                        (item.heightM * 100).toInt(),
                        (item.depthM * 100).toInt(),
                    )
                    textSize = 11f
                    setTextColor(Color.parseColor("#8C8377"))
                },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
            rows.addView(row, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            if (index < page.objects.lastIndex) {
                val divider = View(this).apply { setBackgroundColor(Color.parseColor("#E2D8C8")) }
                rows.addView(divider, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1))
            }
        }
    }

    /**
     * 사진 판의 높이를 사진 비율에 맞춘다. 잡지처럼 사진 위아래에 종이 여백이 남고,
     * 사진이 잘리거나 검은 여백이 생기지 않는다.
     */
    private fun resizePhotoPlate() {
        val plate = binding.photoPlate
        val width = plate.width
        if (width <= 0) return
        val aspect = binding.magazineImage.cropAspect().coerceIn(0.6f, 2.0f)
        val available = binding.magazinePage.height - binding.magazinePage.paddingTop -
            binding.magazinePage.paddingBottom
        val maxHeight = (available * 0.52f).toInt().coerceAtLeast(dp(200f).toInt())
        val target = (width / aspect).toInt().coerceAtMost(maxHeight)
        if (plate.layoutParams.height != target) {
            plate.layoutParams = plate.layoutParams.apply { height = target }
            plate.requestLayout()
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

    /**
     * `assets/magazine/<page.id>.jpg|png`가 있으면 그 세로 사진을 그대로 쓴다.
     * ImageGen이나 sjkim0637이 실제 화보 사진을 넣어 주면 코드 변경 없이 바로 반영된다.
     */
    private fun findCustomPhotoAsset(pageId: String): String? {
        return listOf("jpg", "png", "webp").map { "magazine/$pageId.$it" }
            .firstOrNull { name ->
                runCatching { assets.open(name).use { } }.isSuccess
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
