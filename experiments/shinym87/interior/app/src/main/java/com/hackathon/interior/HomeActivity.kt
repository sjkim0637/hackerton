package com.hackathon.interior

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.hackathon.interior.databinding.ActivityHomeBinding
import com.hackathon.interior.settings.ServerSettings

class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnOpenAr.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }
        binding.btnOpenGuide.setOnClickListener {
            startActivity(Intent(this, GuideActivity::class.java))
        }
        binding.btnOpenSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        binding.serverSummaryText.text = getString(
            R.string.home_server_summary,
            ServerSettings.getBaseUrl(this),
        )
    }
}
