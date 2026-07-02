package com.quickdictate.android

import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Speech-to-text + LLM cleanup, ported from the desktop app's callSTT/callLLM.
 * Both calls are synchronous and meant to be invoked off the main thread.
 */
class TranscriptionClient(private val config: Config) {

    private val http = OkHttpClient.Builder()
        .callTimeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    /** Transcribe the recorded audio file. Returns the trimmed transcript, or null on failure. */
    fun transcribe(audio: File): String? {
        val mediaType = "audio/m4a".toMediaType()
        val builder = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("model", config.sttModel)
            .addFormDataPart("language", "en")
            .addFormDataPart("response_format", "json")
        if (config.whisperPrompt.isNotEmpty()) {
            builder.addFormDataPart("prompt", config.whisperPrompt)
        }
        builder.addFormDataPart("file", "audio.m4a", audio.asRequestBody(mediaType))

        val req = Request.Builder()
            .url(config.sttUrl)
            .header("Authorization", "Bearer ${config.sttKey}")
            .post(builder.build())
            .build()

        return try {
            http.newCall(req).execute().use { resp ->
                val body = resp.body?.string()
                if (!resp.isSuccessful) {
                    Log.e(TAG, "stt http error: status ${resp.code} body=$body")
                    return null
                }
                if (body == null) return null
                val json = JSONObject(body)
                when {
                    json.has("text") -> json.getString("text").trim()
                    json.has("error") -> {
                        Log.e(TAG, "stt api error: ${json.get("error")}")
                        null
                    }
                    else -> null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "stt network error", e)
            null
        }
    }

    /** Clean up a raw transcript. Falls back to the raw text if cleanup fails. */
    fun cleanup(raw: String): String {
        val payload = JSONObject().apply {
            put("model", config.llmModel)
            put("messages", org.json.JSONArray().apply {
                put(JSONObject().put("role", "system").put("content", config.cleanupPrompt))
                put(JSONObject().put("role", "user").put("content", "<dictation>\n$raw\n</dictation>"))
            })
            put("max_tokens", 4096)
            put("temperature", 0.1)
        }
        val req = Request.Builder()
            .url(config.llmUrl)
            .header("Authorization", "Bearer ${config.llmKey}")
            .header("Content-Type", "application/json")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val cleaned = try {
            http.newCall(req).execute().use { resp ->
                val body = resp.body?.string()
                if (!resp.isSuccessful) {
                    Log.e(TAG, "llm http error: status ${resp.code} body=$body")
                    return@use raw
                }
                if (body == null) return@use raw
                val json = JSONObject(body)
                val choices = json.optJSONArray("choices")
                val content = choices?.optJSONObject(0)
                    ?.optJSONObject("message")
                    ?.optString("content")
                if (content.isNullOrEmpty()) {
                    if (json.has("error")) Log.e(TAG, "llm api error: ${json.get("error")}")
                    raw
                } else {
                    content.trim()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "llm network error", e)
            raw
        }

        // Strip any <dictation> tags the model may have echoed back.
        return cleaned
            .replace("<dictation>", "")
            .replace("</dictation>", "")
            .trim()
    }

    companion object {
        private const val TAG = "QuickDictate"
        private const val REQUEST_TIMEOUT_SECONDS = 30L
    }
}
