package com.hackathon.interior

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.hackathon.interior.databinding.ActivityGuideBinding

class GuideActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityGuideBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.btnGuideBack.setOnClickListener { finish() }
    }
}
