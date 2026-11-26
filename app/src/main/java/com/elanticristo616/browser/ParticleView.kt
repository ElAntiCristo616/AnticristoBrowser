package com.elanticristo616.browser

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.sin
import kotlin.random.Random

/**
 * Partículas animadas del fondo, una por tema (v1.0.4):
 *  - SNOW: copos blancos cayendo con vaivén (Navidad, Invierno).
 *  - PETALS: pétalos rosa cayendo y girando (Sakura).
 *  - LEAVES: hojas cayendo y girando (Bosque, Otoño).
 *  - BUBBLES: burbujas subiendo (Océano).
 *  - EMBERS: brasas naranjas subiendo con parpadeo (Fuego).
 *  - RAIN: gotas de lluvia cayendo rápido (Lluvia).
 *  - STARS: estrellas titilando con caída lenta (Galaxia).
 *  - HEARTS: corazones rosa cayendo (Dulce).
 *  - SPARKLES: destellos cian/magenta titilando (Neón).
 *  - GOLD_DUST: polvo dorado cayendo despacio (Atardecer).
 *  - NONE: sin partículas (Claro).
 *
 * El tipo y el color base se ponen con setTheme() desde cada
 * actividad (ThemeManager.current). Sustituye a SnowView, que
 * solo sabía hacer nieve.
 */
class ParticleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private data class Particle(
        var x: Float, var y: Float, var r: Float, var speed: Float,
        var sway: Float, var phase: Float, var rot: Float, var rotSpeed: Float,
        var color: Int, var alpha: Int
    )

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private var particles: List<Particle> = emptyList()
    private var seeded = false
    private var kind: ThemeManager.ParticleKind = ThemeManager.ParticleKind.SNOW
    private var baseColor: Int = 0xFFFFFFFF.toInt()

    /** Colores que alterna cada partícula según el tipo. */
    private fun paletteFor(kind: ThemeManager.ParticleKind, base: Int): IntArray = when (kind) {
        ThemeManager.ParticleKind.PETALS -> intArrayOf(
            0xFFFFB7C9.toInt(), 0xFFFF8FB3.toInt(), 0xFFFFD1DC.toInt(), 0xFFF48FB1.toInt()
        )
        ThemeManager.ParticleKind.LEAVES -> intArrayOf(
            0xFF81C784.toInt(), 0xFFFFB74D.toInt(), 0xFFFF8A65.toInt(),
            0xFFA5D6A7.toInt(), 0xFFFFCC80.toInt()
        )
        ThemeManager.ParticleKind.BUBBLES -> intArrayOf(
            0xFFBFE9FF.toInt(), 0xFFFFFFFF.toInt(), 0xFF81D4FA.toInt()
        )
        ThemeManager.ParticleKind.EMBERS -> intArrayOf(
            0xFFFFB74D.toInt(), 0xFFFF7043.toInt(), 0xFFFFD54F.toInt(), 0xFFFF5722.toInt()
        )
        ThemeManager.ParticleKind.STARS -> intArrayOf(
            0xFFFFFFFF.toInt(), 0xFFFFF59D.toInt(), 0xFFB388FF.toInt()
        )
        ThemeManager.ParticleKind.HEARTS -> intArrayOf(
            0xFFFF8FB3.toInt(), 0xFFFF80AB.toInt(), 0xFFF48FB1.toInt(), 0xFFFFB7C9.toInt()
        )
        ThemeManager.ParticleKind.SPARKLES -> intArrayOf(
            0xFF00E5FF.toInt(), 0xFFFF00E5.toInt(), 0xFFFFFFFF.toInt()
        )
        ThemeManager.ParticleKind.GOLD_DUST -> intArrayOf(
            0xFFFFD54F.toInt(), 0xFFFFC107.toInt(), 0xFFFFF59D.toInt()
        )
        else -> intArrayOf(base)
    }

    fun setTheme(kind: ThemeManager.ParticleKind, color: Int) {
        this.kind = kind
        this.baseColor = color
        seeded = false
        requestLayout()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0 && kind != ThemeManager.ParticleKind.NONE) {
            val rnd = Random(42)
            val palette = paletteFor(kind, baseColor)
            val count = when (kind) {
                ThemeManager.ParticleKind.RAIN -> 80
                ThemeManager.ParticleKind.STARS -> 70
                ThemeManager.ParticleKind.SPARKLES -> 45
                ThemeManager.ParticleKind.GOLD_DUST -> 45
                else -> 55
            }
            particles = List(count) {
                Particle(
                    x = rnd.nextFloat() * w,
                    y = rnd.nextFloat() * h,
                    r = when (kind) {
                        ThemeManager.ParticleKind.RAIN -> 14f + rnd.nextFloat() * 14f
                        ThemeManager.ParticleKind.STARS -> 1.5f + rnd.nextFloat() * 2.5f
                        ThemeManager.ParticleKind.SPARKLES -> 3f + rnd.nextFloat() * 4f
                        ThemeManager.ParticleKind.GOLD_DUST -> 1.5f + rnd.nextFloat() * 2.5f
                        ThemeManager.ParticleKind.BUBBLES -> 3f + rnd.nextFloat() * 8f
                        ThemeManager.ParticleKind.EMBERS -> 2f + rnd.nextFloat() * 4f
                        else -> 3f + rnd.nextFloat() * 6f
                    },
                    speed = when (kind) {
                        ThemeManager.ParticleKind.RAIN -> 9f + rnd.nextFloat() * 7f
                        ThemeManager.ParticleKind.BUBBLES -> 0.5f + rnd.nextFloat() * 1.2f
                        ThemeManager.ParticleKind.EMBERS -> 0.8f + rnd.nextFloat() * 1.6f
                        ThemeManager.ParticleKind.STARS -> 0.15f + rnd.nextFloat() * 0.35f
                        ThemeManager.ParticleKind.SPARKLES -> 0.1f + rnd.nextFloat() * 0.3f
                        ThemeManager.ParticleKind.GOLD_DUST -> 0.3f + rnd.nextFloat() * 0.6f
                        else -> 0.6f + rnd.nextFloat() * 1.6f
                    },
                    sway = 12f + rnd.nextFloat() * 22f,
                    phase = rnd.nextFloat() * 6.28f,
                    rot = rnd.nextFloat() * 360f,
                    rotSpeed = (rnd.nextFloat() - 0.5f) * 90f,
                    color = palette[rnd.nextInt(palette.size)],
                    alpha = 140 + rnd.nextInt(115)
                )
            }
            seeded = true
        }
    }

    /** Corazón en un cuadro de 20x20, para HEARTS. */
    private val heartPath = Path().apply {
        moveTo(10f, 18f)
        cubicTo(4f, 12f, 0f, 8.5f, 0f, 5f)
        cubicTo(0f, 2f, 2.2f, 0f, 5f, 0f)
        cubicTo(7f, 0f, 9f, 1.2f, 10f, 3f)
        cubicTo(11f, 1.2f, 13f, 0f, 15f, 0f)
        cubicTo(17.8f, 0f, 20f, 2f, 20f, 5f)
        cubicTo(20f, 8.5f, 16f, 12f, 10f, 18f)
        close()
    }

    /** Diamante (rombo) de 4 puntas, para SPARKLES. */
    private fun sparklePath(size: Float): Path = Path().apply {
        moveTo(0f, -size)
        lineTo(size * 0.35f, -size * 0.35f)
        lineTo(size, 0f)
        lineTo(size * 0.35f, size * 0.35f)
        lineTo(0f, size)
        lineTo(-size * 0.35f, size * 0.35f)
        lineTo(-size, 0f)
        lineTo(-size * 0.35f, -size * 0.35f)
        close()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!seeded || kind == ThemeManager.ParticleKind.NONE) return
        val w = width.toFloat()
        val h = height.toFloat()
        val t = System.currentTimeMillis() / 1000f
        val rect = RectF()
        for (p in particles) {
            val x = (p.x + sin(t + p.phase) * p.sway) % (w + 20f)
            // Las que suben (burbujas, brasas) restan; las demás suman.
            val rising = kind == ThemeManager.ParticleKind.BUBBLES ||
                kind == ThemeManager.ParticleKind.EMBERS
            var y = if (rising) p.y - t * p.speed * 30f else p.y + t * p.speed * 30f
            y = ((y % (h + 20f)) + (h + 20f)) % (h + 20f)
            val cx = if (x < 0) x + w + 20f else x
            val cy = y - 10f
            // Titileo para estrellas, destellos, polvo dorado y brasas.
            val twinkle = when (kind) {
                ThemeManager.ParticleKind.STARS,
                ThemeManager.ParticleKind.SPARKLES,
                ThemeManager.ParticleKind.GOLD_DUST ->
                    0.45f + 0.55f * (0.5f + 0.5f * sin(t * 2.5f + p.phase * 3f))
                ThemeManager.ParticleKind.EMBERS ->
                    0.6f + 0.4f * (0.5f + 0.5f * sin(t * 6f + p.phase * 5f))
                else -> 1f
            }
            paint.color = p.color
            paint.alpha = (p.alpha * twinkle).toInt().coerceIn(0, 255)
            when (kind) {
                ThemeManager.ParticleKind.SNOW -> canvas.drawCircle(cx, cy, p.r, paint)
                ThemeManager.ParticleKind.STARS -> canvas.drawCircle(cx, cy, p.r, paint)
                ThemeManager.ParticleKind.GOLD_DUST -> canvas.drawCircle(cx, cy, p.r, paint)
                ThemeManager.ParticleKind.BUBBLES -> {
                    strokePaint.color = p.color
                    strokePaint.alpha = (p.alpha * 0.8f).toInt()
                    strokePaint.strokeWidth = 2f
                    canvas.drawCircle(cx, cy, p.r, strokePaint)
                    paint.alpha = (p.alpha * 0.25f).toInt()
                    canvas.drawCircle(cx, cy, p.r, paint)
                }
                ThemeManager.ParticleKind.EMBERS -> canvas.drawCircle(cx, cy, p.r, paint)
                ThemeManager.ParticleKind.RAIN -> {
                    strokePaint.color = p.color
                    strokePaint.alpha = 150
                    strokePaint.strokeWidth = 2.5f
                    canvas.drawLine(cx, cy, cx - 3f, cy + p.r, strokePaint)
                }
                ThemeManager.ParticleKind.PETALS, ThemeManager.ParticleKind.LEAVES -> {
                    canvas.save()
                    canvas.translate(cx, cy)
                    canvas.rotate(p.rot + t * p.rotSpeed)
                    rect.set(-p.r, -p.r * 0.6f, p.r, p.r * 0.6f)
                    canvas.drawOval(rect, paint)
                    canvas.restore()
                }
                ThemeManager.ParticleKind.HEARTS -> {
                    canvas.save()
                    canvas.translate(cx, cy)
                    canvas.rotate(p.rot * 0.3f + sin(t + p.phase) * 25f)
                    val s = p.r / 10f
                    canvas.scale(s, s)
                    canvas.translate(-10f, -10f)
                    canvas.drawPath(heartPath, paint)
                    canvas.restore()
                }
                ThemeManager.ParticleKind.SPARKLES -> {
                    canvas.save()
                    canvas.translate(cx, cy)
                    canvas.rotate(t * 20f + p.phase * 40f)
                    canvas.drawPath(sparklePath(p.r), paint)
                    canvas.restore()
                }
                else -> canvas.drawCircle(cx, cy, p.r, paint)
            }
        }
        postInvalidateOnAnimation()
    }
}
