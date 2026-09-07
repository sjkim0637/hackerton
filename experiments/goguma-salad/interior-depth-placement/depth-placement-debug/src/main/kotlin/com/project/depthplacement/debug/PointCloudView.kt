package com.project.depthplacement.debug

import android.content.Context
import android.opengl.GLES30
import android.opengl.GLES11Ext
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import com.project.depthplacement.PlacementResult
import com.project.depthplacement.PlacementObjectSize
import com.project.depthplacement.PointCloudSnapshot
import com.project.depthplacement.Vec3
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.cos
import kotlin.math.sin

enum class PointDisplayMode { RAW, FILTERED, PLACEMENT_ROI, SURFACE, OBSTACLE }

data class DebugRenderConfig(
    val pointSize: Float = 6f,
    val nearPlane: Float = 0.05f,
    val farPlane: Float = 20f,
    val showNormal: Boolean = true,
    val showPlacementFootprint: Boolean = true,
)

class PointCloudView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : GLSurfaceView(context, attrs) {
    private val cloudRenderer = CloudRenderer()
    private val scaler = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            cloudRenderer.zoom = (cloudRenderer.zoom / detector.scaleFactor).coerceIn(0.25f, 12f)
            return true
        }
    })
    private var lastX = 0f
    private var lastY = 0f
    private var downX = 0f
    private var downY = 0f
    private var frozen = false
    var onPlacementTap: ((normalizedX: Float, normalizedY: Float) -> Unit)? = null
    var onCameraTextureReady: ((textureId: Int) -> Unit)?
        get() = cloudRenderer.onCameraTextureReady
        set(value) { cloudRenderer.onCameraTextureReady = value }
    var onRenderFrame: (() -> Unit)?
        get() = cloudRenderer.onRenderFrame
        set(value) { cloudRenderer.onRenderFrame = value }
    val renderFps: Double get() = cloudRenderer.renderFps

    init {
        setEGLContextClientVersion(3)
        setRenderer(cloudRenderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    fun submit(snapshot: PointCloudSnapshot?) { if (!frozen) cloudRenderer.submit(snapshot) }
    fun submitPlacement(result: PlacementResult?, objectSize: PlacementObjectSize? = null) { cloudRenderer.placement = result; cloudRenderer.placementSize = objectSize }
    fun setFrozen(value: Boolean) { frozen = value }
    fun isFrozen(): Boolean = frozen
    fun resetView() { cloudRenderer.resetView() }
    fun updateRenderConfig(config: DebugRenderConfig) { cloudRenderer.config = config }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaler.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { lastX = event.x; lastY = event.y; downX = event.x; downY = event.y }
            MotionEvent.ACTION_MOVE -> if (!scaler.isInProgress) {
                cloudRenderer.yaw += (event.x - lastX) * 0.35f
                cloudRenderer.pitch = (cloudRenderer.pitch + (event.y - lastY) * 0.35f).coerceIn(-89f, 89f)
                lastX = event.x; lastY = event.y
            }
            MotionEvent.ACTION_UP -> if (kotlin.math.abs(event.x - downX) < 12f && kotlin.math.abs(event.y - downY) < 12f) {
                onPlacementTap?.invoke((event.x / width).coerceIn(0f, 1f), (event.y / height).coerceIn(0f, 1f))
            }
        }
        return true
    }
}

private class CloudRenderer : GLSurfaceView.Renderer {
    @Volatile var onCameraTextureReady: ((Int) -> Unit)? = null
    @Volatile var onRenderFrame: (() -> Unit)? = null
    @Volatile private var pending: PointCloudSnapshot? = null
    @Volatile var placement: PlacementResult? = null
    @Volatile var placementSize: PlacementObjectSize? = null
    @Volatile var config = DebugRenderConfig()
    @Volatile var yaw = 35f
    @Volatile var pitch = -20f
    @Volatile var zoom = 2.5f
    @Volatile var renderFps = 0.0
    private var program = 0
    private var pointBuffer: FloatBuffer? = null
    private var pointCount = 0
    private val center = FloatArray(3)
    private var minHeight = -1f
    private var maxHeight = 1f
    private var frames = 0
    private var fpsStart = System.nanoTime()

    fun submit(snapshot: PointCloudSnapshot?) { pending = snapshot }
    fun resetView() { yaw = 35f; pitch = -20f; zoom = 2.5f }

    override fun onSurfaceCreated(gl: javax.microedition.khronos.opengles.GL10?, cfg: javax.microedition.khronos.egl.EGLConfig?) {
        GLES30.glClearColor(0.025f, 0.035f, 0.055f, 1f)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        program = link(VERTEX, FRAGMENT)
        val textures = IntArray(1)
        GLES30.glGenTextures(1, textures, 0)
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textures[0])
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        onCameraTextureReady?.invoke(textures[0])
    }

    override fun onSurfaceChanged(gl: javax.microedition.khronos.opengles.GL10?, width: Int, height: Int) { GLES30.glViewport(0, 0, width, height) }

    override fun onDrawFrame(gl: javax.microedition.khronos.opengles.GL10?) {
        onRenderFrame?.invoke()
        pending?.also { upload(it); pending = null }
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
        val buffer = pointBuffer ?: return updateFps()
        val viewport = IntArray(4); GLES30.glGetIntegerv(GLES30.GL_VIEWPORT, viewport, 0)
        val projection = FloatArray(16); Matrix.perspectiveM(projection, 0, 55f, viewport[2].toFloat() / viewport[3].coerceAtLeast(1), config.nearPlane, config.farPlane)
        val yawRad = Math.toRadians(yaw.toDouble()); val pitchRad = Math.toRadians(pitch.toDouble())
        val eyeX = center[0] + (zoom * cos(pitchRad) * sin(yawRad)).toFloat()
        val eyeY = center[1] + (zoom * sin(pitchRad)).toFloat()
        val eyeZ = center[2] + (zoom * cos(pitchRad) * cos(yawRad)).toFloat()
        val view = FloatArray(16); Matrix.setLookAtM(view, 0, eyeX, eyeY, eyeZ, center[0], center[1], center[2], 0f, 1f, 0f)
        val vp = FloatArray(16); Matrix.multiplyMM(vp, 0, projection, 0, view, 0)
        GLES30.glUseProgram(program)
        GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(program, "uMvp"), 1, false, vp, 0)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(program, "uPointSize"), config.pointSize)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(program, "uMinHeight"), minHeight)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(program, "uMaxHeight"), maxHeight)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(program, "uUseHeightColor"), 1)
        GLES30.glEnableVertexAttribArray(0)
        buffer.position(0); GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 4 * 4, buffer)
        GLES30.glDrawArrays(GLES30.GL_POINTS, 0, pointCount)
        GLES30.glDisableVertexAttribArray(0)
        drawPlacement(vp)
        updateFps()
    }

    private fun drawPlacement(vp: FloatArray) {
        val result = placement ?: return
        val pose = result.pose ?: return
        val vertices = ArrayList<Float>()
        if (config.showNormal) {
            vertices += listOf(pose.position.x, pose.position.y, pose.position.z)
            val tip = pose.position + pose.surfaceNormal * 0.35f
            vertices += listOf(tip.x, tip.y, tip.z)
        }
        if (config.showPlacementFootprint) {
            val size = placementSize ?: PlacementObjectSize(0.3f, 0.3f, 0.3f)
            val n = pose.surfaceNormal
            val xAxis = Vec3(1f, 0f, 0f).let { (it - n * it.dot(n)).normalized() }
            val zAxis = n.cross(xAxis).normalized()
            val corners = listOf(
                pose.position - xAxis * (size.widthMeters / 2f) - zAxis * (size.depthMeters / 2f),
                pose.position + xAxis * (size.widthMeters / 2f) - zAxis * (size.depthMeters / 2f),
                pose.position + xAxis * (size.widthMeters / 2f) + zAxis * (size.depthMeters / 2f),
                pose.position - xAxis * (size.widthMeters / 2f) + zAxis * (size.depthMeters / 2f),
            )
            for (i in corners.indices) {
                val a = corners[i]; val b = corners[(i + 1) % corners.size]
                vertices += listOf(a.x, a.y, a.z, b.x, b.y, b.z)
            }
        }
        if (vertices.isEmpty()) return
        val buffer = ByteBuffer.allocateDirect(vertices.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply { vertices.forEach(::put); position(0) }
        GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(program, "uMvp"), 1, false, vp, 0)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(program, "uUseHeightColor"), 0)
        GLES30.glUniform4f(GLES30.glGetUniformLocation(program, "uSolidColor"), if (result.isValid) 0.2f else 1f, if (result.isValid) 1f else 0.2f, 0.25f, 1f)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 0, buffer)
        GLES30.glLineWidth(4f)
        GLES30.glDrawArrays(GLES30.GL_LINES, 0, vertices.size / 3)
        GLES30.glDisableVertexAttribArray(0)
    }

    private fun upload(snapshot: PointCloudSnapshot) {
        val points = snapshot.copyPoints()
        pointCount = snapshot.pointCount
        pointBuffer = ByteBuffer.allocateDirect(points.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(points); position(0) }
        if (pointCount > 0) {
            center.fill(0f)
            minHeight = Float.POSITIVE_INFINITY
            maxHeight = Float.NEGATIVE_INFINITY
            for (i in 0 until pointCount) {
                center[0] += points[i * 4]; center[1] += points[i * 4 + 1]; center[2] += points[i * 4 + 2]
                minHeight = minOf(minHeight, points[i * 4 + 1])
                maxHeight = maxOf(maxHeight, points[i * 4 + 1])
            }
            center[0] /= pointCount; center[1] /= pointCount; center[2] /= pointCount
            if (maxHeight - minHeight < 0.1f) maxHeight = minHeight + 0.1f
        }
    }

    private fun updateFps() {
        frames++
        val now = System.nanoTime()
        if (now - fpsStart >= 1_000_000_000L) { renderFps = frames * 1e9 / (now - fpsStart); frames = 0; fpsStart = now }
    }

    private fun link(vertex: String, fragment: String): Int {
        fun shader(type: Int, source: String): Int = GLES30.glCreateShader(type).also { GLES30.glShaderSource(it, source); GLES30.glCompileShader(it) }
        return GLES30.glCreateProgram().also { GLES30.glAttachShader(it, shader(GLES30.GL_VERTEX_SHADER, vertex)); GLES30.glAttachShader(it, shader(GLES30.GL_FRAGMENT_SHADER, fragment)); GLES30.glLinkProgram(it) }
    }

    companion object {
        private const val VERTEX = """#version 300 es
            layout(location=0) in vec3 aPosition;
            uniform mat4 uMvp; uniform float uPointSize; uniform float uMinHeight; uniform float uMaxHeight;
            out float vHeight;
            void main(){ gl_Position=uMvp*vec4(aPosition,1.0); gl_PointSize=uPointSize; vHeight=clamp((aPosition.y-uMinHeight)/max(uMaxHeight-uMinHeight,.001),0.0,1.0); }
        """
        private const val FRAGMENT = """#version 300 es
            precision mediump float; in float vHeight; uniform bool uUseHeightColor; uniform vec4 uSolidColor; out vec4 outColor;
            vec3 heightColor(float t) {
                if (t < .25) return mix(vec3(.02,.18,1.0), vec3(.0,1.0,1.0), t*4.0);
                if (t < .50) return mix(vec3(.0,1.0,1.0), vec3(.05,1.0,.15), (t-.25)*4.0);
                if (t < .75) return mix(vec3(.05,1.0,.15), vec3(1.0,1.0,.0), (t-.50)*4.0);
                return mix(vec3(1.0,1.0,.0), vec3(1.0,.12,.02), (t-.75)*4.0);
            }
            void main(){ vec2 p=gl_PointCoord-vec2(.5); if(dot(p,p)>.25) discard; outColor=uUseHeightColor?vec4(heightColor(vHeight),1.0):uSolidColor; }
        """
    }
}
