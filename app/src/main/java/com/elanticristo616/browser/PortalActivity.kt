package com.elanticristo616.browser

import android.content.Context
import android.content.Intent
import android.graphics.drawable.GradientDrawable
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
import androidx.lifecycle.lifecycleScope
import com.elanticristo616.browser.databinding.ActivityPortalBinding
import kotlinx.coroutines.launch

/**
 * CuteNovna Browser v1.1.0 — PANTALLA 2: EL LOBBY.
 *
 * Solo se entra aquí después de vincular una tele (MainActivity):
 * Roku, Chromecast o DLNA. Look de la referencia (Behance "Movie
 * Mobile App"): saludo grande, barra para pegar el link, botón
 * grande «Transmitir», filas de tarjetas y navegación inferior con
 * botón central +. Contenido NUESTRO, sin réplica: nada de
 * onboarding VR, nada de «Sign up», nada de pósters ni datos falsos
 * de películas.
 *
 * - Link: se pega el link del video y «Transmitir» lo manda a la
 *   tele vinculada (video directo, YouTube por DIAL en Roku, o
 *   yt-dlp en la calidad más alta). Si yt-dlp no puede con el
 *   link, se abre en el navegador para elegir el video ahí.
 * - Favoritos: sitios guardados (estrella para agregar/quitar) con
 *   su icono oficial; tocar uno lo transmite.
 * - Historial: links recientes con fecha y hora; tocar uno lo
 *   reabre, ✕ borra esa entrada, «Limpiar» borra todo.
 * - CERO accesos directos fijos (los 5 shortcuts se eliminaron).
 * - «Cambiar» vuelve a la puerta para vincular otra tele.
 * - Sin temas ni personalización: un solo look fijo.
 *
 * La navegación DuckDuckGo sigue sin tema, como siempre.
 */
class PortalActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPortalBinding

    private enum class Screen { INICIO, FAVORITOS, HISTORIAL }

    private var currentScreen = Screen.INICIO

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPortalBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Sin tele vinculada no hay lobby: de vuelta a la puerta.
        val target = CastSession.activeTarget
        if (target == null) {
            // Compatibilidad: una Roku guardada por IP.
            val ip = CastSession.rokuIp
            if (ip == null) {
                startActivity(Intent(this, MainActivity::class.java))
                finish()
                return
            }
            CastSession.activeTarget = RokuCastTarget(ip, CastSession.rokuName ?: "Roku TV")
        }
        updateTvCard()

        // Aviso si se entró aunque la tele no respondió al chequeo
        // de la puerta.
        val warning = CastSession.tvWarning
            ?: intent.getStringExtra("tv_warning")
        if (warning != null) {
            binding.tvTvWarning.text = warning
            binding.tvTvWarning.visibility = View.VISIBLE
        } else {
            binding.tvTvWarning.visibility = View.GONE
        }

        // Pegar link + Transmitir
        binding.btnTransmitir.setOnClickListener { transmitirLink() }
        binding.etLink.setOnEditorActionListener { _, _, _ ->
            transmitirLink(); true
        }

        // Favoritos
        binding.tvAgregarFav.setOnClickListener { agregarFavorito() }
        refreshFavorites()

        // Historial
        binding.tvLimpiarHistorial.setOnClickListener {
            LibraryStore.clearHistory(this)
            refreshHistory()
            Toast.makeText(this, "Historial limpio.", Toast.LENGTH_SHORT).show()
        }
        refreshHistory()

        // Cambiar de tele: vuelve a la puerta (IP o buscar teles).
        binding.btnCambiarTv.setOnClickListener {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .putExtra("change_tv", true)
            )
            finish()
        }

        binding.btnAcercaDe.setOnClickListener {
            val t = CastSession.activeTarget
            val name = t?.name ?: "Sin tele"
            val detail = if (t is RokuCastTarget) t.ip else t?.protocol ?: ""
            AlertDialog.Builder(this)
                .setTitle("Acerca de")
                .setMessage(
                    getString(R.string.about) +
                        "\n\nTV vinculada: $name ($detail)"
                )
                .setPositiveButton("Cerrar", null)
                .show()
        }

        // Barra inferior estilo referencia.
        binding.navInicio.setOnClickListener { showScreen(Screen.INICIO) }
        binding.navNavegador.setOnClickListener {
            abrirNavegador("https://duckduckgo.com")
        }
        binding.navCentral.setOnClickListener {
            showScreen(Screen.INICIO)
            binding.scrollPrincipal.smoothScrollTo(0, 0)
            binding.etLink.requestFocus()
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(binding.etLink, InputMethodManager.SHOW_IMPLICIT)
        }
        binding.navFavoritos.setOnClickListener { showScreen(Screen.FAVORITOS) }
        binding.navHistorial.setOnClickListener { showScreen(Screen.HISTORIAL) }
    }

    override fun onResume() {
        super.onResume()
        updateTvCard()
        refreshFavorites()
        refreshHistory()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Al salir del lobby se apaga el servidor local y se olvida
        // el receptor (no hay transmisión activa desde el lobby).
        if (isFinishing) CastSession.reset()
    }

    /** Tarjeta «TV vinculada: <nombre> • <protocolo/IP>». */
    private fun updateTvCard() {
        val target = CastSession.activeTarget ?: return
        binding.tvTvNombre.text = target.name
        binding.tvTvDetalle.text = when (target) {
            is RokuCastTarget -> "Roku • ${target.ip}"
            is GoogleCastTarget -> "Chromecast • misma red Wi-Fi"
            is DlnaCastTarget -> "DLNA • ${target.device.ip}"
            else -> target.protocol
        }
    }

    // ---------- Pantallas de la barra inferior ----------

    private fun showScreen(screen: Screen) {
        currentScreen = screen
        val linkPill = binding.etLink.parent as View
        val favHeader = binding.tvFavoritos.parent as View
        val histHeader = binding.tvHistorial.parent as View
        when (screen) {
            Screen.INICIO -> {
                binding.tvSaludo.text = "¿Qué quieres ver hoy?"
                binding.tvSubSaludo.text = "Pega un link y mándalo a tu tele"
                binding.cardTv.visibility = View.VISIBLE
                linkPill.visibility = View.VISIBLE
                binding.btnTransmitir.visibility = View.VISIBLE
                favHeader.visibility = View.VISIBLE
                binding.rowFavoritos.parent?.let {
                    (it as View).visibility = View.VISIBLE
                }
                binding.tvFavoritosVacio.visibility =
                    if (LibraryStore.favorites(this).isEmpty()) View.VISIBLE else View.GONE
                histHeader.visibility = View.VISIBLE
                binding.listaHistorial.visibility = View.VISIBLE
                binding.tvHistorialVacio.visibility =
                    if (LibraryStore.history(this).isEmpty()) View.VISIBLE else View.GONE
                binding.btnAcercaDe.visibility = View.VISIBLE
            }
            Screen.FAVORITOS -> {
                binding.tvSaludo.text = "Favoritos"
                binding.tvSubSaludo.text = "Toca uno para transmitirlo"
                binding.cardTv.visibility = View.GONE
                linkPill.visibility = View.GONE
                binding.btnTransmitir.visibility = View.GONE
                favHeader.visibility = View.VISIBLE
                binding.rowFavoritos.parent?.let {
                    (it as View).visibility = View.VISIBLE
                }
                binding.tvFavoritosVacio.visibility =
                    if (LibraryStore.favorites(this).isEmpty()) View.VISIBLE else View.GONE
                histHeader.visibility = View.GONE
                binding.listaHistorial.visibility = View.GONE
                binding.tvHistorialVacio.visibility = View.GONE
                binding.btnAcercaDe.visibility = View.GONE
            }
            Screen.HISTORIAL -> {
                binding.tvSaludo.text = "Historial"
                binding.tvSubSaludo.text = "Toca un link para volver a transmitirlo"
                binding.cardTv.visibility = View.GONE
                linkPill.visibility = View.GONE
                binding.btnTransmitir.visibility = View.GONE
                favHeader.visibility = View.GONE
                binding.rowFavoritos.parent?.let {
                    (it as View).visibility = View.GONE
                }
                binding.tvFavoritosVacio.visibility = View.GONE
                histHeader.visibility = View.VISIBLE
                binding.listaHistorial.visibility = View.VISIBLE
                binding.tvHistorialVacio.visibility =
                    if (LibraryStore.history(this).isEmpty()) View.VISIBLE else View.GONE
                binding.btnAcercaDe.visibility = View.GONE
            }
        }
        binding.scrollPrincipal.smoothScrollTo(0, 0)
        updateNavColors()
    }

    private fun updateNavColors() {
        val active = AppStyle.TEXT
        val inactive = AppStyle.MUTED
        binding.tvNavInicio.setTextColor(
            if (currentScreen == Screen.INICIO) active else inactive
        )
        binding.tvNavFavoritos.setTextColor(
            if (currentScreen == Screen.FAVORITOS) active else inactive
        )
        binding.tvNavHistorial.setTextColor(
            if (currentScreen == Screen.HISTORIAL) active else inactive
        )
    }

    // ---------- Transmitir un link ----------

    /** Transmite el link del campo grande. */
    private fun transmitirLink() {
        val link = binding.etLink.text.toString().trim()
        if (link.isBlank()) {
            Toast.makeText(this, "Pega un link primero.", Toast.LENGTH_SHORT).show()
            return
        }
        if (!link.startsWith("http://") && !link.startsWith("https://")) {
            Toast.makeText(
                this,
                "Ese link no se ve bien, tiene que empezar por https://",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        val target = UniversalCast.activeTarget()
        if (target == null) {
            Toast.makeText(this, "Primero conecta una tele.", Toast.LENGTH_LONG).show()
            return
        }
        // Video o audio directo: va directo a la tele.
        val lower = link.lowercase().substringBefore('?').substringBefore('#')
        if (lower.endsWith(".mp4") || lower.endsWith(".mp3") ||
            lower.endsWith(".m3u8") || lower.endsWith(".mpd")
        ) {
            transmitirDirecto(link)
            return
        }
        // YouTube en Roku: DIAL abre la app de YouTube en la tele.
        val ytId = RokuClient.extractYouTubeId(link)
        if (ytId != null && target is RokuCastTarget) {
            binding.btnTransmitir.isEnabled = false
            lifecycleScope.launch {
                val code = try {
                    RokuClient(target.ip).launchYouTube(ytId)
                } catch (e: Exception) {
                    -1
                }
                binding.btnTransmitir.isEnabled = true
                if (code in 200..299) {
                    LibraryStore.addHistory(this@PortalActivity, link)
                    refreshHistory()
                    Toast.makeText(
                        this@PortalActivity,
                        "Abriendo YouTube en la tele…",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    transmitirConYtDlp(link)
                }
            }
            return
        }
        transmitirConYtDlp(link)
    }

    /** Video directo a la tele vinculada (cualquier protocolo). */
    private fun transmitirDirecto(link: String) {
        val fmt = when {
            link.lowercase().contains(".m3u8") -> "hls"
            link.lowercase().contains(".mpd") -> "dash"
            link.lowercase().contains(".mp3") -> "mp3"
            else -> "mp4"
        }
        binding.btnTransmitir.isEnabled = false
        lifecycleScope.launch {
            val res = UniversalCast.castVideo(
                this@PortalActivity, link, "Video", fmt
            )
            binding.btnTransmitir.isEnabled = true
            Toast.makeText(this@PortalActivity, res.message, Toast.LENGTH_LONG).show()
            if (res.ok) {
                LibraryStore.addHistory(this@PortalActivity, link)
                refreshHistory()
            }
        }
    }

    /** Saca el video del link con yt-dlp y lo manda a la tele.
     *  En Roku pasa por CastHelper.castLink (canal WVC); en
     *  Chromecast/DLNA, por la URL directa de yt-dlp. Si yt-dlp no
     *  puede con el link, se abre en el navegador para elegir el
     *  video ahí. */
    private fun transmitirConYtDlp(link: String) {
        val target = UniversalCast.activeTarget() ?: return
        binding.btnTransmitir.isEnabled = false
        Toast.makeText(this, "Extrayendo video…", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            try {
                if (target is RokuCastTarget) {
                    val res = CastHelper.castLink(
                        this@PortalActivity, RokuClient(target.ip), link,
                        onProgress = { }
                    )
                    binding.btnTransmitir.isEnabled = true
                    Toast.makeText(this@PortalActivity, res.message, Toast.LENGTH_LONG).show()
                    if (res.ok) {
                        LibraryStore.addHistory(this@PortalActivity, link)
                        refreshHistory()
                    } else {
                        abrirNavegador(link)
                    }
                    return@launch
                }
                val qualities = YtDlpExtractor.listQualities(this@PortalActivity, link)
                val options = if (target is DlnaCastTarget) {
                    // DLNA solo reproduce MP4 directo.
                    qualities.options.filter { it.fmt == "mp4" }
                        .ifEmpty { qualities.options }
                } else {
                    qualities.options
                }
                val opt = YtDlpExtractor.pickOption(options, null)
                    ?: throw YtDlpExtractor.YtDlpException(
                        "Ese enlace no trae un video que la tele pueda reproducir."
                    )
                val res = UniversalCast.castVideo(
                    this@PortalActivity, opt.url, qualities.title, opt.fmt
                )
                binding.btnTransmitir.isEnabled = true
                Toast.makeText(this@PortalActivity, res.message, Toast.LENGTH_LONG).show()
                if (res.ok) {
                    LibraryStore.addHistory(this@PortalActivity, link)
                    refreshHistory()
                }
            } catch (e: YtDlpExtractor.YtDlpException) {
                binding.btnTransmitir.isEnabled = true
                Toast.makeText(this@PortalActivity, e.message, Toast.LENGTH_LONG).show()
                abrirNavegador(link)
            } catch (e: Exception) {
                binding.btnTransmitir.isEnabled = true
                Toast.makeText(
                    this@PortalActivity,
                    "No pude sacar el video de ese enlace. Abriendo en el navegador…",
                    Toast.LENGTH_LONG
                ).show()
                abrirNavegador(link)
            }
        }
    }

    // ---------- Favoritos ----------

    /** Agrega el link del campo como favorito (nombre = el sitio). */
    private fun agregarFavorito() {
        val link = binding.etLink.text.toString().trim()
        if (link.isBlank() ||
            (!link.startsWith("http://") && !link.startsWith("https://"))
        ) {
            Toast.makeText(
                this,
                "Pega primero el link en el campo de arriba.",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        LibraryStore.addFavorite(this, "", link)
        refreshFavorites()
        Toast.makeText(this, "Agregado a favoritos.", Toast.LENGTH_SHORT).show()
    }

    /** Tarjetas horizontales de favoritos: icono oficial + nombre.
     *  Tocar transmite; mantener presionado quita el favorito. */
    private fun refreshFavorites() {
        val favs = LibraryStore.favorites(this)
        binding.rowFavoritos.removeAllViews()
        binding.tvFavoritosVacio.visibility =
            if (favs.isEmpty()) View.VISIBLE else View.GONE
        for (fav in favs) {
            val density = resources.displayMetrics.density
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    setColor(AppStyle.SURFACE)
                    cornerRadius = 16f * density
                }
                setPadding(dp(14), dp(14), dp(14), dp(10))
                layoutParams = LinearLayout.LayoutParams(
                    dp(104), LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = dp(10) }
            }
            val icon = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
                scaleType = ImageView.ScaleType.FIT_CENTER
                setImageResource(LibraryStore.brandIconRes(fav.brand))
            }
            val label = TextView(this).apply {
                text = fav.name
                setTextColor(AppStyle.TEXT)
                textSize = 13f
                gravity = Gravity.CENTER
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(8) }
            }
            card.addView(icon)
            card.addView(label)
            card.setOnClickListener {
                binding.etLink.setText(fav.url)
                transmitirLink()
            }
            card.setOnLongClickListener {
                LibraryStore.removeFavorite(this, fav.url)
                refreshFavorites()
                Toast.makeText(this, "Quitado de favoritos.", Toast.LENGTH_SHORT).show()
                true
            }
            binding.rowFavoritos.addView(card)
        }
    }

    // ---------- Historial ----------

    /** Filas de historial: link + fecha y hora. Tocar reabre el
     *  link; ✕ borra esa entrada. */
    private fun refreshHistory() {
        val history = LibraryStore.history(this)
        binding.listaHistorial.removeAllViews()
        binding.tvHistorialVacio.visibility =
            if (history.isEmpty()) View.VISIBLE else View.GONE
        for (entry in history) {
            val density = resources.displayMetrics.density
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    setColor(AppStyle.SURFACE)
                    cornerRadius = 12f * density
                }
                setPadding(dp(12), dp(10), dp(12), dp(10))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(8) }
            }
            val textos = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                )
            }
            val tvLink = TextView(this).apply {
                text = entry.url
                setTextColor(AppStyle.TEXT)
                textSize = 13f
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            }
            val tvFecha = TextView(this).apply {
                text = LibraryStore.formatTime(entry.timeMs)
                setTextColor(AppStyle.MUTED)
                textSize = 11f
            }
            textos.addView(tvLink)
            textos.addView(tvFecha)
            val btnBorrar = TextView(this).apply {
                text = "✕"
                setTextColor(AppStyle.MUTED)
                textSize = 18f
                setPadding(dp(10), dp(4), dp(4), dp(4))
            }
            btnBorrar.setOnClickListener {
                LibraryStore.removeHistory(this, entry.url)
                refreshHistory()
            }
            row.addView(textos)
            row.addView(btnBorrar)
            row.setOnClickListener {
                binding.etLink.setText(entry.url)
                transmitirLink()
            }
            binding.listaHistorial.addView(row)
        }
    }

    // ---------- Navegador ----------

    private fun abrirNavegador(url: String) {
        startActivity(
            Intent(this, BrowserActivity::class.java).putExtra("url", url)
        )
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
