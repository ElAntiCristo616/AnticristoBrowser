package com.elanticristo616.browser

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.elanticristo616.browser.databinding.ActivityPortalBinding

/**
 * CuteNovna Browser v1.0.4 — PANTALLA 2: EL PORTAL.
 *
 * Solo se entra aquí después de poner la IP de la tele en la puerta
 * (MainActivity). Lobby de DuckDuckGo: buscador en píldora blanca
 * con botón rojo «Ir», accesos rápidos con los 5 iconos de marca
 * REALES incluidos como drawables dentro de la app (sin favicons
 * de DuckDuckGo) y barra inferior Inicio / Buscar / Cast.
 *
 * Los accesos rápidos abren el sitio en el navegador (WebView);
 * para la tele solo se usa el canal receptor WVC 259656, nada más.
 * TikTok entra por www.tiktok.com (con el user-agent de Chrome
 * móvil real del navegador, la web carga y se navega).
 *
 * «TV: <ip> · Cambiar» vuelve a la puerta para meter otra IP; la
 * IP guardada se reemplaza al conectar con la nueva.
 *
 * Temas (v1.0.4): paleta + partículas del tema guardado; el botón
 * «Tema» abre el selector con los 13 temas.
 */
class PortalActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPortalBinding
    private lateinit var theme: ThemeManager.BrowserTheme

    /** Nombre, icono (drawable de la app) y dominio de cada acceso rápido. */
    private val quickSites = listOf(
        Triple("YouTube", R.drawable.ic_brand_youtube, "youtube.com"),
        Triple("TikTok", R.drawable.ic_brand_tiktok, "www.tiktok.com"),
        Triple("Instagram", R.drawable.ic_brand_instagram, "instagram.com"),
        Triple("X", R.drawable.ic_brand_x, "x.com"),
        Triple("Facebook", R.drawable.ic_brand_facebook, "facebook.com")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPortalBinding.inflate(layoutInflater)
        setContentView(binding.root)
        theme = ThemeManager.current(this)
        aplicarTema()

        // Sin tele conectada no hay portal: de vuelta a la puerta.
        val ip = CastSession.rokuIp
        if (ip == null) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }
        binding.tvTvInfo.text = "TV: $ip"

        // v1.0.2: si se entró aunque la tele no respondió al chequeo
        // de la puerta, el aviso queda visible en el portal.
        val warning = CastSession.tvWarning
            ?: intent.getStringExtra("tv_warning")
        if (warning != null) {
            binding.tvTvWarning.text = warning
            binding.tvTvWarning.visibility = View.VISIBLE
        } else {
            binding.tvTvWarning.visibility = View.GONE
        }

        // Accesos rápidos
        for ((name, iconRes, domain) in quickSites) {
            binding.quickRow.addView(quickItem(name, iconRes, domain))
        }

        // Buscador
        binding.btnBuscarWeb.setOnClickListener { buscar(binding.etBuscar.text.toString()) }
        binding.etBuscar.setOnEditorActionListener { _, _, _ ->
            buscar(binding.etBuscar.text.toString()); true
        }

        // Selector de temas
        binding.btnTema.setOnClickListener {
            ThemeManager.showPicker(this)
        }

        // Cambiar de tele: vuelve a la puerta con la IP ya escrita.
        binding.tvCambiar.setOnClickListener {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .putExtra("change_tv", true)
            )
            finish()
        }

        binding.btnAcercaDe.setOnClickListener {
            val name = CastSession.rokuName ?: "Roku TV"
            AlertDialog.Builder(this)
                .setTitle("Acerca de")
                .setMessage(
                    getString(R.string.about) +
                        "\n\nTV conectada: $name ($ip)"
                )
                .setPositiveButton("Cerrar", null)
                .show()
        }

        // Barra inferior
        binding.navInicio.setOnClickListener {
            binding.scrollPrincipal.smoothScrollTo(0, 0)
        }
        binding.navBuscar.setOnClickListener {
            binding.etBuscar.requestFocus()
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(binding.etBuscar, InputMethodManager.SHOW_IMPLICIT)
        }
        binding.navCast.setOnClickListener {
            if (CastSession.rokuIp == null) {
                Toast.makeText(
                    this,
                    "Primero conecta tu tele en la puerta.",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                abrirNavegador("https://duckduckgo.com")
            }
        }
    }

    /** Paleta + partículas del tema guardado en todo el portal. */
    private fun aplicarTema() {
        val density = resources.displayMetrics.density
        binding.root.background = ThemeManager.gradient(theme)
        binding.particleView.setTheme(theme.particles, theme.particleColor)
        binding.garlandView.visibility =
            if (theme.garland) View.VISIBLE else View.GONE
        binding.tvTitulo.setTextColor(theme.text)
        binding.tvSubtitulo.setTextColor(theme.muted)
        binding.tvTvInfo.setTextColor(theme.muted)
        binding.tvCambiar.setTextColor(theme.accent)
        binding.tvDuck.setTextColor(theme.text)
        binding.tvAccesos.setTextColor(theme.text)
        binding.btnBuscarWeb.backgroundTintList =
            ColorStateList.valueOf(theme.accent)
        binding.btnTema.text = "Tema: ${theme.name}"
        binding.btnTema.backgroundTintList =
            ColorStateList.valueOf(theme.surfaceLight)
        binding.btnTema.setTextColor(theme.text)
        binding.btnAcercaDe.backgroundTintList =
            ColorStateList.valueOf(theme.surfaceLight)
        binding.btnAcercaDe.setTextColor(theme.text)
        binding.navBar.background = ThemeManager.roundedSurface(theme, 18f, density)
        binding.tvNavInicio.setTextColor(theme.text)
        binding.tvNavBuscar.setTextColor(theme.muted)
        binding.tvNavCast.setTextColor(theme.accent)
    }

    override fun onDestroy() {
        super.onDestroy()
        // Al salir del portal se apaga el servidor local y se olvida
        // el receptor (no hay transmisión activa desde el portal).
        if (isFinishing) CastSession.reset()
    }

    // ---------- Navegador ----------

    private fun buscar(text: String) {
        val t = text.trim()
        if (t.isBlank()) return
        val url = when {
            t.startsWith("http://") || t.startsWith("https://") -> t
            t.contains(".") && !t.contains(" ") -> "https://$t"
            else -> "https://duckduckgo.com/?q=" +
                java.net.URLEncoder.encode(t, "UTF-8")
        }
        abrirNavegador(url)
    }

    private fun abrirNavegador(url: String) {
        startActivity(
            Intent(this, BrowserActivity::class.java).putExtra("url", url)
        )
    }

    private fun quickItem(name: String, iconRes: Int, domain: String): View {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val icon = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(144, 144)
            scaleType = ImageView.ScaleType.FIT_CENTER
            setImageResource(iconRes)
        }
        val label = TextView(this).apply {
            text = name
            setTextColor(theme.text)
            textSize = 13f
            gravity = Gravity.CENTER
        }
        col.addView(icon)
        col.addView(label)
        col.setOnClickListener { abrirNavegador("https://$domain") }
        return col
    }
}
