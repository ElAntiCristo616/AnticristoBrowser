package com.elanticristo616.browser

import android.graphics.drawable.GradientDrawable

/**
 * CuteNovna Browser v1.1.0 — ESTILO FIJO (sin temas).
 *
 * Un solo look, el de la referencia (Behance "Movie Mobile App"):
 * fondo oscuro con brillo neón rosa/teal, tarjetas oscuras, texto
 * blanco, botón principal con degradado rosa → teal. Sin selector
 * de temas, sin partículas, sin personalización de ningún tipo.
 * Sustituye a ThemeManager (borrado en v1.1.0).
 */
object AppStyle {

    const val BG_TOP = 0xFF160A24.toInt()
    const val BG_BOTTOM = 0xFF05060A.toInt()
    const val SURFACE = 0xFF1D1D2B.toInt()
    const val SURFACE_LIGHT = 0xFF2A2A3D.toInt()
    const val BORDER = 0xFF2E2E42.toInt()
    const val PINK = 0xFFFF2E88.toInt()
    const val TEAL = 0xFF00E5C7.toInt()
    const val TEXT = 0xFFFFFFFF.toInt()
    const val MUTED = 0xFF9A9AB0.toInt()
    const val GOLD = 0xFFFFC107.toInt()
    const val INK = 0xFF1A1A1A.toInt()

    /** Fondo de la pantalla: degradado oscuro de arriba a abajo. */
    fun background(): GradientDrawable = GradientDrawable(
        GradientDrawable.Orientation.TOP_BOTTOM,
        intArrayOf(BG_TOP, BG_BOTTOM)
    )

    /** Tarjeta oscura con borde fino. */
    fun card(radiusDp: Float = 20f, density: Float): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(SURFACE)
            cornerRadius = radiusDp * density
            setStroke((1f * density).toInt(), BORDER)
        }

    /** Superficie clara (filas seleccionadas, botones secundarios). */
    fun surfaceLight(radiusDp: Float = 16f, density: Float): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(SURFACE_LIGHT)
            cornerRadius = radiusDp * density
        }

    /** Píldora oscura (campos de texto). */
    fun pill(density: Float): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(SURFACE_LIGHT)
            cornerRadius = 28f * density
            setStroke((1f * density).toInt(), BORDER)
        }

    /** Botón principal: degradado neón rosa → teal. */
    fun gradientButton(density: Float): GradientDrawable =
        GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(PINK, TEAL)
        ).apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 28f * density
        }

    /** Chip pequeño (etiqueta de protocolo, insignia). */
    fun chip(density: Float): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(SURFACE_LIGHT)
            cornerRadius = 12f * density
        }
}
