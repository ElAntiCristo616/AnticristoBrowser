package com.elanticristo616.browser

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View

/**
 * CuteNovna Browser v1.1.0 — BRILLO NEÓN ESTÁTICO.
 *
 * Dos brillos radiales fijos sobre el fondo oscuro (rosa arriba a
 * la izquierda, teal abajo a la derecha), como el fondo de la
 * referencia. Es ESTÁTICO: no hay animación ni partículas (v1.1.0
 * quitó ParticleView por orden del usuario).
 */
class NeonGlowView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var pinkPaint: Paint? = null
    private var tealPaint: Paint? = null

    init {
        isClickable = false
        isFocusable = false
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return
        pinkPaint = Paint().apply {
            shader = RadialGradient(
                w * 0.12f, h * 0.02f, w * 0.75f,
                Color.argb(64, 255, 46, 136),
                Color.TRANSPARENT,
                Shader.TileMode.CLAMP
            )
        }
        tealPaint = Paint().apply {
            shader = RadialGradient(
                w * 0.92f, h * 0.98f, w * 0.8f,
                Color.argb(56, 0, 229, 199),
                Color.TRANSPARENT,
                Shader.TileMode.CLAMP
            )
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        pinkPaint?.let { canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), it) }
        tealPaint?.let { canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), it) }
    }
}
