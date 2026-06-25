package com.quickdictate.android

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.quickdictate.android.databinding.ActivityMainBinding

/**
 * Settings + one-time setup. Because services can't request runtime permissions
 * themselves, the user grants everything here, then starts the floating button.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val micPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* status refreshed in onResume */ refreshStatus() }

    private val notifPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { refreshStatus() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        loadFields()

        binding.saveButton.setOnClickListener { saveFields(); toast(getString(R.string.saved)) }

        binding.grantMic.setOnClickListener {
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        binding.grantOverlay.setOnClickListener {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName"),
                ),
            )
        }
        binding.grantAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            toast(getString(R.string.enable_accessibility_hint))
        }

        binding.startButton.setOnClickListener { startFloatingButton() }
        binding.stopButton.setOnClickListener { OverlayService.stop(this); refreshStatus() }
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    // --- Settings persistence ----------------------------------------------------

    private fun loadFields() {
        val p = getSharedPreferences(Config.PREFS, Context.MODE_PRIVATE)
        binding.groqKey.setText(p.getString(Config.K_GROQ_KEY, ""))
        binding.sttUrl.setText(p.getString(Config.K_STT_URL, Config.DEFAULT_STT_URL))
        binding.sttModel.setText(p.getString(Config.K_STT_MODEL, Config.DEFAULT_STT_MODEL))
        binding.llmUrl.setText(p.getString(Config.K_LLM_URL, Config.DEFAULT_LLM_URL))
        binding.llmModel.setText(p.getString(Config.K_LLM_MODEL, Config.DEFAULT_LLM_MODEL))
        binding.whisperPrompt.setText(p.getString(Config.K_WHISPER_PROMPT, ""))
    }

    private fun saveFields() {
        getSharedPreferences(Config.PREFS, Context.MODE_PRIVATE).edit().apply {
            putString(Config.K_GROQ_KEY, binding.groqKey.text.toString().trim())
            putString(Config.K_STT_URL, binding.sttUrl.text.toString().trim())
            putString(Config.K_STT_MODEL, binding.sttModel.text.toString().trim())
            putString(Config.K_LLM_URL, binding.llmUrl.text.toString().trim())
            putString(Config.K_LLM_MODEL, binding.llmModel.text.toString().trim())
            putString(Config.K_WHISPER_PROMPT, binding.whisperPrompt.text.toString().trim())
            apply()
        }
    }

    // --- Launch ------------------------------------------------------------------

    private fun startFloatingButton() {
        saveFields()
        if (!hasMic()) { toast(getString(R.string.need_mic)); return }
        if (!hasOverlay()) { toast(getString(R.string.need_overlay)); return }
        if (!DictationAccessibilityService.isConnected) { toast(getString(R.string.need_accessibility)); return }
        OverlayService.start(this)
        toast(getString(R.string.floating_started))
        refreshStatus()
    }

    private fun hasMic() = ContextCompatChecks.granted(this, Manifest.permission.RECORD_AUDIO)
    private fun hasOverlay() = Settings.canDrawOverlays(this)

    private fun refreshStatus() {
        fun mark(ok: Boolean) = if (ok) "✓" else "✗"
        binding.statusText.text = getString(
            R.string.status_template,
            mark(hasMic()),
            mark(hasOverlay()),
            mark(DictationAccessibilityService.isConnected),
        )
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}

/** Tiny indirection so the permission check reads clearly at the call site. */
private object ContextCompatChecks {
    fun granted(context: Context, permission: String): Boolean =
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
}
