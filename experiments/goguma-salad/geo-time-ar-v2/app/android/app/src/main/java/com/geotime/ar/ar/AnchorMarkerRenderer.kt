package com.geotime.ar.ar

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.opengl.GLES20
import android.opengl.GLUtils
import android.opengl.Matrix
import com.google.ar.core.Anchor
import com.google.ar.core.TrackingState
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

class AnchorMarkerRenderer {
    private val vertices = floatBuffer(
        floatArrayOf(
            -0.75f, -0.42f, 0f, 0f, 1f,
            0.75f, -0.42f, 0f, 1f, 1f,
            -0.75f, 0.42f, 0f, 0f, 0f,
            -0.75f, 0.42f, 0f, 0f, 0f,
            0.75f, -0.42f, 0f, 1f, 1f,
            0.75f, 0.42f, 0f, 1f, 0f,
        )
    )
    private val textures = mutableMapOf<String, Int>()
    private var program = 0
    private val model = FloatArray(16)
    private val modelView = FloatArray(16)
    private val mvp = FloatArray(16)

    fun createOnGlThread() {
        program = GlTools.createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
    }

    fun draw(
        anchor: Anchor,
        title: String,
        view: FloatArray,
        projection: FloatArray,
        contentAlpha: Float,
    ) {
        if (anchor.trackingState != TrackingState.TRACKING) return
        anchor.pose.toMatrix(model, 0)
        Matrix.multiplyMM(modelView, 0, view, 0, model, 0)
        Matrix.multiplyMM(mvp, 0, projection, 0, modelView, 0)

        GLES20.glUseProgram(program)
        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(program, "u_Mvp"), 1, false, mvp, 0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures.getOrPut(title) { createCardTexture(title) })
        GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "u_Texture"), 0)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "u_Alpha"), contentAlpha)

        val stride = 5 * 4
        val position = GLES20.glGetAttribLocation(program, "a_Position")
        val texCoord = GLES20.glGetAttribLocation(program, "a_TexCoord")
        vertices.position(0)
        GLES20.glEnableVertexAttribArray(position)
        GLES20.glVertexAttribPointer(position, 3, GLES20.GL_FLOAT, false, stride, vertices)
        vertices.position(3)
        GLES20.glEnableVertexAttribArray(texCoord)
        GLES20.glVertexAttribPointer(texCoord, 2, GLES20.GL_FLOAT, false, stride, vertices)

        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6)
        GLES20.glDisable(GLES20.GL_BLEND)
        GLES20.glDisableVertexAttribArray(position)
        GLES20.glDisableVertexAttribArray(texCoord)
    }

    private fun createCardTexture(title: String): Int {
        val bitmap = Bitmap.createBitmap(768, 432, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val background = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(235, 17, 24, 39) }
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(251, 191, 36)
            style = Paint.Style.STROKE
            strokeWidth = 14f
        }
        canvas.drawRoundRect(RectF(16f, 16f, 752f, 416f), 42f, 42f, background)
        canvas.drawRoundRect(RectF(16f, 16f, 752f, 416f), 42f, 42f, border)

        val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(251, 191, 36)
            textAlign = Paint.Align.CENTER
            textSize = 44f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        canvas.drawText("GEO · TIME · AR", 384f, 105f, label)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            textSize = 62f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        val displayTitle = if (title.length > 22) title.take(21) + "…" else title
        canvas.drawText(displayTitle, 384f, 245f, titlePaint)

        val hint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.LTGRAY
            textAlign = Paint.Align.CENTER
            textSize = 34f
        }
        canvas.drawText("터치해서 시간 기록 보기", 384f, 335f, hint)

        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, ids[0])
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
        bitmap.recycle()
        return ids[0]
    }

    private fun floatBuffer(values: FloatArray): FloatBuffer =
        ByteBuffer.allocateDirect(values.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(values)
                position(0)
            }

    companion object {
        private const val VERTEX_SHADER = """
            uniform mat4 u_Mvp;
            attribute vec3 a_Position;
            attribute vec2 a_TexCoord;
            varying vec2 v_TexCoord;
            void main() {
                gl_Position = u_Mvp * vec4(a_Position, 1.0);
                v_TexCoord = a_TexCoord;
            }
        """
        private const val FRAGMENT_SHADER = """
            precision mediump float;
            uniform sampler2D u_Texture;
            uniform float u_Alpha;
            varying vec2 v_TexCoord;
            void main() {
                vec4 color = texture2D(u_Texture, v_TexCoord);
                gl_FragColor = vec4(color.rgb, color.a * u_Alpha);
            }
        """
    }
}
