package com.hackathon.interior.settings

import android.content.Context

object ServerSettings {
    private const val PREFS_NAME = "interior"
    private const val KEY_SERVER_URL = "server_url"
    const val DEFAULT_SERVER_URL = "http://127.0.0.1:8000"

    fun getBaseUrl(context: Context): String {
        val stored = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_SERVER_URL, DEFAULT_SERVER_URL)
            .orEmpty()
        return normalize(stored)
    }

    fun saveBaseUrl(context: Context, input: String): String {
        val normalized = normalize(input)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SERVER_URL, normalized)
            .apply()
        return normalized
    }

    private fun normalize(input: String): String {
        var url = input.trim().ifEmpty { DEFAULT_SERVER_URL }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "http://$url"
        }
        return url.trimEnd('/')
    }
}
