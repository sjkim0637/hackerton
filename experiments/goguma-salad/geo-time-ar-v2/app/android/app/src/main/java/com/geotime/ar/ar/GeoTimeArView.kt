package com.geotime.ar.ar

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.os.SystemClock
import android.view.Surface
import com.geotime.ar.interaction.HeadPose
import com.geotime.ar.spatial.SpatialCandidate
import com.geotime.ar.spatial.SpatialVisibilitySelector
import com.geotime.ar.spatial.Vector3
import com.google.ar.core.Anchor
import com.google.ar.core.Pose
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.asin
import kotlin.math.atan2
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class GeoTimeArView(context: Context) : GLSurfaceView(context), GLSurfaceView.Renderer {
    private val backgroundRenderer = CameraBackgroundRenderer()
    private val markerRenderer = AnchorMarkerRenderer()
    private val anchors = ConcurrentHashMap<String, Anchor>()
    @Volatile private var arSession: Session? = null
    @Volatile private var candidates: List<SpatialCandidate> = emptyList()
    @Volatile private var contentAlpha = 1f
    @Volatile private var focusedCandidateId: String? = null
    @Volatile var onTrackingUpdate: ((String) -> Unit)? = null
    @Volatile var onSpatialFrame: ((String?, HeadPose, Long) -> Unit)? = null
    private var lastSpatialFrameAtMs = 0L
    private var viewportWidth = 1
    private var viewportHeight = 1

    init {
        preserveEGLContextOnPause = true
        setEGLContextClientVersion(2)
        setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        setRenderer(this)
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    fun attachSession(session: Session) = queueEvent {
        arSession = session
        if (backgroundRenderer.textureId != 0) {
            session.setCameraTextureName(backgroundRenderer.textureId)
        }
        updateDisplayGeometry(session)
    }

    fun updateCandidates(next: List<SpatialCandidate>) = queueEvent {
        val nextIds = next.mapTo(hashSetOf()) { it.id }
        anchors.entries.removeIf { (id, anchor) ->
            if (id !in nextIds) anchor.detach()
            id !in nextIds
        }
        candidates = next
    }

    fun updateContentAlpha(alpha: Float) {
        contentAlpha = alpha.coerceIn(0f, 1f)
    }

    fun focusedCandidateId(): String? = focusedCandidateId

    fun detachAllAnchors() = queueEvent {
        anchors.values.forEach(Anchor::detach)
        anchors.clear()
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.03f, 0.05f, 0.08f, 1f)
        backgroundRenderer.createOnGlThread()
        markerRenderer.createOnGlThread()
        arSession?.setCameraTextureName(backgroundRenderer.textureId)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        viewportWidth = width
        viewportHeight = height
        GLES20.glViewport(0, 0, width, height)
        arSession?.let(::updateDisplayGeometry)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        val session = arSession ?: return
        try {
            val frame = session.update()
            backgroundRenderer.draw(frame)
            val camera = frame.camera
            if (camera.trackingState != TrackingState.TRACKING) {
                focusedCandidateId = null
                onTrackingUpdate?.invoke("AR 추적 대기 중")
                return
            }
            val pose = camera.pose
            val translation = pose.translation
            val zAxis = pose.zAxis
            val forward = Vector3(-zAxis[0], -zAxis[1], -zAxis[2])
            val visible = SpatialVisibilitySelector.select(
                cameraPosition = Vector3(translation[0], translation[1], translation[2]),
                cameraForward = forward,
                candidates = candidates,
            )
            focusedCandidateId = visible.firstOrNull { it.angleDegrees <= 10f }?.candidate?.id
            val nowMs = SystemClock.elapsedRealtime()
            if (nowMs - lastSpatialFrameAtMs >= 50L) {
                lastSpatialFrameAtMs = nowMs
                onSpatialFrame?.invoke(
                    focusedCandidateId,
                    HeadPose(
                        yawDegrees = Math.toDegrees(
                            atan2(forward.x.toDouble(), -forward.z.toDouble())
                        ).toFloat(),
                        pitchDegrees = Math.toDegrees(
                            asin(forward.y.coerceIn(-1f, 1f).toDouble())
                        ).toFloat(),
                    ),
                    nowMs,
                )
            }
            visible.forEach { item ->
                anchors.getOrPut(item.candidate.id) {
                    val position = item.candidate.position
                    session.createAnchor(Pose.makeTranslation(position.x, position.y, position.z))
                }
            }

            val view = FloatArray(16)
            val projection = FloatArray(16)
            camera.getViewMatrix(view, 0)
            camera.getProjectionMatrix(projection, 0, 0.1f, 100f)
            visible.forEach { item ->
                anchors[item.candidate.id]?.let {
                    markerRenderer.draw(it, item.candidate.title, view, projection, contentAlpha)
                }
            }
            val nearest = visible.minByOrNull { it.distanceM }
            val detail = nearest?.let { " · ${it.candidate.title} ${"%.1f".format(it.distanceM)}m" }.orEmpty()
            onTrackingUpdate?.invoke("6DoF 추적 · 표시 ${visible.size}/${candidates.size}$detail")
        } catch (error: Exception) {
            focusedCandidateId = null
            onTrackingUpdate?.invoke("AR 오류: ${error.message}")
        }
    }

    private fun updateDisplayGeometry(session: Session) {
        val rotation = display?.rotation ?: Surface.ROTATION_0
        session.setDisplayGeometry(rotation, viewportWidth, viewportHeight)
    }
}
