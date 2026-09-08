package com.hackathon.interior.settings

import android.content.Context

/** 앱 전역 설정의 단일 저장소. 기능 Controller가 화면 입력창에 직접 의존하지 않게 한다. */
class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var serverBaseUrl: String
        get() = normalizeServerUrl(prefs.getString(KEY_SERVER_URL, DEFAULT_SERVER_URL).orEmpty())
        set(value) {
            prefs.edit().putString(KEY_SERVER_URL, normalizeServerUrl(value)).apply()
        }

    fun resetServerBaseUrl() {
        prefs.edit().remove(KEY_SERVER_URL).apply()
    }

    companion object {
        const val DEFAULT_SERVER_URL = "http://192.168.0.2:8000"
        private const val PREFS_NAME = "interior"
        private const val KEY_SERVER_URL = "server_url"

        fun normalizeServerUrl(input: String): String {
            var url = input.trim().ifEmpty { DEFAULT_SERVER_URL }
            if (!url.startsWith("http://") && !url.startsWith("https://")) url = "http://$url"
            return url.trimEnd('/')
        }
    }
}
