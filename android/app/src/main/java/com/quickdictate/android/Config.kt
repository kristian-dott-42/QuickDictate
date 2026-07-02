package com.quickdictate.android

import android.content.Context

/**
 * Provider configuration, mirroring the desktop app's ~/.dictate/.env keys.
 *
 * Stored in SharedPreferences and re-read before every dictation so settings
 * changes take effect without restarting the floating button. Defaults target
 * Groq (fast + cheap) so the user only needs to paste a single API key.
 */
data class Config(
    val sttUrl: String,
    val sttKey: String,
    val sttModel: String,
    val llmUrl: String,
    val llmKey: String,
    val llmModel: String,
    val whisperPrompt: String,
    val cleanupPrompt: String,
) {
    companion object {
        const val PREFS = "quickdictate"

        // Keys mirror the desktop .env names where they overlap.
        const val K_GROQ_KEY = "GROQ_API_KEY"
        const val K_STT_URL = "STT_URL"
        const val K_STT_KEY = "STT_KEY"
        const val K_STT_MODEL = "STT_MODEL"
        const val K_LLM_URL = "LLM_URL"
        const val K_LLM_KEY = "LLM_KEY"
        const val K_LLM_MODEL = "LLM_MODEL"
        const val K_WHISPER_PROMPT = "WHISPER_PROMPT"
        const val K_CLEANUP_PROMPT = "CLEANUP_PROMPT"

        const val DEFAULT_STT_URL = "https://api.groq.com/openai/v1/audio/transcriptions"
        const val DEFAULT_STT_MODEL = "whisper-large-v3-turbo"
        const val DEFAULT_LLM_URL = "https://api.groq.com/openai/v1/chat/completions"
        // Matches the desktop recommendation. A capable model matters here: small
        // models tend to "answer" dictation instead of just cleaning it up. Groq's
        // production-tier gpt-oss-120b replaces the deprecated llama-3.3-70b-versatile
        // (decommissioned 16 Aug 2026); prefer it over the qwen3.6-27b preview model.
        const val DEFAULT_LLM_MODEL = "openai/gpt-oss-120b"

        fun load(context: Context): Config {
            val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            fun cfg(key: String, def: String): String {
                val v = p.getString(key, null)?.trim()
                return if (v.isNullOrEmpty()) def else v
            }
            val groqKey = cfg(K_GROQ_KEY, "")
            return Config(
                sttUrl = cfg(K_STT_URL, DEFAULT_STT_URL),
                sttKey = cfg(K_STT_KEY, groqKey),
                sttModel = cfg(K_STT_MODEL, DEFAULT_STT_MODEL),
                llmUrl = cfg(K_LLM_URL, DEFAULT_LLM_URL),
                llmKey = cfg(K_LLM_KEY, groqKey),
                llmModel = cfg(K_LLM_MODEL, DEFAULT_LLM_MODEL),
                whisperPrompt = cfg(K_WHISPER_PROMPT, ""),
                cleanupPrompt = cfg(K_CLEANUP_PROMPT, DEFAULT_CLEANUP_PROMPT),
            )
        }
    }

    /** True once at least an STT key is present — the minimum to do anything useful. */
    val isUsable: Boolean get() = sttKey.isNotEmpty()
}
