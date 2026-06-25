package com.quickdictate.android

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.Toast
import com.quickdictate.android.databinding.OverlayMicBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

/**
 * Foreground service that shows the draggable floating mic button and runs the
 * dictation pipeline when it's tapped.
 *
 * Tap to start recording, tap again to stop → transcribe → clean up → insert at
 * the cursor (via [DictationAccessibilityService]). Drag to reposition.
 */
class OverlayService : Service() {

    private enum class State { IDLE, RECORDING, PROCESSING }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var windowManager: WindowManager
    private lateinit var binding: OverlayMicBinding
    private lateinit var layoutParams: WindowManager.LayoutParams

    private val recorder by lazy { AudioRecorder(this) }
    private var state = State.IDLE

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        startInForeground()
        addOverlayButton()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    // --- Foreground notification -------------------------------------------------

    private fun startInForeground() {
        val channelId = "quickdictate_overlay"
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                getString(R.string.overlay_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            )
            nm.createNotificationChannel(channel)
        }
        val notification: Notification = Notification.Builder(this, channelId)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.overlay_running))
            .setSmallIcon(R.drawable.ic_mic)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    // --- Floating button ---------------------------------------------------------

    private fun addOverlayButton() {
        binding = OverlayMicBinding.inflate(LayoutInflater.from(this))

        val type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            // NOT_FOCUSABLE is critical: it lets the underlying text field keep
            // input focus so we have somewhere to insert the transcript.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 24
            y = 240
        }

        binding.root.setOnTouchListener(makeDragAndTapListener())
        windowManager.addView(binding.root, layoutParams)
        render()
    }

    /** Distinguishes a tap (toggle) from a drag (reposition). */
    private fun makeDragAndTapListener(): View.OnTouchListener {
        var initialX = 0
        var initialY = 0
        var touchX = 0f
        var touchY = 0f
        var moved = false
        val slop = 12

        return View.OnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    touchX = event.rawX
                    touchY = event.rawY
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - touchX).toInt()
                    val dy = (event.rawY - touchY).toInt()
                    if (abs(dx) > slop || abs(dy) > slop) moved = true
                    layoutParams.x = initialX + dx
                    layoutParams.y = initialY + dy
                    windowManager.updateViewLayout(binding.root, layoutParams)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) onButtonTapped()
                    true
                }
                else -> false
            }
        }
    }

    private fun onButtonTapped() {
        when (state) {
            State.IDLE -> startRecording()
            State.RECORDING -> stopAndProcess()
            State.PROCESSING -> { /* busy — ignore */ }
        }
    }

    private fun startRecording() {
        if (!DictationAccessibilityService.isConnected) {
            toast(getString(R.string.enable_accessibility_first))
            return
        }
        if (!recorder.start()) {
            toast(getString(R.string.record_failed))
            return
        }
        state = State.RECORDING
        render()
    }

    private fun stopAndProcess() {
        val audio = recorder.stop()
        if (audio == null) {
            state = State.IDLE
            render()
            toast(getString(R.string.too_short))
            return
        }
        state = State.PROCESSING
        render()

        val config = Config.load(this)
        if (!config.isUsable) {
            state = State.IDLE
            render()
            toast(getString(R.string.no_api_key))
            return
        }

        scope.launch {
            val result = withContext(Dispatchers.IO) {
                val client = TranscriptionClient(config)
                val transcript = client.transcribe(audio) ?: return@withContext null
                val trimmed = transcript.trim()
                if (trimmed.isEmpty() || trimmed.lowercase() in HALLUCINATIONS) {
                    return@withContext ""   // nothing meaningful was said
                }
                client.cleanup(trimmed)
            }
            state = State.IDLE
            render()
            when {
                result == null -> toast(getString(R.string.transcribe_failed))
                result.isEmpty() -> { /* silence / hallucination — say nothing */ }
                else -> DictationAccessibilityService.instance?.insertText(result)
                    ?: toast(getString(R.string.enable_accessibility_first))
            }
        }
    }

    private fun render() {
        val mic: ImageView = binding.micButton
        when (state) {
            State.IDLE -> {
                mic.setImageResource(R.drawable.ic_mic)
                mic.setBackgroundResource(R.drawable.mic_button_bg)
                binding.spinner.visibility = View.GONE
            }
            State.RECORDING -> {
                mic.setImageResource(R.drawable.ic_mic)
                mic.setBackgroundResource(R.drawable.mic_button_bg_recording)
                binding.spinner.visibility = View.GONE
            }
            State.PROCESSING -> {
                binding.spinner.visibility = View.VISIBLE
            }
        }
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        scope.cancel()
        if (this::binding.isInitialized) {
            runCatching { windowManager.removeView(binding.root) }
        }
        if (recorder.isRecording) recorder.stop()
        super.onDestroy()
    }

    companion object {
        private const val NOTIFICATION_ID = 1

        fun start(context: Context) {
            val intent = Intent(context, OverlayService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, OverlayService::class.java))
        }
    }
}
