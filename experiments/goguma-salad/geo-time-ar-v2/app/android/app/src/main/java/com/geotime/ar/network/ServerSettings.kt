package com.geotime.ar.network

import android.content.SharedPreferences
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.SocketTimeoutException
import java.net.URI
import java.net.UnknownHostException
import java.net.URL
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

enum class ServerProfile(
    val label: String,
    val description: String,
    val defaultApiUrl: String,
    val defaultMediaUrl: String,
    val usesNetwork: Boolean,
) {
    DEMO(
        label = "Demo",
        description = "서버 없이 앱에 포함된 Local Moment를 사용합니다.",
        defaultApiUrl = "http://127.0.0.1:8000",
        defaultMediaUrl = "http://127.0.0.1:9000",
        usesNetwork = false,
    ),
    USB(
        label = "USB",
        description = "개발 PC의 Docker 서비스에 USB Reverse로 연결합니다.",
        defaultApiUrl = "http://127.0.0.1:8000",
        defaultMediaUrl = "http://127.0.0.1:9000",
        usesNetwork = true,
    ),
    PRODUCTION(
        label = "운영",
        description = "인터넷에서 접근 가능한 HTTPS API와 Media를 사용합니다.",
        defaultApiUrl = "https://api.example.com",
        defaultMediaUrl = "https://media.example.com",
        usesNetwork = true,
    ),
}

data class ServerSettings(
    val profile: ServerProfile,
    val apiBaseUrl: String,
    val mediaBaseUrl: String,
) {
    fun normalized(): ServerSettings = copy(
        apiBaseUrl = normalizeBaseUrl(apiBaseUrl),
        mediaBaseUrl = normalizeBaseUrl(mediaBaseUrl),
    )

    fun validationError(): String? {
        if (!profile.usesNetwork) return null
        return validateHttpUrl(apiBaseUrl, "API") ?: validateHttpUrl(mediaBaseUrl, "Media")
    }

    fun resolveMediaUrl(publicUrl: String): String {
        if (!profile.usesNetwork) return publicUrl
        val source = runCatching { URI(publicUrl) }.getOrNull() ?: return publicUrl
        val pathAndQuery = buildString {
            append(source.rawPath.orEmpty().ifBlank { "/" })
            source.rawQuery?.let { append('?').append(it) }
        }
        return normalizeBaseUrl(mediaBaseUrl) + pathAndQuery
    }

    companion object {
        fun defaults(profile: ServerProfile) = ServerSettings(
            profile,
            profile.defaultApiUrl,
            profile.defaultMediaUrl,
        )

        fun normalizeBaseUrl(value: String) = value.trim().trimEnd('/')

        fun validateHttpUrl(value: String, label: String): String? {
            val uri = runCatching { URI(normalizeBaseUrl(value)) }.getOrNull()
                ?: return "$label 주소 형식이 올바르지 않습니다."
            if (uri.scheme !in setOf("http", "https") || uri.host.isNullOrBlank()) {
                return "$label 주소는 http:// 또는 https://로 시작해야 합니다."
            }
            return null
        }
    }
}

class ServerSettingsStore(private val preferences: SharedPreferences) {
    fun selectedProfile(): ServerProfile = runCatching {
        ServerProfile.valueOf(preferences.getString(KEY_PROFILE, null) ?: ServerProfile.USB.name)
    }.getOrDefault(ServerProfile.USB)

    fun load(profile: ServerProfile = selectedProfile()): ServerSettings = ServerSettings(
        profile = profile,
        apiBaseUrl = preferences.getString(apiKey(profile), profile.defaultApiUrl) ?: profile.defaultApiUrl,
        mediaBaseUrl = preferences.getString(mediaKey(profile), profile.defaultMediaUrl) ?: profile.defaultMediaUrl,
    ).normalized()

    fun save(settings: ServerSettings) {
        val normalized = settings.normalized()
        preferences.edit()
            .putString(KEY_PROFILE, normalized.profile.name)
            .putString(apiKey(normalized.profile), normalized.apiBaseUrl)
            .putString(mediaKey(normalized.profile), normalized.mediaBaseUrl)
            .apply()
    }

    private fun apiKey(profile: ServerProfile) = "server_api_${profile.name.lowercase()}"
    private fun mediaKey(profile: ServerProfile) = "server_media_${profile.name.lowercase()}"

    companion object {
        private const val KEY_PROFILE = "server_profile"
    }
}

data class EndpointTestResult(
    val success: Boolean,
    val message: String,
)

data class ServerConnectionResult(
    val api: EndpointTestResult,
    val media: EndpointTestResult,
) {
    val success: Boolean get() = api.success && media.success
}

class ServerConnectionTester(
    private val executor: ExecutorService = Executors.newSingleThreadExecutor(),
) {
    fun test(settings: ServerSettings, onResult: (ServerConnectionResult) -> Unit) {
        executor.execute {
            if (!settings.profile.usesNetwork) {
                val local = EndpointTestResult(true, "Local Demo · 네트워크 불필요")
                onResult(ServerConnectionResult(local, local))
                return@execute
            }
            val validationError = settings.validationError()
            if (validationError != null) {
                val invalid = EndpointTestResult(false, validationError)
                onResult(ServerConnectionResult(invalid, invalid))
                return@execute
            }
            onResult(
                ServerConnectionResult(
                    api = testApi(settings.apiBaseUrl),
                    media = testMedia(settings.mediaBaseUrl),
                )
            )
        }
    }

    fun close() = executor.shutdownNow()

    private fun testApi(baseUrl: String): EndpointTestResult = request(
        url = ServerSettings.normalizeBaseUrl(baseUrl) + "/health",
        acceptedCodes = 200..299,
    ) { body ->
        val health = JSONObject(body)
        val status = health.optString("status", "응답")
        val environment = health.optString("environment").takeIf(String::isNotBlank)
        val version = health.optString("version").takeIf(String::isNotBlank)
        "API 연결됨 · $status${environment?.let { " · $it" }.orEmpty()}" +
            version?.let { " · Backend $it" }.orEmpty()
    }

    private fun testMedia(baseUrl: String): EndpointTestResult = request(
        url = ServerSettings.normalizeBaseUrl(baseUrl) + "/",
        acceptedCodes = 200..499,
    ) { "Media 주소 도달 가능" }

    private fun request(
        url: String,
        acceptedCodes: IntRange,
        successMessage: (String) -> String,
    ): EndpointTestResult {
        var connection: HttpURLConnection? = null
        return try {
            connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 4_000
            connection.readTimeout = 5_000
            connection.setRequestProperty("Accept", "application/json")
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code in acceptedCodes) {
                EndpointTestResult(true, successMessage(body))
            } else {
                EndpointTestResult(false, "HTTP $code · Server 응답 오류")
            }
        } catch (_: UnknownHostException) {
            EndpointTestResult(false, "DNS 실패 · Host 이름을 확인하세요")
        } catch (_: SocketTimeoutException) {
            EndpointTestResult(false, "Timeout · Server와 Network를 확인하세요")
        } catch (_: MalformedURLException) {
            EndpointTestResult(false, "주소 형식이 올바르지 않습니다")
        } catch (error: SecurityException) {
            EndpointTestResult(false, "연결 차단 · ${error.message ?: "보안 설정을 확인하세요"}")
        } catch (error: Exception) {
            EndpointTestResult(false, "${error.javaClass.simpleName} · ${error.message ?: "연결 실패"}")
        } finally {
            connection?.disconnect()
        }
    }
}
