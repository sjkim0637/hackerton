package com.hackathon.interior

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.hackathon.interior.databinding.ActivitySettingsBinding
import com.hackathon.interior.settings.ServerSettings

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.serverUrlInput.setText(ServerSettings.getBaseUrl(this))
        binding.serverStatusText.text = getString(R.string.settings_usb_hint)

        binding.btnSettingsBack.setOnClickListener { finish() }
        binding.btnSaveServer.setOnClickListener {
            val normalized = ServerSettings.saveBaseUrl(
                this,
                binding.serverUrlInput.text?.toString().orEmpty(),
            )
            binding.serverUrlInput.setText(normalized)
            binding.serverUrlInput.setSelection(normalized.length)
            binding.serverStatusText.text = getString(R.string.settings_saved_value, normalized)
            Toast.makeText(this, R.string.settings_saved, Toast.LENGTH_SHORT).show()
        }
    }
}
