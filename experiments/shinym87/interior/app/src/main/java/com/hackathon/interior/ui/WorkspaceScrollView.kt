package com.hackathon.interior.ui

import android.content.Context
import android.util.AttributeSet
import android.widget.ScrollView

/** 도구가 늘어나거나 글꼴이 커져도 카메라 시야를 남긴다. */
class WorkspaceScrollView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null,
) : ScrollView(context, attrs) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val limit = (MeasureSpec.getSize(heightMeasureSpec) * 0.48f).toInt()
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(limit, MeasureSpec.AT_MOST))
    }
}
