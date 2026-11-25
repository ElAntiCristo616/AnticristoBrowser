package com.elanticristo616.browser

import android.content.Context
import android.content.Intent
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
 * Anticristo Browser v1.0.1 — PANTALLA 2: EL PORTAL.
 *
 * Solo se entra aquí después de conectar la tele en la puerta
 * (MainActivity). Lobby de DuckDuckGo: buscador en píldora blanca
 * con botón rojo «Ir», accesos rápidos con los 5 iconos de marca
 * REALES incluidos como drawables dentro de la app (sin favicons
 * de DuckDuckGo) y barra inferior Inicio / Buscar / Cast.
 *
 * Los accesos rápidos abren el sitio en el navegador (WebView);
 * para la tele solo se usa el canal receptor WVC 259656, nada más.
 *
 * «TV: <ip> · Cambiar» vuelve a la puerta para meter otra IP; la
 * IP guardada se reemplaza al conectar con la nueva.
 */
class PortalActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPortalBinding

    /** Nombre, icono (drawable de la app) y dominio de cada acceso rápido. */
    private val quickSites = listOf(
        Triple("YouTube", R.drawable.ic_brand_youtube, "youtube.com"),
        Triple("TikTok", R.drawable.ic_brand_tiktok, "tiktok.com"),
        Triple("Instagram", R.drawable.ic_brand_instagram, "instagram.com"),
        Triple("X", R.drawable.ic_brand_x, "x.com"),
        Triple("Facebook", R.drawable.ic_brand_facebook, "facebook.com")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPortalBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Sin tele conectada no hay portal: de vuelta a la puerta.
        val ip = CastSession.rokuIp
        if (ip == null) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }
        binding.tvTvInfo.text = "TV: $ip"

        // Accesos rápidos
        for ((name, iconRes, domain) in quickSites) {
            binding.quickRow.addView(quickItem(name, iconRes, domain))
        }

        // Buscador
        binding.btnBuscarWeb.setOnClickListener { buscar(binding.etBuscar.text.toString()) }
        binding.etBuscar.setOnEditorActionListener { _, _, _ ->
            buscar(binding.etBuscar.text.toString()); true
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
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 13f
            gravity = Gravity.CENTER
        }
        col.addView(icon)
        col.addView(label)
        col.setOnClickListener { abrirNavegador("https://$domain") }
        return col
    }
}
