package com.geotime.ar.ar

import android.opengl.GLES20
import android.opengl.Matrix
import com.google.ar.core.Anchor
import com.google.ar.core.TrackingState

class AnchorMarkerRenderer {
    private var program = 0
    private val model = FloatArray(16)
    private val modelView = FloatArray(16)
    private val mvp = FloatArray(16)

    fun createOnGlThread() {
        program = GlTools.createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
    }

    fun draw(anchor: Anchor, view: FloatArray, projection: FloatArray) {
        if (anchor.trackingState != TrackingState.TRACKING) return
        anchor.pose.toMatrix(model, 0)
        Matrix.multiplyMM(modelView, 0, view, 0, model, 0)
        Matrix.multiplyMM(mvp, 0, projection, 0, modelView, 0)

        GLES20.glUseProgram(program)
        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(program, "u_Mvp"), 1, false, mvp, 0)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glDrawArrays(GLES20.GL_POINTS, 0, 1)
        GLES20.glDisable(GLES20.GL_BLEND)
    }

    companion object {
        private const val VERTEX_SHADER = """
            uniform mat4 u_Mvp;
            void main() {
                gl_Position = u_Mvp * vec4(0.0, 0.0, 0.0, 1.0);
                gl_PointSize = 64.0;
            }
        """
        private const val FRAGMENT_SHADER = """
            precision mediump float;
            void main() {
                vec2 p = gl_PointCoord - vec2(0.5);
                if (length(p) > 0.5) discard;
                gl_FragColor = vec4(1.0, 0.70, 0.0, 0.92);
            }
        """
    }
}

