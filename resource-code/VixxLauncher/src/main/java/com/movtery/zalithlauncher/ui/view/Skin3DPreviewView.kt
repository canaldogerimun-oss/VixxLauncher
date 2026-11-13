package com.movtery.zalithlauncher.ui.view

import android.content.Context
import android.graphics.Bitmap
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewParent
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import android.opengl.Matrix
import kotlin.math.hypot

/**
 * OpenGL ES 2.0 Minecraft skin preview.
 *
 * Uses a depth buffer and a real perspective camera instead of painting projected
 * polygons onto Canvas. UV rectangles follow the 64x64 Java skin atlas.
 */
class Skin3DPreviewView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : GLSurfaceView(context, attrs) {

    private val skinRenderer = SkinRenderer()
    private var lastX = 0f
    private var lastY = 0f
    private var pinchDistance = 0f
    private var dragging = false

    init {
        setEGLContextClientVersion(2)
        setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        setRenderer(skinRenderer)
        renderMode = RENDERMODE_WHEN_DIRTY
        preserveEGLContextOnPause = true
        setZOrderOnTop(false)
    }

    fun setSkin(bitmap: Bitmap?, isSlim: Boolean) {
        // Copy before crossing threads: the fragment may recycle its bitmap when
        // its view is destroyed while the GL queue is still waiting.
        val safeCopy = bitmap?.copy(Bitmap.Config.ARGB_8888, false)
        queueEvent { skinRenderer.setSkin(safeCopy, isSlim) }
        requestRender()
    }

    fun setOuterLayerVisible(visible: Boolean) {
        queueEvent {
            skinRenderer.showOuter = visible
            skinRenderer.rebuildMesh()
        }
        requestRender()
    }

    fun setViewPreset(preset: String) {
        queueEvent {
            when (preset) {
                "front" -> { skinRenderer.yaw = 0f; skinRenderer.pitch = 0f }
                "back" -> { skinRenderer.yaw = 180f; skinRenderer.pitch = 0f }
                "left" -> { skinRenderer.yaw = -90f; skinRenderer.pitch = 0f }
                "right" -> { skinRenderer.yaw = 90f; skinRenderer.pitch = 0f }
                "top" -> { skinRenderer.yaw = 0f; skinRenderer.pitch = -65f }
                else -> { skinRenderer.yaw = -25f; skinRenderer.pitch = -8f }
            }
        }
        requestRender()
    }

    override fun onPause() {
        super.onPause()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                lastX = event.x
                lastY = event.y
                dragging = true
                return true
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.pointerCount >= 2) pinchDistance = pointerDistance(event)
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }
            MotionEvent.ACTION_POINTER_UP -> {
                pinchDistance = 0f
                if (event.pointerCount > 1) {
                    val remaining = if (event.actionIndex == 0) 1 else 0
                    lastX = event.getX(remaining)
                    lastY = event.getY(remaining)
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount >= 2) {
                    val next = pointerDistance(event)
                    if (pinchDistance > 0f) {
                        queueEvent { skinRenderer.zoom = (skinRenderer.zoom * next / pinchDistance).coerceIn(.65f, 2.4f) }
                        requestRender()
                    }
                    pinchDistance = next
                } else if (dragging) {
                    val dx = event.x - lastX
                    val dy = event.y - lastY
                    queueEvent {
                        skinRenderer.yaw = (skinRenderer.yaw + dx * .55f) % 360f
                        skinRenderer.pitch = (skinRenderer.pitch + dy * .35f).coerceIn(-45f, 45f)
                    }
                    requestRender()
                    lastX = event.x
                    lastY = event.y
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                dragging = false
                pinchDistance = 0f
                parent?.requestDisallowInterceptTouchEvent(false)
                performClick()
                return true
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun pointerDistance(e: MotionEvent): Float {
        if (e.pointerCount < 2) return 0f
        return hypot((e.getX(0) - e.getX(1)).toDouble(), (e.getY(0) - e.getY(1)).toDouble()).toFloat()
    }

    private class SkinRenderer : Renderer {
        @Volatile var yaw = -25f
        @Volatile var pitch = -8f
        @Volatile var zoom = 1f
        @Volatile var showOuter = true
        private var slim = false
        private var textureId = 0
        private var program = 0
        private var vertexBuffer: FloatBuffer? = null
        private var vertexCount = 0
        private var projection = FloatArray(16)
        private var view = FloatArray(16)
        private var model = FloatArray(16)
        private var mvp = FloatArray(16)
        private var positionHandle = 0
        private var uvHandle = 0
        private var normalHandle = 0
        private var matrixHandle = 0
        private var modelHandle = 0
        private var samplerHandle = 0
        private var bitmap: Bitmap? = null
        private var surfaceWidth = 1
        private var surfaceHeight = 1

        private val vertexShader = """
            attribute vec3 aPosition;
            attribute vec2 aTexCoord;
            attribute vec3 aNormal;
            uniform mat4 uMVP;
            uniform mat4 uModel;
            varying vec2 vTexCoord;
            varying float vLight;
            void main() {
                gl_Position = uMVP * vec4(aPosition, 1.0);
                vec3 worldNormal = normalize((uModel * vec4(aNormal, 0.0)).xyz);
                vec3 lightDir = normalize(vec3(-0.4, 0.75, 0.55));
                vLight = 0.62 + 0.38 * max(dot(worldNormal, lightDir), 0.0);
                vTexCoord = aTexCoord;
            }
        """.trimIndent()

        private val fragmentShader = """
            precision mediump float;
            varying vec2 vTexCoord;
            varying float vLight;
            uniform sampler2D uTexture;
            void main() {
                vec4 color = texture2D(uTexture, vTexCoord);
                if (color.a < 0.06) discard;
                gl_FragColor = vec4(color.rgb * vLight, color.a);
            }
        """.trimIndent()

        fun setSkin(source: Bitmap?, isSlim: Boolean) {
            bitmap?.recycle()
            bitmap = source
            slim = isSlim
            rebuildMesh()
            if (textureId != 0 && bitmap != null) {
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
                GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
            }
        }

        override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
            GLES20.glClearColor(.035f, .025f, .055f, 1f)
            GLES20.glEnable(GLES20.GL_DEPTH_TEST)
            GLES20.glDepthFunc(GLES20.GL_LEQUAL)
            GLES20.glEnable(GLES20.GL_BLEND)
            GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
            program = createProgram(vertexShader, fragmentShader)
            positionHandle = GLES20.glGetAttribLocation(program, "aPosition")
            uvHandle = GLES20.glGetAttribLocation(program, "aTexCoord")
            normalHandle = GLES20.glGetAttribLocation(program, "aNormal")
            matrixHandle = GLES20.glGetUniformLocation(program, "uMVP")
            modelHandle = GLES20.glGetUniformLocation(program, "uModel")
            samplerHandle = GLES20.glGetUniformLocation(program, "uTexture")
            val textures = IntArray(1)
            GLES20.glGenTextures(1, textures, 0)
            textureId = textures[0]
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            bitmap?.let { GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, it, 0) }
            rebuildMesh()
        }

        override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
            surfaceWidth = width.coerceAtLeast(1)
            surfaceHeight = height.coerceAtLeast(1)
            GLES20.glViewport(0, 0, surfaceWidth, surfaceHeight)
            Matrix.perspectiveM(projection, 0, 34f, surfaceWidth.toFloat() / surfaceHeight, 1f, 160f)
        }

        override fun onDrawFrame(gl: GL10?) {
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
            if (program == 0 || textureId == 0 || vertexCount == 0 || bitmap == null) return
            Matrix.setLookAtM(view, 0, 0f, 0f, 64f / zoom, 0f, 0f, 0f, 0f, 1f, 0f)
            Matrix.setIdentityM(model, 0)
            Matrix.rotateM(model, 0, pitch, 1f, 0f, 0f)
            Matrix.rotateM(model, 0, yaw, 0f, 1f, 0f)
            val mv = FloatArray(16)
            Matrix.multiplyMM(mv, 0, view, 0, model, 0)
            Matrix.multiplyMM(mvp, 0, projection, 0, mv, 0)
            GLES20.glUseProgram(program)
            GLES20.glUniformMatrix4fv(matrixHandle, 1, false, mvp, 0)
            GLES20.glUniformMatrix4fv(modelHandle, 1, false, model, 0)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
            GLES20.glUniform1i(samplerHandle, 0)
            val buffer = vertexBuffer ?: return
            buffer.position(0)
            GLES20.glEnableVertexAttribArray(positionHandle)
            GLES20.glEnableVertexAttribArray(uvHandle)
            GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 32, buffer)
            buffer.position(3)
            GLES20.glVertexAttribPointer(uvHandle, 2, GLES20.GL_FLOAT, false, 32, buffer)
            buffer.position(5)
            GLES20.glEnableVertexAttribArray(normalHandle)
            GLES20.glVertexAttribPointer(normalHandle, 3, GLES20.GL_FLOAT, false, 32, buffer)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, vertexCount)
            GLES20.glDisableVertexAttribArray(positionHandle)
            GLES20.glDisableVertexAttribArray(uvHandle)
            GLES20.glDisableVertexAttribArray(normalHandle)
        }

        fun rebuildMesh() {
            val data = ArrayList<Float>(5000)
            val head = listOf(Uv(8,0,8,8), Uv(16,0,8,8), Uv(0,8,8,8), Uv(8,8,8,8), Uv(16,8,8,8), Uv(24,8,8,8))
            val torso = listOf(Uv(20,16,8,4), Uv(28,16,8,4), Uv(16,20,4,12), Uv(20,20,8,12), Uv(28,20,4,12), Uv(32,20,8,12))
            val rightLeg = listOf(Uv(4,16,4,4), Uv(8,16,4,4), Uv(0,20,4,12), Uv(4,20,4,12), Uv(8,20,4,12), Uv(12,20,4,12))
            val leftLeg = listOf(Uv(20,48,4,4), Uv(24,48,4,4), Uv(16,52,4,12), Uv(20,52,4,12), Uv(24,52,4,12), Uv(28,52,4,12))
            val armW = if (slim) 3f else 4f
            val armRight = armUvs(40, 16, slim)
            val armLeft = armUvs(32, 48, slim)
            addBox(data, 0f, 12f, 0f, 8f, 8f, 8f, head)
            addBox(data, 0f, 2f, 0f, 8f, 12f, 4f, torso)
            addBox(data, -2f, -10f, 0f, 4f, 12f, 4f, rightLeg)
            addBox(data, 2f, -10f, 0f, 4f, 12f, 4f, leftLeg)
            addBox(data, -4f - armW/2f, 2f, 0f, armW, 12f, 4f, armRight)
            addBox(data, 4f + armW/2f, 2f, 0f, armW, 12f, 4f, armLeft)
            if (showOuter) {
                addBox(data, 0f, 12f, 0f, 8.5f, 8.5f, 8.5f,
                    listOf(Uv(40,0,8,8),Uv(48,0,8,8),Uv(32,8,8,8),Uv(40,8,8,8),Uv(48,8,8,8),Uv(56,8,8,8)))
                addBox(data, 0f, 2f, 0f, 8.25f, 12.25f, 4.25f,
                    listOf(Uv(20,32,8,4),Uv(28,32,8,4),Uv(16,36,4,12),Uv(20,36,8,12),Uv(28,36,4,12),Uv(32,36,8,12)))
                addBox(data, -2f, -10f, 0f, 4.25f, 12.25f, 4.25f,
                    listOf(Uv(4,32,4,4),Uv(8,32,4,4),Uv(0,36,4,12),Uv(4,36,4,12),Uv(8,36,4,12),Uv(12,36,4,12)))
                addBox(data, 2f, -10f, 0f, 4.25f, 12.25f, 4.25f,
                    listOf(Uv(4,48,4,4),Uv(8,48,4,4),Uv(0,52,4,12),Uv(4,52,4,12),Uv(8,52,4,12),Uv(12,52,4,12)))
                addBox(data, -4f-armW/2f, 2f, 0f, armW+.25f, 12.25f, 4.25f, armUvs(40,32,slim))
                addBox(data, 4f+armW/2f, 2f, 0f, armW+.25f, 12.25f, 4.25f, armUvs(48,48,slim))
            }
            val array = FloatArray(data.size) { data[it] }
            vertexBuffer = ByteBuffer.allocateDirect(array.size * 4).order(ByteOrder.nativeOrder())
                .asFloatBuffer().apply { put(array); position(0) }
            vertexCount = array.size / 8
        }

        /**
         * Minecraft 64x64 player-skin UV layout.
         * originX/originY identify the upper-left corner of this arm's UV island:
         * right base (40,16), left base (32,48), right sleeve (40,32),
         * left sleeve (48,48). Keeping X and Y independent is essential:
         * the old implementation reused the right-arm X coordinates for both arms,
         * which sampled unrelated torso/leg pixels and made the model look corrupted.
         */
        private fun armUvs(originX: Int, originY: Int, isSlim: Boolean): List<Uv> {
            val frontWidth = if (isSlim) 3 else 4
            val sideWidth = 4
            val frontX = originX + sideWidth
            val backX = frontX + frontWidth + sideWidth
            return listOf(
                Uv(frontX, originY, frontWidth, 4),                 // top
                Uv(backX, originY, frontWidth, 4),                  // bottom
                Uv(originX, originY + 4, sideWidth, 12),             // left
                Uv(frontX, originY + 4, frontWidth, 12),             // front
                Uv(frontX + frontWidth, originY + 4, sideWidth, 12), // right
                Uv(backX, originY + 4, frontWidth, 12)               // back
            )
        }

        private data class Uv(val x: Int, val y: Int, val w: Int, val h: Int)

        private fun addBox(out: MutableList<Float>, cx: Float, cy: Float, cz: Float,
                           w: Float, h: Float, d: Float, uv: List<Uv>) {
            val x0 = cx-w/2; val x1 = cx+w/2
            val y0 = cy-h/2; val y1 = cy+h/2
            val z0 = cz-d/2; val z1 = cz+d/2
            val faces = listOf(
                listOf(floatArrayOf(x0,y1,z0),floatArrayOf(x0,y1,z1),floatArrayOf(x1,y1,z1),floatArrayOf(x1,y1,z0)),
                listOf(floatArrayOf(x0,y0,z1),floatArrayOf(x0,y0,z0),floatArrayOf(x1,y0,z0),floatArrayOf(x1,y0,z1)),
                listOf(floatArrayOf(x0,y1,z0),floatArrayOf(x0,y0,z0),floatArrayOf(x0,y0,z1),floatArrayOf(x0,y1,z1)),
                listOf(floatArrayOf(x0,y1,z1),floatArrayOf(x0,y0,z1),floatArrayOf(x1,y0,z1),floatArrayOf(x1,y1,z1)),
                listOf(floatArrayOf(x1,y1,z1),floatArrayOf(x1,y0,z1),floatArrayOf(x1,y0,z0),floatArrayOf(x1,y1,z0)),
                listOf(floatArrayOf(x1,y1,z0),floatArrayOf(x1,y0,z0),floatArrayOf(x0,y0,z0),floatArrayOf(x0,y1,z0))
            )
            val normals = arrayOf(
                floatArrayOf(0f,1f,0f), floatArrayOf(0f,-1f,0f),
                floatArrayOf(-1f,0f,0f), floatArrayOf(0f,0f,1f),
                floatArrayOf(1f,0f,0f), floatArrayOf(0f,0f,-1f)
            )
            faces.forEachIndexed { index, face ->
                val r = uv[index]
                val u0 = r.x / 64f; val u1 = (r.x+r.w) / 64f
                val v0 = 1f - r.y / 64f; val v1 = 1f - (r.y+r.h) / 64f
                val tex = arrayOf(floatArrayOf(u0,v0),floatArrayOf(u0,v1),floatArrayOf(u1,v1),floatArrayOf(u1,v0))
                val normal = normals[index]
                val order = intArrayOf(0,1,2,0,2,3)
                order.forEach { idx ->
                    out.add(face[idx][0]); out.add(face[idx][1]); out.add(face[idx][2])
                    out.add(tex[idx][0]); out.add(tex[idx][1])
                    out.add(normal[0]); out.add(normal[1]); out.add(normal[2])
                }
            }
        }

        private fun createProgram(vs: String, fs: String): Int {
            val vertex = compileShader(GLES20.GL_VERTEX_SHADER, vs)
            val fragment = compileShader(GLES20.GL_FRAGMENT_SHADER, fs)
            return GLES20.glCreateProgram().also { p ->
                GLES20.glAttachShader(p, vertex)
                GLES20.glAttachShader(p, fragment)
                GLES20.glLinkProgram(p)
                val status = IntArray(1)
                GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, status, 0)
                if (status[0] == 0) {
                    val error = GLES20.glGetProgramInfoLog(p)
                    GLES20.glDeleteProgram(p)
                    throw IllegalStateException("Skin preview shader link failed: $error")
                }
            }
        }

        private fun compileShader(type: Int, source: String): Int {
            val shader = GLES20.glCreateShader(type)
            GLES20.glShaderSource(shader, source)
            GLES20.glCompileShader(shader)
            val status = IntArray(1)
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
            if (status[0] == 0) {
                val error = GLES20.glGetShaderInfoLog(shader)
                GLES20.glDeleteShader(shader)
                throw IllegalStateException("Skin preview shader compile failed: $error")
            }
            return shader
        }
    }
}
