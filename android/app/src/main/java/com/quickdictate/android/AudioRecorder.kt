package com.quickdictate.android

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File

/**
 * Records 16 kHz mono AAC audio to a file in the app cache, matching the desktop
 * app's capture settings. Groq's Whisper endpoint accepts m4a directly.
 */
class AudioRecorder(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null

    fun start(): Boolean {
        val file = File(context.cacheDir, "dictation.m4a")
        if (file.exists()) file.delete()

        val rec = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        return try {
            rec.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(16_000)
                setAudioEncodingBitRate(64_000)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            recorder = rec
            outputFile = file
            true
        } catch (e: Exception) {
            Log.e("QuickDictate", "recorder start failed", e)
            runCatching { rec.release() }
            recorder = null
            outputFile = null
            false
        }
    }

    /** Stop recording and return the captured file, or null if nothing usable was recorded. */
    fun stop(): File? {
        val rec = recorder ?: return null
        recorder = null
        return try {
            rec.stop()
            rec.release()
            outputFile?.takeIf { it.exists() && it.length() > 0 }
        } catch (e: Exception) {
            // stop() throws if it's called too soon (e.g. < ~0.4s of audio).
            Log.w("QuickDictate", "recorder stop failed (too short?)", e)
            runCatching { rec.release() }
            null
        } finally {
            outputFile = null
        }
    }

    val isRecording: Boolean get() = recorder != null
}
