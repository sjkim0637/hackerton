package com.hackathon.interior.remove

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.DataOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * `experiments/shinym87/interior/server/` 의 FastAPI 서버와 통신한다.
 *
 * PHASE 1 목표(흐름 연결)에 맞춰 최소 구현이다. OkHttp 같은 의존성 없이
 * `HttpURLConnection` + `org.json` 만 쓴다. 서버 주소는 우선 하드코딩한다.
 */
class InteriorApiClient(private val baseUrl: String = DEFAULT_BASE_URL) {

    companion object {
        // 실기기 네트워크 연결은 나중에. 지금은 로컬 서버 고정.
        const val DEFAULT_BASE_URL = "http://localhost:8000"
    }

    data class JobStatus(
        val jobId: String,
        val status: String,           // queued | running | done | failed
        val resultImageUrl: String?,  // 예: /scenes/{id}/results/{job}.jpg
        val changedRect: FloatArray?, // [x, y, w, h] 정규화, 없으면 null
        val error: String?,
    )

    suspend fun createScene(): String = withContext(Dispatchers.IO) {
        val conn = open("/scenes", "POST")
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json")
        conn.outputStream.use { it.write("""{"device":"android"}""".toByteArray(Charsets.UTF_8)) }
        JSONObject(readBody(conn)).getString("scene_id")
    }

    /** multipart/form-data 로 이미지(part `image`)와 메타 JSON 문자열(part `meta`)을 올린다. */
    suspend fun uploadKeyframe(
        sceneId: String,
        jpeg: ByteArray,
        metaJson: String,
    ): String = withContext(Dispatchers.IO) {
        val boundary = "----interior${System.currentTimeMillis()}"
        val conn = open("/scenes/$sceneId/keyframes", "POST")
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        DataOutputStream(conn.outputStream).use { out ->
            out.writeBytes("--$boundary\r\n")
            out.writeBytes(
                "Content-Disposition: form-data; name=\"image\"; filename=\"keyframe.jpg\"\r\n",
            )
            out.writeBytes("Content-Type: image/jpeg\r\n\r\n")
            out.write(jpeg)
            out.writeBytes("\r\n")

            out.writeBytes("--$boundary\r\n")
            out.writeBytes("Content-Disposition: form-data; name=\"meta\"\r\n")
            out.writeBytes("Content-Type: application/json; charset=utf-8\r\n\r\n")
            out.write(metaJson.toByteArray(Charsets.UTF_8))
            out.writeBytes("\r\n--$boundary--\r\n")
        }
        JSONObject(readBody(conn)).getString("keyframe_id")
    }

    suspend fun requestRemoveObject(
        sceneId: String,
        keyframeId: String,
        bbox: FloatArray,      // [x, y, w, h] 정규화
        objectType: String,
    ): String = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("keyframe_id", keyframeId)
            .put("object_type", objectType)
            .put(
                "target",
                JSONObject()
                    .put("type", "bbox")
                    .put("rect", JSONArray(bbox.map { it.toDouble() })),
            )
        val conn = open("/scenes/$sceneId/remove-object", "POST")
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json")
        conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        JSONObject(readBody(conn)).getString("job_id")
    }

    suspend fun getJob(sceneId: String, jobId: String): JobStatus = withContext(Dispatchers.IO) {
        val conn = open("/scenes/$sceneId/jobs/$jobId", "GET")
        val json = JSONObject(readBody(conn))
        val region = json.optJSONObject("changed_region")
        val rect = region?.optJSONArray("rect")?.let { arr ->
            FloatArray(arr.length()) { i -> arr.getDouble(i).toFloat() }
        }
        JobStatus(
            jobId = json.optString("job_id", jobId),
            status = json.optString("status", "unknown"),
            resultImageUrl = json.optString("result_image_url").ifEmpty { null },
            changedRect = rect,
            error = json.optString("error").ifEmpty { null },
        )
    }

    suspend fun downloadBytes(pathOrUrl: String): ByteArray = withContext(Dispatchers.IO) {
        val conn = open(pathOrUrl, "GET")
        val code = conn.responseCode
        if (code !in 200..299) error("HTTP $code: ${conn.errorStream?.readBytes()?.decodeToString()}")
        conn.inputStream.use { it.readBytes() }
    }

    // ------------------------------------------------------------------ 내부

    private fun open(pathOrUrl: String, method: String): HttpURLConnection {
        val full = if (pathOrUrl.startsWith("http")) pathOrUrl else baseUrl + pathOrUrl
        return (URL(full).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 10_000
            readTimeout = 30_000
            useCaches = false
        }
    }

    private fun readBody(conn: HttpURLConnection): String {
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (code !in 200..299) error("HTTP $code: $text")
        return text
    }
}
