package com.geotime.ar.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerSettingsTest {
    @Test
    fun `production profile uses deployed public endpoints`() {
        val settings = ServerSettings.defaults(ServerProfile.PRODUCTION)

        assertEquals("https://geo-time-ar-v2.vercel.app", settings.apiBaseUrl)
        assertEquals(
            "https://rsmdmqhmerjiaqoyefhs.supabase.co/storage/v1/object",
            settings.mediaBaseUrl,
        )
        assertNull(settings.validationError())
    }

    @Test
    fun `base URLs are trimmed and normalized`() {
        val settings = ServerSettings(ServerProfile.USB, " http://127.0.0.1:8000/ ", "http://127.0.0.1:9000/")

        assertEquals("http://127.0.0.1:8000", settings.normalized().apiBaseUrl)
        assertEquals("http://127.0.0.1:9000", settings.normalized().mediaBaseUrl)
    }

    @Test
    fun `configured media origin replaces backend public origin`() {
        val settings = ServerSettings(ServerProfile.USB, "http://127.0.0.1:8000", "http://127.0.0.1:9000")

        assertEquals(
            "http://127.0.0.1:9000/geo-time-assets/demo/video.mp4?version=2",
            settings.resolveMediaUrl("http://localhost:9000/geo-time-assets/demo/video.mp4?version=2"),
        )
    }

    @Test
    fun `network profiles require an HTTP URL with host`() {
        val settings = ServerSettings(ServerProfile.PRODUCTION, "api.example.com", "https://media.example.com")

        assertTrue(settings.validationError()!!.contains("API"))
    }

    @Test
    fun `local demo does not require valid network endpoints`() {
        val settings = ServerSettings(ServerProfile.DEMO, "", "")

        assertNull(settings.validationError())
    }
}
