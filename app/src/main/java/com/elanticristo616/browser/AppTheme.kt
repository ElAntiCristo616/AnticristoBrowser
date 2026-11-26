package com.elanticristo616.browser

import android.content.Context
import android.graphics.drawable.GradientDrawable
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

/**
 * CuteNovna Browser v1.0.4 — TEMAS PERSONALIZABLES.
 *
 * 13 temas guardados en SharedPreferences ("cutenovna_theme",
 * clave "theme_id"; por defecto "navidad"). Cada tema cambia la
 * paleta COMPLETA (fondo degradado, superficies, acento, texto,
 * color apagado) Y el tipo de partícula animada del fondo
 * (nieve, pétalos, hojas, burbujas, brasas, lluvia, estrellas,
 * corazones, destellos, polvo dorado, o ninguna).
 *
 * Se aplica al portal, a la puerta y al chrome del navegador
 * (barra superior, barra inferior, barrita de videos, barrita de
 * transmisión y hoja de videos). La guirnalda navideña solo se
 * muestra en el tema Navidad: en los demás se esconde.
 *
 * El selector vive aquí para usarlo igual desde el portal (botón
 * «Tema») y desde el menú ⋮ del navegador («Tema»): al elegir se
 * guarda y se recrea la actividad para aplicar el tema entero.
 */
object ThemeManager {

    enum class ParticleKind {
        NONE, SNOW, PETALS, LEAVES, BUBBLES, EMBERS,
        RAIN, STARS, HEARTS, SPARKLES, GOLD_DUST
    }

    data class BrowserTheme(
        val id: String,
        val name: String,
        /** Degradado de fondo: arriba → abajo. */
        val bgTop: Int,
        val bgBottom: Int,
        /** Tarjetas, barras y hoja de videos. */
        val surface: Int,
        /** Botones secundarios (Pausa, Seguir, Parar, Cerrar…). */
        val surfaceLight: Int,
        /** Botones principales (Ir, Transmitir) y detalles. */
        val accent: Int,
        val text: Int,
        val muted: Int,
        val particles: ParticleKind,
        /** Color base de la partícula (cada tipo lo matiza). */
        val particleColor: Int,
        /** ¿Se muestra la guirnalda de foquitos? Solo Navidad. */
        val garland: Boolean
    )

    val THEMES = listOf(
        BrowserTheme(
            "navidad", "Navidad",
            0xFF0E3426.toInt(), 0xFF04140E.toInt(),
            0xFF14291F.toInt(), 0xFF1A4A33.toInt(),
            0xFFCD2D32.toInt(), 0xFFFFFFFF.toInt(), 0xFFA8C3B5.toInt(),
            ParticleKind.SNOW, 0xFFFFFFFF.toInt(), true
        ),
        BrowserTheme(
            "sakura", "Sakura",
            0xFF4A2138.toInt(), 0xFF1F0E18.toInt(),
            0xFF3A1A2C.toInt(), 0xFF5C2C46.toInt(),
            0xFFFF7BAC.toInt(), 0xFFFFFFFF.toInt(), 0xFFF2B8D0.toInt(),
            ParticleKind.PETALS, 0xFFFFB7C9.toInt(), false
        ),
        BrowserTheme(
            "invierno", "Invierno",
            0xFF1B3A5C.toInt(), 0xFF0A1B2E.toInt(),
            0xFF16324E.toInt(), 0xFF24486B.toInt(),
            0xFF7FC4FF.toInt(), 0xFFFFFFFF.toInt(), 0xFFB9D6F2.toInt(),
            ParticleKind.SNOW, 0xFFFFFFFF.toInt(), false
        ),
        BrowserTheme(
            "oceano", "Océano",
            0xFF0B3D5C.toInt(), 0xFF04263E.toInt(),
            0xFF0D3249.toInt(), 0xFF14506E.toInt(),
            0xFF29B6F6.toInt(), 0xFFFFFFFF.toInt(), 0xFF9FD8F0.toInt(),
            ParticleKind.BUBBLES, 0xFFBFE9FF.toInt(), false
        ),
        BrowserTheme(
            "galaxia", "Galaxia",
            0xFF2A1650.toInt(), 0xFF0D0620.toInt(),
            0xFF221240.toInt(), 0xFF3A2560.toInt(),
            0xFFB388FF.toInt(), 0xFFFFFFFF.toInt(), 0xFFCBB8F5.toInt(),
            ParticleKind.STARS, 0xFFFFFFFF.toInt(), false
        ),
        BrowserTheme(
            "bosque", "Bosque",
            0xFF1E4D2B.toInt(), 0xFF0A2413.toInt(),
            0xFF173D22.toInt(), 0xFF27603A.toInt(),
            0xFF81C784.toInt(), 0xFFFFFFFF.toInt(), 0xFFA9CBA9.toInt(),
            ParticleKind.LEAVES, 0xFFA5D6A7.toInt(), false
        ),
        BrowserTheme(
            "fuego", "Fuego",
            0xFF5C1A08.toInt(), 0xFF260A03.toInt(),
            0xFF4A1506.toInt(), 0xFF7A2410.toInt(),
            0xFFFF7043.toInt(), 0xFFFFFFFF.toInt(), 0xFFFFB59E.toInt(),
            ParticleKind.EMBERS, 0xFFFFB74D.toInt(), false
        ),
        BrowserTheme(
            "atardecer", "Atardecer",
            0xFF8A3B2E.toInt(), 0xFF33141F.toInt(),
            0xFF6B2E26.toInt(), 0xFF96483A.toInt(),
            0xFFFFB74D.toInt(), 0xFFFFFFFF.toInt(), 0xFFF5C6A8.toInt(),
            ParticleKind.GOLD_DUST, 0xFFFFD54F.toInt(), false
        ),
        BrowserTheme(
            "neon", "Neón",
            0xFF0A0A12.toInt(), 0xFF000000.toInt(),
            0xFF12121E.toInt(), 0xFF1E1E30.toInt(),
            0xFF00E5FF.toInt(), 0xFFFFFFFF.toInt(), 0xFF9A9AB5.toInt(),
            ParticleKind.SPARKLES, 0xFF00E5FF.toInt(), false
        ),
        BrowserTheme(
            "otono", "Otoño",
            0xFF5C3A17.toInt(), 0xFF241503.toInt(),
            0xFF4A2E12.toInt(), 0xFF6B4420.toInt(),
            0xFFFF9800.toInt(), 0xFFFFFFFF.toInt(), 0xFFD9B380.toInt(),
            ParticleKind.LEAVES, 0xFFFFB74D.toInt(), false
        ),
        BrowserTheme(
            "dulce", "Dulce",
            0xFF6B2D4D.toInt(), 0xFF2E1122.toInt(),
            0xFF57233E.toInt(), 0xFF7A3A5C.toInt(),
            0xFFFF80AB.toInt(), 0xFFFFFFFF.toInt(), 0xFFF3B8D2.toInt(),
            ParticleKind.HEARTS, 0xFFFF8FB3.toInt(), false
        ),
        BrowserTheme(
            "lluvia", "Lluvia",
            0xFF37474F.toInt(), 0xFF141E24.toInt(),
            0xFF2A363E.toInt(), 0xFF455A64.toInt(),
            0xFF64B5F6.toInt(), 0xFFFFFFFF.toInt(), 0xFFA9C3D4.toInt(),
            ParticleKind.RAIN, 0xFFB0D4F0.toInt(), false
        ),
        BrowserTheme(
            "claro", "Claro",
            0xFFF7F7F2.toInt(), 0xFFE3E6DF.toInt(),
            0xFFFFFFFF.toInt(), 0xFFE8EDE9.toInt(),
            0xFFCD2D32.toInt(), 0xFF1A1A1A.toInt(), 0xFF5A6B60.toInt(),
            ParticleKind.NONE, 0xFFFFFFFF.toInt(), false
        )
    )

    private const val PREFS = "cutenovna_theme"
    private const val KEY_ID = "theme_id"

    /** Tema guardado (o Navidad si no hay ninguno). */
    fun current(context: Context): BrowserTheme {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val id = prefs.getString(KEY_ID, "navidad")
        return THEMES.firstOrNull { it.id == id } ?: THEMES[0]
    }

    fun save(context: Context, id: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_ID, id).apply()
    }

    /** Degradado de fondo del tema, para la raíz de cada pantalla. */
    fun gradient(theme: BrowserTheme): GradientDrawable =
        GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(theme.bgTop, theme.bgBottom)
        )

    /** Superficie redondeada (barras, barritas, tarjetas) del tema. */
    fun roundedSurface(theme: BrowserTheme, radiusDp: Float = 16f, density: Float): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(theme.surface)
            cornerRadius = radiusDp * density
            setStroke((1f * density).toInt(), theme.muted)
        }

    /** Píldora del color de acento (botones Ir / Transmitir). */
    fun accentPill(theme: BrowserTheme, density: Float): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(theme.accent)
            cornerRadius = 24f * density
        }

    /** Selector de temas: lista con los 13 nombres; al elegir se
     *  guarda y se recrea la actividad para aplicar el tema entero. */
    fun showPicker(activity: AppCompatActivity) {
        val current = current(activity)
        val names = THEMES.map { it.name }.toTypedArray()
        val checked = THEMES.indexOfFirst { it.id == current.id }.coerceAtLeast(0)
        AlertDialog.Builder(activity)
            .setTitle("Tema")
            .setSingleChoiceItems(names, checked) { dialog, which ->
                save(activity, THEMES[which].id)
                dialog.dismiss()
                activity.recreate()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}
