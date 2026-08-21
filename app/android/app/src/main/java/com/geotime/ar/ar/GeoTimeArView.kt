package com.geotime.ar.ar

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.view.Surface
import com.geotime.ar.spatial.SpatialCandidate
import com.geotime.ar.spatial.SpatialVisibilitySelector
import com.geotime.ar.spatial.Vector3
import com.google.ar.core.Anchor
import com.google.ar.core.Pose
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import java.util.concurrent.ConcurrentHashMap
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class GeoTimeArView(context: Context) : GLSurfaceView(context), GLSurfaceView.Renderer {
    private val backgroundRenderer = CameraBackgroundRenderer()
    private val markerRenderer = AnchorMarkerRenderer()
    private val anchors = ConcurrentHashMap<String, Anchor>()
    @Volatile private var arSession: Session? = null
    @Volatile private var candidates: List<SpatialCandidate> = emptyList()
    @Volatile var onTrackingUpdate: ((String) -> Unit)? = null
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
                onTrackingUpdate?.invoke("AR 추적 대기 중")
                return
            }
            val pose = camera.pose
            val translation = pose.translation
            val zAxis = pose.zAxis
            val visible = SpatialVisibilitySelector.select(
                cameraPosition = Vector3(translation[0], translation[1], translation[2]),
                cameraForward = Vector3(-zAxis[0], -zAxis[1], -zAxis[2]),
                candidates = candidates,
            )
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
            visible.forEach { item -> anchors[item.candidate.id]?.let { markerRenderer.draw(it, view, projection) } }
            onTrackingUpdate?.invoke("6DoF 추적 · 표시 ${visible.size}/${candidates.size}")
        } catch (error: Exception) {
            onTrackingUpdate?.invoke("AR 오류: ${error.message}")
        }
    }

    private fun updateDisplayGeometry(session: Session) {
        val rotation = display?.rotation ?: Surface.ROTATION_0
        session.setDisplayGeometry(rotation, viewportWidth, viewportHeight)
    }
}
