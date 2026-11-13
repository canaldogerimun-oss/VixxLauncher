package com.movtery.zalithlauncher.ui.view

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import java.util.ArrayDeque
import kotlin.math.abs
import kotlin.math.hypot

/** Pixel-perfect 64x64 skin texture editor with pinch zoom, pan and undo history. */
class SkinPixelEditorView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {
    private var bitmap: Bitmap? = null
    private var selectedColor: Int = Color.BLACK
    private var eraser = false
    private var lastPixelX = -1
    private var lastPixelY = -1
    private var pinchDistance = 0f
    private var pinchCenterX = 0f
    private var pinchCenterY = 0f
    private var zoom = 1f
    private var panX = 0f
    private var panY = 0f
    private val undo = ArrayDeque<Bitmap>()
    private val redo = ArrayDeque<Bitmap>()
    private val checker = Paint().apply { isAntiAlias = false }
    private val imagePaint = Paint().apply {
        isAntiAlias = false
        isFilterBitmap = false
        isDither = false
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x557F65A8
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }
    private val dst = Rect()
    private var changed: (() -> Unit)? = null

    fun setOnSkinChanged(listener: (() -> Unit)?) { changed = listener }

    fun setBitmap(source: Bitmap) {
        bitmap?.recycle()
        clearHistory()
        bitmap = source.copy(Bitmap.Config.ARGB_8888, true)
        zoom = 1f
        panX = 0f
        panY = 0f
        invalidate()
    }

    fun getEditedBitmap(): Bitmap? = bitmap?.copy(Bitmap.Config.ARGB_8888, false)
    fun setColor(color: Int) { selectedColor = color; eraser = false }
    fun setEraser(enabled: Boolean) { eraser = enabled }

    fun undo(): Boolean {
        if (undo.isEmpty()) return false
        bitmap?.let { redo.addLast(it.copy(Bitmap.Config.ARGB_8888, true)); it.recycle() }
        bitmap = undo.removeLast()
        invalidate()
        changed?.invoke()
        return true
    }

    fun redo(): Boolean {
        if (redo.isEmpty()) return false
        bitmap?.let { undo.addLast(it.copy(Bitmap.Config.ARGB_8888, true)); it.recycle() }
        bitmap = redo.removeLast()
        invalidate()
        changed?.invoke()
        return true
    }

    private fun clearHistory() {
        undo.forEach { it.recycle() }; undo.clear()
        redo.forEach { it.recycle() }; redo.clear()
    }

    private fun snapshot() {
        val current = bitmap ?: return
        undo.addLast(current.copy(Bitmap.Config.ARGB_8888, true))
        while (undo.size > 30) undo.removeFirst().recycle()
        redo.forEach { it.recycle() }; redo.clear()
    }

    private fun baseSide(): Float = minOf(width, height).toFloat() * .94f
    private fun imageSide(): Float = baseSide() * zoom
    private fun imageLeft(): Float = (width - imageSide()) / 2f + panX
    private fun imageTop(): Float = (height - imageSide()) / 2f + panY

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(0xFF17121F.toInt())
        val bmp = bitmap ?: return
        val side = imageSide()
        val left = imageLeft()
        val top = imageTop()
        val cell = side / 64f
        val save = canvas.save()
        canvas.clipRect(0, 0, width, height)
        // Checkerboard indicates transparent pixels.
        var y = 0
        while (y < 64) {
            var x = 0
            while (x < 64) {
                checker.color = if (((x / 4) + (y / 4)) % 2 == 0) 0xFF292332.toInt() else 0xFF393243.toInt()
                canvas.drawRect(left + x*cell, top + y*cell, left + (x+4)*cell, top + (y+4)*cell, checker)
                x += 4
            }
            y += 4
        }
        dst.set(left.toInt(), top.toInt(), (left+side).toInt(), (top+side).toInt())
        canvas.drawBitmap(bmp, null, dst, imagePaint)
        if (cell >= 6f) {
            for (i in 0..64) {
                val p = left + i*cell
                canvas.drawLine(p, top, p, top+side, gridPaint)
                val q = top + i*cell
                canvas.drawLine(left, q, left+side, q, gridPaint)
            }
        }
        canvas.restoreToCount(save)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                snapshot()
                lastPixelX = -1
                lastPixelY = -1
                paintAt(event.x, event.y)
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.pointerCount >= 2) {
                    pinchDistance = pointerDistance(event)
                    pinchCenterX = (event.getX(0) + event.getX(1)) / 2f
                    pinchCenterY = (event.getY(0) + event.getY(1)) / 2f
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount >= 2) {
                    val nextDistance = pointerDistance(event)
                    if (pinchDistance > 0f) {
                        zoom = (zoom * nextDistance / pinchDistance).coerceIn(1f, 8f)
                        val centerX = (event.getX(0) + event.getX(1)) / 2f
                        val centerY = (event.getY(0) + event.getY(1)) / 2f
                        val maxPan = (imageSide() - baseSide()) / 2f
                        panX = (panX + centerX - pinchCenterX).coerceIn(-maxPan, maxPan)
                        panY = (panY + centerY - pinchCenterY).coerceIn(-maxPan, maxPan)
                        pinchCenterX = centerX
                        pinchCenterY = centerY
                        invalidate()
                    }
                    pinchDistance = nextDistance
                } else {
                    paintAt(event.x, event.y)
                }
                return true
            }
            MotionEvent.ACTION_POINTER_UP -> {
                pinchDistance = 0f
                lastPixelX = -1
                lastPixelY = -1
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                lastPixelX = -1
                lastPixelY = -1
                pinchDistance = 0f
                parent?.requestDisallowInterceptTouchEvent(false)
                performClick()
                return true
            }
        }
        return true
    }

    private fun paintAt(px: Float, py: Float) {
        val bmp = bitmap ?: return
        val cell = imageSide() / 64f
        val x = ((px-imageLeft()) / cell).toInt()
        val y = ((py-imageTop()) / cell).toInt()
        if (x !in 0..63 || y !in 0..63) return
        if (lastPixelX >= 0 && lastPixelY >= 0) {
            drawLine(lastPixelX, lastPixelY, x, y, bmp)
        } else {
            bmp.setPixel(x, y, if (eraser) Color.TRANSPARENT else selectedColor)
        }
        lastPixelX = x
        lastPixelY = y
        invalidate()
        changed?.invoke()
    }

    private fun drawLine(x0: Int, y0: Int, x1: Int, y1: Int, bmp: Bitmap) {
        var x = x0
        var y = y0
        val dx = abs(x1-x0)
        val sx = if (x0 < x1) 1 else -1
        val dy = -abs(y1-y0)
        val sy = if (y0 < y1) 1 else -1
        var error = dx + dy
        while (true) {
            bmp.setPixel(x, y, if (eraser) Color.TRANSPARENT else selectedColor)
            if (x == x1 && y == y1) break
            val e2 = 2 * error
            if (e2 >= dy) { error += dy; x += sx }
            if (e2 <= dx) { error += dx; y += sy }
        }
    }

    private fun pointerDistance(e: MotionEvent): Float {
        if (e.pointerCount < 2) return 0f
        return hypot((e.getX(0)-e.getX(1)).toDouble(), (e.getY(0)-e.getY(1)).toDouble()).toFloat()
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onDetachedFromWindow() {
        clearHistory()
        bitmap?.recycle()
        bitmap = null
        super.onDetachedFromWindow()
    }
}
