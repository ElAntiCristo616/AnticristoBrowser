package com.elanticristo616.browser

import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.BitmapFactory
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.RadioButton
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.elanticristo616.browser.databinding.ActivityBrowserBinding
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * CuteNovna Browser v1.1.0: navegador mínimo estilo Brave (Opción B).
 *  - Barra superior delgada, WebView a pantalla completa, barra
 *    inferior delgada (atrás, adelante, transmitir, menú). Nada tapa
 *    la página: no hay hub gigante debajo del WebView.
 *  - Detección de video AGRUPADA: un mismo video sale como UNA
 *    tarjeta (sus variantes hls.m3u8, hls-360p, hls-480p, hls-720p
 *    y mp4 se fusionan por URL normalizada o por póster igual) y
 *    DENTRO van sus calidades. El video principal (el reproductor
 *    más grande/visible, o el que coincide con el título) va
 *    PRIMERO con su insignia dorada «Video principal». Los
 *    anuncios quedan fuera SIEMPRE (AdFilter: marcadores de URL,
 *    contenedores de anuncios, títulos de anuncio, videos ocultos
 *    o muy cortos < 10 s).
 *  - Al tocar una tarjeta queda marcada (borde dorado + ✓) y se
 *    despliegan sus calidades; al tocar una calidad queda marcada
 *    (radio lleno + fondo resaltado). Miniatura del póster y
 *    duración si se conocen.
 *  - Antes de transmitir muestra TODAS las calidades disponibles y
 *    deja elegir; por defecto va la MÁS ALTA, nunca la más baja.
 *  - Para enlaces de YouTube, TikTok, X, Instagram o Facebook se usa
 *    yt-dlp, que saca el video directo sin descargar nada.
 *  - Al empezar a transmitir se PAUSA el video del teléfono para que
 *    la tele no se pause sola, y se mandan SIEMPRE los headers del
 *    video (User-Agent, Referer, Origin) al receptor.
 *  - Reproductor nuevo (v1.1.0): barra de progreso ARRASTRABLE con
 *    el dedo, tiempo actual/total, play/pausa grande, saltos −10 s
 *    / +10 s, velocidad (solo Chromecast) y pantalla completa. Todo
 *    funciona en los 3 protocolos (Roku, Chromecast, DLNA) por
 *    UniversalCast; la posición se sondea cada 5 s. En DLNA solo se
 *    ofrecen calidades MP4.
 *  - TikTok: user-agent de Chrome móvil real (sin «wv»), cookies
 *    de terceros aceptadas y entrada por www.tiktok.com; los
 *    enlaces tiktok:// o intent:// se abren fuera o se ignoran.
 *  - Look fijo neón de la referencia (v1.1.0): SIN temas ni
 *    partículas. El menú ⋮ trae «Elegir tele» para cambiar de
 *    destino (Roku, Chromecast o DLNA).
 * Sin configuración: no hay menú ni pantalla de ajustes en la app y
 * el buscador es fijo DuckDuckGo. La página de ajustes del propio
 * DuckDuckGo (duckduckgo.com/settings*) está bloqueada: no se carga
 * y se avisa. Los inicios de sesión (p. ej. Gmail) NO se bloquean.
 *  - v1.1.4: bloqueo de anuncios casi total en 4 capas — lista de
 *    DuckDuckGo a nivel de petición (Blocklist), sin ventanas nuevas,
 *    veto a las redirecciones a dominios de anuncios al tocar, y JS
 *    que esconde botones falsos de play, overlays y contenedores de
 *    anuncios. El video principal, los hosts de video, el login y la
 *    red local (Cast/DLNA/Roku) no se tocan jamás.
 *  - v1.1.6: el botón de transmitir re-escanea la página al tocarlo
 *    (el reproductor puede poner el src tarde, al darle play) y el
 *    detector acepta las URLs estilo KVS (.../video_720p.mp4/ con
 *    barra final, ?file=...mp4&...). El listener anti-trampa del play
 *    se instala una sola vez por carga.
 */
class BrowserActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBrowserBinding
    private var client: RokuClient? = null
    private var pageTitle: String = ""
    private var currentUrl: String = ""
    private var castActive = false

    /** Sondeo de posición mientras se transmite (cada 5 s). */
    private var positionJob: Job? = null

    /** El usuario está arrastrando la barra de progreso. */
    private var seeking = false

    /** El usuario está arrastrando la barra del diálogo grande. */
    private var dialogSeeking = false

    /** Velocidad actual del reproductor (solo Chromecast la cambia). */
    private var currentSpeed = 1.0f

    /** ¿Suena ahora mismo lo que se transmite? */
    private var isPlayingNow = false

    /** Título de lo que se transmite (barra + diálogo). */
    private var currentTitle = "Video"

    /** Diálogo de pantalla completa del reproductor. */
    private var playerDialog: AlertDialog? = null
    private var dlgSeek: SeekBar? = null
    private var dlgTime: TextView? = null
    private var dlgStatus: TextView? = null
    private var dlgPlay: ImageButton? = null
    private var dlgSpeed: Button? = null

    /** Un video DISTINTO de la página: sus fuentes (variantes de
     *  calidad del mismo video) viven juntas en `sources`; sus
     *  calidades se leen al desplegar la tarjeta. */
    private data class DetectedVideo(
        val key: String,
        var title: String,
        var poster: String? = null,
        var durationSec: Double = -1.0,
        val sources: LinkedHashSet<String> = LinkedHashSet(),
        var qualities: List<YtDlpExtractor.QualityOption> = emptyList(),
        var resolved: Boolean = false,
        var selectedHeight: Int? = null,
        var expanded: Boolean = false,
        var isMain: Boolean = false,
        var selected: Boolean = false,
        var area: Double = 0.0,
        var visible: Boolean = false,
        /** Título real del video (yt-dlp). Si es false, el título es
         *  el de la página y solo vale para el video principal. */
        var hasRealTitle: Boolean = false
    ) {
        /** URL representativa: la maestra HLS si la hay, si no la
         *  primera fuente. */
        val url: String
            get() = sources.firstOrNull {
                val l = it.lowercase()
                l.contains("hls.m3u8") && !l.contains("-")
            } ?: sources.firstOrNull() ?: ""
    }

    /** Todos los videos reales de la página, en orden de aparición,
     *  ya agrupados por video (clave = URL normalizada). */
    private val detectedVideos = LinkedHashMap<String, DetectedVideo>()

    /** Hoja de videos (bottom sheet) y su contenido actual. */
    private var hojaVideos: BottomSheetDialog? = null
    private var hojaContainer: LinearLayout? = null
    private var hojaLista: List<DetectedVideo> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBrowserBinding.inflate(layoutInflater)
        setContentView(binding.root)
        aplicarEstilo()

        // Sin tele vinculada no hay navegador: de vuelta a la puerta.
        val target = UniversalCast.activeTarget()
        if (target == null) {
            Toast.makeText(
                this,
                "Primero conecta tu tele en la pantalla principal.",
                Toast.LENGTH_LONG
            ).show()
            finish()
            return
        }
        if (target is RokuCastTarget) {
            client = RokuClient(target.ip)
        }
        // Punto verde en el botón de transmitir: conectado a la tele.
        binding.dotConexion.visibility = View.VISIBLE
        binding.tvNavTv.text = target.protocol
        updateSpeedButton()

        binding.btnVolver.setOnClickListener { finish() }
        binding.btnIr.setOnClickListener { irA(binding.etDireccion.text.toString()) }
        binding.etDireccion.setOnEditorActionListener { _, _, _ ->
            irA(binding.etDireccion.text.toString()); true
        }
        binding.pillDetected.setOnClickListener { intentarTransmitir() }
        binding.btnPillTransmitir.setOnClickListener { intentarTransmitir() }
        binding.btnNavAtras.setOnClickListener {
            if (binding.webView.canGoBack()) binding.webView.goBack() else finish()
        }
        binding.btnNavAdelante.setOnClickListener {
            if (binding.webView.canGoForward()) binding.webView.goForward()
        }
        binding.btnNavTransmitir.setOnClickListener { intentarTransmitir() }
        binding.btnNavMenu.setOnClickListener { mostrarMenu() }
        binding.btnCastParar.setOnClickListener { comandoReceptor("stop") }
        binding.btnCastDesconectar.setOnClickListener { desconectarTele() }
        binding.btnPlayerPlay.setOnClickListener { togglePlayPause() }
        binding.btnSeekBack.setOnClickListener { saltar(-10) }
        binding.btnSeekFwd.setOnClickListener { saltar(10) }
        binding.btnCastVelocidad.setOnClickListener { cycleSpeed() }
        binding.btnCastPantalla.setOnClickListener { showPlayerDialog() }
        binding.seekCast.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(
                    seekBar: SeekBar?, progress: Int, fromUser: Boolean
                ) {
                    if (fromUser) {
                        binding.tvCastTime.text =
                            "${CastHelper.fmtTime(progress.toDouble())} / " +
                                CastHelper.fmtTime((seekBar?.max ?: 0).toDouble())
                    }
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {
                    seeking = true
                }

                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    seeking = false
                    val pos = seekBar?.progress ?: return
                    lifecycleScope.launch {
                        val res = UniversalCast.seekTo(pos)
                        if (binding.castBar.visibility == View.VISIBLE) {
                            binding.tvCastBarStatus.text = res.message
                        }
                        if (!res.ok) {
                            Toast.makeText(
                                this@BrowserActivity, res.message, Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            }
        )

        // v1.1.4: carga la lista de bloqueo de anuncios (asset).
        Blocklist.init(this)
        setupWebView()
        val startUrl = intent.getStringExtra("url") ?: "https://duckduckgo.com"
        if (isSettingsBloqueada(startUrl)) {
            avisoConfigBloqueada()
            binding.webView.loadUrl("https://duckduckgo.com")
            binding.etDireccion.setText("https://duckduckgo.com")
        } else {
            binding.webView.loadUrl(startUrl)
            binding.etDireccion.setText(startUrl)
        }
    }

    override fun onResume() {
        super.onResume()
        CastSession.onReceiverEvent = { event, body ->
            runOnUiThread {
                if (event == "position") {
                    val pos = CastSession.receiverPosition
                    val dur = CastSession.receiverDuration
                    if (dur > 0) {
                        binding.seekCast.max = dur.toInt()
                        if (!seeking) {
                            binding.seekCast.progress =
                                pos.toInt().coerceIn(0, dur.toInt())
                        }
                        binding.tvCastTime.text =
                            "${CastHelper.fmtTime(pos)} / ${CastHelper.fmtTime(dur)}"
                    }
                }
                CastHelper.describeReceiverEvent(event, body)?.let {
                    if (binding.castBar.visibility == View.VISIBLE) {
                        binding.tvCastBarStatus.text = it
                    }
                }
            }
        }
    }

    override fun onPause() {
        if (CastSession.onReceiverEvent != null) CastSession.onReceiverEvent = null
        super.onPause()
    }

    override fun onDestroy() {
        detenerSondeoPosicion()
        // v1.1.1: si se cierra el navegador sin transmisión activa,
        // soltar la sesión Cast para que el receptor salga del modo
        // Cast. Con transmisión activa NO se toca: la tele sigue
        // reproduciendo aunque se salga del navegador (al salir de
        // la app por completo lo cierra el onDestroy del portal).
        if (isFinishing && !castActive) UniversalCast.disconnectCastNow()
        super.onDestroy()
    }

    override fun onBackPressed() {
        if (binding.webView.canGoBack()) {
            binding.webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    // ---------- Estilo fijo neón (v1.1.0) ----------

    /** Paleta fija de la referencia: sin temas ni partículas. Casi
     *  todo el estilo ya vive en los layouts (XML); aquí solo van
     *  los tintes que no se pueden poner en XML. */
    private fun aplicarEstilo() {
        binding.seekCast.progressTintList = ColorStateList.valueOf(AppStyle.PINK)
        binding.seekCast.thumbTintList = ColorStateList.valueOf(AppStyle.PINK)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        // v1.0.4 — TikTok: user-agent de Chrome móvil REAL (sin el
        // «wv» del WebView, que TikTok bloquea o manda al muro de
        // login) y cookies de terceros aceptadas para que la web
        // cargue, se pueda navegar y no pida la app a cada rato.
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        binding.webView.apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            // v1.1.4: sin ventanas nuevas ni pop-ups de JavaScript.
            // Los pop-ups invasivos de las páginas de pelis/series
            // nacen de window.open: con esto no se abren.
            settings.javaScriptCanOpenWindowsAutomatically = false
            settings.setSupportMultipleWindows(false)
            settings.userAgentString = CHROME_MOBILE_UA
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            cookieManager.setAcceptThirdPartyCookies(this, true)
            webViewClient = object : WebViewClient() {
                /** La página de ajustes de DuckDuckGo no se carga. */
                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest
                ): Boolean {
                    val url = request.url.toString()
                    if (isSettingsBloqueada(url)) {
                        avisoConfigBloqueada()
                        return true
                    }
                    // TikTok (y otras apps) intentan abrir su app con
                    // esquemas propios: se abren fuera si hay app, y
                    // si no, se ignoran sin romper la navegación.
                    val scheme = request.url.scheme?.lowercase() ?: ""
                    if (scheme !in listOf("http", "https")) {
                        return try {
                            startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            )
                            true
                        } catch (e: Exception) {
                            true
                        }
                    }
                    // v1.1.4/v1.1.5: si al tocar algo la página
                    // quiere mandarnos a un dominio de anuncios/
                    // rastreadores (el salto a otra página con el
                    // pop-up invasivo), se veta y nos quedamos donde
                    // estábamos. v1.1.5: además se veta el salto a
                    // OTRO host cuando la URL huele a anuncio
                    // (marcadores de AdFilter); la navegación dentro
                    // del mismo host nunca se veta por marcadores, y
                    // la legítima (resultados, páginas de video,
                    // login) pasa.
                    val currentHost = try {
                        android.net.Uri.parse(currentUrl).host?.lowercase()
                    } catch (e: Exception) {
                        null
                    }
                    if (Blocklist.shouldVetoNavigation(url, currentHost)) {
                        if (request.isForMainFrame) {
                            Toast.makeText(
                                this@BrowserActivity,
                                "Anuncio bloqueado",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        return true
                    }
                    return false
                }

                override fun onPageStarted(
                    view: WebView,
                    url: String,
                    favicon: android.graphics.Bitmap?
                ) {
                    // Página nueva: se descartan los videos de la anterior.
                    detectedVideos.clear()
                    updatePill()
                    hojaVideos?.dismiss()
                }

                override fun onPageFinished(view: WebView, url: String) {
                    currentUrl = url
                    binding.etDireccion.setText(url)
                    scanPageForVideos()
                    // v1.1.4: esconder los anuncios de la página
                    // (botones falsos de play, overlays, contenedores
                    // de anuncios). Se repite con retraso porque
                    // estas páginas inyectan sus anuncios después de
                    // cargar. No toca el reproductor real ni el
                    // escaneo de videos, que ya corrió arriba.
                    ocultarAnuncios()
                    // v1.1.5: neutralizar la trampa del botón play
                    // (window.open anulado, clics en overlays que
                    // tapan el video reproducen el video real).
                    // Se repite con retraso junto con ocultarAnuncios
                    // porque estas páginas inyectan sus anuncios
                    // después de cargar.
                    neutralizarTrampa()
                    lifecycleScope.launch {
                        delay(1500)
                        if (!isFinishing) ocultarAnuncios()
                        if (!isFinishing) neutralizarTrampa()
                        // v1.1.6: el reproductor puede poner el src
                        // tarde (al darle play) o cambiarlo después
                        // de cargar: se re-escanea con retraso, como
                        // los anuncios, para no depender solo del
                        // escaneo de carga.
                        if (!isFinishing) scanPageForVideos()
                        delay(2500)
                        if (!isFinishing) ocultarAnuncios()
                        if (!isFinishing) neutralizarTrampa()
                        if (!isFinishing) scanPageForVideos()
                    }
                    // Si se había pausado el WebView al transmitir, se
                    // reanuda al cargar otra página.
                    view.onResume()
                }

                override fun shouldInterceptRequest(
                    view: WebView,
                    request: WebResourceRequest
                ): WebResourceResponse? {
                    val u = request.url.toString()
                    if (isDirectVideoUrl(u) && !isAdUrl(u)) {
                        runOnUiThread { onDirectVideoFound(u) }
                    }
                    // v1.1.4: bloqueo a nivel de petición. Los scripts
                    // de anuncios y rastreadores no llegan a cargar:
                    // el pop-up ni nace. Nunca el marco principal
                    // (eso lo veta shouldOverrideUrlLoading), nunca
                    // video/medios, nunca la red local ni los hosts
                    // de video y login (lo cuida Blocklist).
                    if (Blocklist.shouldBlockRequest(u, request.isForMainFrame)) {
                        return WebResourceResponse(
                            "text/plain", "utf-8",
                            java.io.ByteArrayInputStream(ByteArray(0))
                        )
                    }
                    return super.shouldInterceptRequest(view, request)
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onReceivedTitle(view: WebView, title: String?) {
                    pageTitle = title ?: ""
                }

                // v1.1.4: nunca abrir ventanas nuevas (pop-ups).
                override fun onCreateWindow(
                    view: WebView,
                    isDialog: Boolean,
                    isUserGesture: Boolean,
                    resultMsg: android.os.Message?
                ): Boolean = false
            }
        }
    }

    private fun irA(text: String) {
        val t = text.trim()
        if (t.isBlank()) return
        val url = when {
            t.startsWith("http://") || t.startsWith("https://") -> t
            t.contains(".") && !t.contains(" ") -> "https://$t"
            else -> "https://duckduckgo.com/?q=" +
                java.net.URLEncoder.encode(t, "UTF-8")
        }
        if (isSettingsBloqueada(url)) {
            avisoConfigBloqueada()
            return
        }
        binding.webView.loadUrl(url)
    }

    // ---------- Configuración bloqueada ----------

    /** ¿Es la página de ajustes de DuckDuckGo? (duckduckgo.com/settings*)
     *  Los inicios de sesión y el resto de páginas NO se bloquean. */
    private fun isSettingsBloqueada(url: String): Boolean {
        return try {
            val uri = android.net.Uri.parse(url)
            val host = (uri.host ?: "").lowercase()
            val isDdg = host == "duckduckgo.com" || host.endsWith(".duckduckgo.com")
            if (!isDdg) return false
            val path = uri.path ?: ""
            path == "/settings" || path.startsWith("/settings/") ||
                path.startsWith("/settings.") || path.startsWith("/settings?")
        } catch (e: Exception) {
            false
        }
    }

    private fun avisoConfigBloqueada() {
        Toast.makeText(this, "La configuración está bloqueada", Toast.LENGTH_SHORT).show()
    }

    // ---------- Filtro de anuncios ----------

    /** ¿La URL huele a anuncio? (AdFilter: marcadores de redes de
     *  publicidad, VAST, preroll/midroll y patrocinados.) */
    private fun isAdUrl(u: String): Boolean = AdFilter.isAdUrl(u)

    /** v1.1.4: esconde los anuncios de la página cargada (botones
     *  falsos de play, overlays, contenedores de anuncios). No toca
     *  el reproductor real (ver AdFilter.HIDE_ADS_JS). */
    private fun ocultarAnuncios() {
        try {
            binding.webView.evaluateJavascript(AdFilter.HIDE_ADS_JS, null)
        } catch (e: Exception) {
            // Si el JS falla, la página sigue igual: no se rompe nada.
        }
    }

    /** v1.1.5: neutraliza la trampa del botón play (ver
     *  AdFilter.TRAP_BLOCK_JS). No toca los controles reales del
     *  reproductor, el escaneo de videos ni el botón de transmitir. */
    private fun neutralizarTrampa() {
        try {
            binding.webView.evaluateJavascript(AdFilter.TRAP_BLOCK_JS, null)
        } catch (e: Exception) {
            // Si el JS falla, la página sigue igual: no se rompe nada.
        }
    }

    // ---------- Detección de video (agrupada) ----------

    /** ¿La URL apunta directo a un archivo o flujo de video?
     *  La forma la decide Blocklist: incluye las formas reales de
     *  los reproductores (KVS: .../video_720p.mp4/ con barra final,
     *  ?file=...mp4&...). */
    private fun isDirectVideoUrl(u: String): Boolean =
        Blocklist.isVideoFileUrl(u)

    /** Clave de grupo de una URL de video: quita la calidad
     *  (-360p, _720p…), el nombre de archivo genérico (hls.m3u8,
     *  video.mp4…) y las carpetas de formato/calidad (hls, mp4,
     *  720p…), para que todas las variantes del mismo video caigan
     *  en el mismo grupo. Sin host: el mismo video en HLS y en mp4
     *  suele venir de hosts distintos y debe fusionarse igual.
     *  Ej.: hls.m3u8, hls-360p, hls-480p, hls-720p y video.mp4 del
     *  mismo video → una sola clave «12/34/56». */
    private fun groupKeyFor(rawUrl: String): String {
        val lower = rawUrl.lowercase().substringBefore('?').substringBefore('#')
        val segs = lower.split('/').filter { it.isNotEmpty() && it != "." && it != ".." }
        val pathSegs = segs.drop(2) // esquema + host
        if (pathSegs.isEmpty()) return lower
        val genericFiles = setOf(
            "hls", "master", "playlist", "index", "video", "media",
            "file", "chunklist", "chunks"
        )
        val genericDirs = setOf(
            "hls", "mp4", "dash", "360p", "480p", "720p", "1080p",
            "144p", "240p", "2160p"
        )
        val dirs = pathSegs.dropLast(1).filter { it !in genericDirs }
        var stem = pathSegs.last()
            .substringBefore('.')
            .replace(Regex("[-_.]\\d{3,4}p$"), "")
        if (stem in genericFiles) stem = ""
        val tail = (dirs.takeLast(3) + listOf(stem)).filter { it.isNotEmpty() }
        return tail.joinToString("/").ifBlank { lower }
    }

    /** Grupo de una URL: primero se fusiona por póster igual (el
     *  mismo video en HLS y en mp4 con claves distintas), si no por
     *  clave de URL normalizada. */
    private fun groupFor(url: String, poster: String? = null): DetectedVideo {
        if (!poster.isNullOrBlank()) {
            detectedVideos.values.firstOrNull { it.poster == poster }?.let { g ->
                g.sources += url
                return g
            }
        }
        val key = groupKeyFor(url)
        return detectedVideos.getOrPut(key) {
            DetectedVideo(key = key, title = pageTitle.ifBlank { "Video" })
        }.also { it.sources += url }
    }

    private fun onDirectVideoFound(url: String) {
        groupFor(url)
        updatePill()
    }

    /** Un <video> de la página ya cargada, con sus datos para
     *  agrupar, ordenar y mostrar: fuentes, anuncio o no, duración,
     *  póster, tamaño en pantalla y si se ve. */
    private data class VideoElement(
        val srcs: List<String>,
        val ad: Boolean,
        val dur: Double,
        val poster: String,
        val w: Double,
        val h: Double,
        val vis: Boolean
    )

    /** Resultado del escaneo: cuántos <video> hay en la página,
     *  aunque no traigan URL directa (para distinguir «no hay
     *  video» de «hay video pero no se pudo detectar su enlace»). */
    private data class ScanResult(val videosEnPagina: Int)

    /** Busca los <video> de la página y los agrupa. El video
     *  PRINCIPAL es el visible más grande (el reproductor); si
     *  ninguno se ve, el más grande. Un reproductor con src blob
     *  (HLS por MSE, como el de xvideos) no trae URL directa: sus
     *  datos (póster, duración, principal) van al grupo con más
     *  fuentes, que es el video real (los anuncios traen una sola).
     *  Fuera: anuncios (contenedor o URL), videos ocultos o de
     *  menos de 10 s, y URLs blob:/data: como entradas (no se
     *  pueden transmitir).
     *
     *  v1.1.6: acepta un callback con el resultado (siempre se
     *  llama, también si la página no trae videos o el JS falla),
     *  para el re-escaneo bajo demanda del botón de transmitir. */
    private fun scanPageForVideos(onDone: ((ScanResult) -> Unit)? = null) {
        // v1.1.4: la detección de anuncios vive en un solo sitio
        // (AdFilter.AD_CHECK_JS), con las señales del reproductor de
        // YouTube incluidas: cuando suena el pre-roll, el <video>
        // queda dentro del contenedor `ad-showing` y se marca como
        // anuncio, así que no sale como tarjeta.
        val js = "(function(){" + AdFilter.AD_CHECK_JS +
            "var out=[];" +
            "document.querySelectorAll('video').forEach(function(el){" +
            "var r={w:0,h:0,y:0};" +
            "try{var b=el.getBoundingClientRect();r={w:b.width,h:b.height,y:b.top};}catch(e){}" +
            "var vis=r.w>0&&r.h>0&&r.y<(window.innerHeight||0)&&(r.y+r.h)>0;" +
            "var dur=-1;try{if(isFinite(el.duration))dur=el.duration;}catch(e){}" +
            "var srcs=[];" +
            "if(el.currentSrc)srcs.push(el.currentSrc);" +
            "if(el.src)srcs.push(el.src);" +
            "el.querySelectorAll('source').forEach(function(s){if(s.src)srcs.push(s.src);});" +
            "out.push({srcs:srcs,ad:esAnuncio(el),dur:dur,poster:el.poster||'',w:r.w,h:r.h,vis:vis});" +
            "});" +
            "return {total:document.querySelectorAll('video').length,items:out};})()"
        binding.webView.evaluateJavascript(js) { result ->
            try {
                val root = JSONObject(result)
                val total = root.optInt("total", 0)
                val arr = root.optJSONArray("items") ?: JSONArray()
                val elements = mutableListOf<VideoElement>()
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val srcs = mutableListOf<String>()
                    val sa = o.optJSONArray("srcs")
                    if (sa != null) {
                        for (j in 0 until sa.length()) {
                            val s = sa.optString(j, "")
                            if (s.isNotBlank()) srcs += s
                        }
                    }
                    val w = o.optDouble("w", 0.0)
                    val h = o.optDouble("h", 0.0)
                    // Oculto o píxel de seguimiento: fuera.
                    if (w < 2 || h < 2) continue
                    if (o.optBoolean("ad", false)) continue
                    val dur = o.optDouble("dur", -1.0)
                    // Muy corto: anuncio o vista previa, fuera.
                    if (dur > 0 && dur < 10) continue
                    elements += VideoElement(
                        srcs = srcs,
                        ad = false,
                        dur = dur,
                        poster = o.optString("poster", ""),
                        w = w, h = h,
                        vis = o.optBoolean("vis", false)
                    )
                }
                if (elements.isEmpty()) {
                    onDone?.invoke(ScanResult(total))
                    return@evaluateJavascript
                }
                val pool = elements.filter { it.vis }.ifEmpty { elements }
                val mainEl = pool.maxByOrNull { it.w * it.h }
                var cambio = false
                for (el in elements) {
                    val isMainEl = el === mainEl
                    var attached = false
                    for (src in el.srcs) {
                        if (!isDirectVideoUrl(src)) continue
                        if (isAdUrl(src)) continue
                        val group = groupFor(src, poster = el.poster)
                        if (el.poster.isNotBlank()) group.poster = el.poster
                        if (el.dur > 0) group.durationSec = el.dur
                        group.area = maxOf(group.area, el.w * el.h)
                        group.visible = group.visible || el.vis
                        if (isMainEl) group.isMain = true
                        attached = true
                        cambio = true
                    }
                    if (!attached && isMainEl) {
                        val target = detectedVideos.values
                            .filter { it.sources.isNotEmpty() }
                            .maxByOrNull { it.sources.size }
                        if (target != null) {
                            target.isMain = true
                            if (el.poster.isNotBlank()) target.poster = el.poster
                            if (el.dur > 0) target.durationSec = el.dur
                            target.area = maxOf(target.area, el.w * el.h)
                            cambio = true
                        }
                    }
                }
                // Solo un principal: se queda el de mayor área.
                val mains = detectedVideos.values.filter { it.isMain }
                if (mains.size > 1) {
                    val keep = mains.maxByOrNull { it.area }!!
                    mains.forEach { if (it !== keep) it.isMain = false }
                }
                if (cambio) updatePill()
                onDone?.invoke(ScanResult(total))
            } catch (e: Exception) {
                // La página no trajo videos directos: nada que mostrar
                onDone?.invoke(ScanResult(0))
            }
        }
    }

    /** La barrita píldora: el conteo de videos REALES de la página
     *  (grupos, no URLs). Sin videos reales, no aparece. */
    private fun updatePill() {
        val n = detectedVideos.size
        if (n == 0) {
            binding.pillDetected.visibility = View.GONE
        } else {
            binding.pillDetected.visibility = View.VISIBLE
            binding.tvPillText.text = if (n == 1) "1 video ▾" else "$n videos ▾"
        }
    }

    // ---------- Hoja de videos (bottom sheet) ----------

    /** Tocar transmitir (icono de Roku o píldora): si ya hay
     *  videos, abre la hoja; si no, re-escanea la página y espera
     *  el resultado antes de decir que no hay video. El
     *  reproductor puede poner el src tarde (al darle play) y el
     *  escaneo de carga ya pasó: sin este re-escaneo el botón
     *  miente. Si hay <video> pero sin enlace detectable, el aviso
     *  dice qué hacer en vez de afirmar que no hay video. */
    private fun intentarTransmitir() {
        if (detectedVideos.isNotEmpty()) {
            abrirHojaVideos()
            return
        }
        Toast.makeText(this, "Buscando video…", Toast.LENGTH_SHORT).show()
        var resuelto = false
        scanPageForVideos { res ->
            if (resuelto) return@scanPageForVideos
            resuelto = true
            if (detectedVideos.isNotEmpty()) {
                abrirHojaVideos()
            } else if (res.videosEnPagina > 0) {
                Toast.makeText(
                    this,
                    "Hay un video en la página pero no se pudo detectar " +
                        "su enlace. Dale play al video y vuelve a intentarlo.",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                Toast.makeText(
                    this,
                    "Conectado a ${UniversalCast.activeLabel()}: " +
                        "no hay video en esta página.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        // Seguridad: si el escaneo no responde en ~1.5 s, avisa
        // igual (evaluateJavascript puede fallar en silencio).
        lifecycleScope.launch {
            delay(1500)
            if (!resuelto) {
                resuelto = true
                if (detectedVideos.isNotEmpty()) {
                    abrirHojaVideos()
                } else {
                    Toast.makeText(
                        this@BrowserActivity,
                        "Conectado a ${UniversalCast.activeLabel()}: " +
                            "no hay video en esta página.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    /** Abre la hoja con la lista de videos. El video principal va
     *  PRIMERO; los demás después, en orden de aparición. Si se pasa
     *  un video (enlace pegado con yt-dlp), la hoja trae solo ese.
     *  Los anuncios nunca entran (título con pinta de anuncio). */
    private fun abrirHojaVideos(unVideo: DetectedVideo? = null) {
        val videos = (if (unVideo != null) listOf(unVideo)
        else detectedVideos.values.sortedWith(compareByDescending { it.isMain }))
            .filter { !AdFilter.isAdTitle(tituloPara(it)) }
        if (videos.isEmpty()) {
            Toast.makeText(this, "No hay video en esta página.", Toast.LENGTH_SHORT).show()
            return
        }
        hojaVideos?.dismiss()
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_videos, null)
        val density = resources.displayMetrics.density
        view.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(AppStyle.SURFACE)
            cornerRadii = floatArrayOf(
                24f * density, 24f * density, 24f * density, 24f * density,
                0f, 0f, 0f, 0f
            )
        }
        view.findViewById<TextView>(R.id.sheetTitle).setTextColor(AppStyle.TEXT)
        view.findViewById<TextView>(R.id.sheetSubtitle).setTextColor(AppStyle.MUTED)
        view.findViewById<Button>(R.id.btnSheetCerrar).apply {
            backgroundTintList = ColorStateList.valueOf(AppStyle.SURFACE_LIGHT)
            setTextColor(AppStyle.TEXT)
        }
        hojaContainer = view.findViewById(R.id.sheetVideoList)
        view.findViewById<Button>(R.id.btnSheetCerrar).setOnClickListener {
            dialog.dismiss()
        }
        dialog.setContentView(view)
        dialog.setOnDismissListener {
            hojaVideos = null
            hojaContainer = null
        }
        hojaVideos = dialog
        hojaLista = videos
        poblarHoja()
        dialog.show()
    }

    /** Arma la lista de la hoja desde el estado actual. */
    private fun poblarHoja() {
        val container = hojaContainer ?: return
        container.removeAllViews()
        for (video in hojaLista) {
            container.addView(crearItemVideo(video))
        }
    }

    /** Una tarjeta por video: miniatura del póster (si hay), título,
     *  duración y nº de calidades, insignia dorada si es el video
     *  principal, y ✓ + borde dorado si es el seleccionado. Al
     *  tocarla queda seleccionada y se despliegan SUS calidades. */
    private fun crearItemVideo(video: DetectedVideo): View {
        val density = resources.displayMetrics.density
        val gold = 0xFFF1CF75.toInt()
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(if (video.isMain) AppStyle.SURFACE_LIGHT else AppStyle.SURFACE)
                cornerRadius = 16f * density
                if (video.selected) {
                    setStroke((3f * density).toInt(), gold)
                } else if (video.isMain) {
                    setStroke((1f * density).toInt(), gold)
                } else {
                    setStroke((1f * density).toInt(), AppStyle.MUTED)
                }
            }
            setPadding(dp(12), dp(12), dp(12), dp(12))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8) }
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        // Miniatura del póster (o icono de tele si no hay).
        val thumb = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(72), dp(54))
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(AppStyle.SURFACE_LIGHT)
                cornerRadius = 8f * density
            }
            clipToOutline = true
            setImageResource(R.drawable.ic_tv)
            setColorFilter(AppStyle.PINK)
        }
        header.addView(thumb)
        val posterUrl = video.poster
        if (!posterUrl.isNullOrBlank()) cargarPoster(thumb, posterUrl)
        val textos = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
            ).apply { marginStart = dp(10) }
        }
        val tvTitulo = TextView(this).apply {
            text = tituloPara(video)
            setTextColor(AppStyle.TEXT)
            textSize = 14f
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        }
        textos.addView(tvTitulo)
        // Insignia del video principal.
        if (video.isMain) {
            val badge = TextView(this).apply {
                text = "★ Video principal"
                setTextColor(0xFF1A1A1A.toInt())
                textSize = 11f
                setPadding(dp(8), dp(3), dp(8), dp(3))
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    setColor(gold)
                    cornerRadius = 12f * density
                }
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(4) }
            }
            textos.addView(badge)
        }
        val tvSub = TextView(this).apply {
            text = subtituloPara(video)
            setTextColor(AppStyle.MUTED)
            textSize = 12f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setPadding(0, dp(4), 0, 0)
        }
        textos.addView(tvSub)
        header.addView(textos)
        // ✓ dorado cuando está seleccionada.
        val check = TextView(this).apply {
            text = if (video.selected) "✓" else ""
            setTextColor(gold)
            textSize = 22f
            setPadding(dp(6), 0, dp(2), 0)
        }
        header.addView(check)
        card.addView(header)

        header.setOnClickListener {
            val yaSeleccionada = video.selected
            hojaLista.forEach { it.selected = false }
            video.selected = true
            video.expanded = !yaSeleccionada || !video.expanded
            if (video.expanded && !video.resolved) {
                poblarHoja() // muestra "Leyendo calidades…" mientras tanto
                resolverCalidades(video) { poblarHoja() }
            } else {
                poblarHoja()
            }
        }

        if (video.expanded) {
            if (!video.resolved) {
                card.addView(TextView(this).apply {
                    text = "Leyendo calidades…"
                    setTextColor(AppStyle.MUTED)
                    textSize = 13f
                    setPadding(0, dp(10), 0, 0)
                })
            } else {
                val mayor = video.qualities.maxByOrNull { it.height }?.height
                for (opt in video.qualities) {
                    val elegida = opt.height == video.selectedHeight
                    val fila = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        background = if (elegida) {
                            GradientDrawable().apply {
                                shape = GradientDrawable.RECTANGLE
                                setColor(AppStyle.SURFACE_LIGHT)
                                cornerRadius = 8f * density
                            }
                        } else null
                        setPadding(dp(8), dp(8), dp(8), dp(8))
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        ).apply { topMargin = dp(4) }
                    }
                    val rb = RadioButton(this).apply {
                        isChecked = elegida
                        buttonTintList = ColorStateList.valueOf(AppStyle.PINK)
                        isClickable = false
                        isFocusable = false
                    }
                    fila.addView(rb)
                    fila.addView(TextView(this).apply {
                        text = opt.label +
                            if (opt.height == mayor && video.qualities.size > 1) {
                                " • Recomendado"
                            } else ""
                        setTextColor(AppStyle.TEXT)
                        textSize = 14f
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        ).apply { marginStart = dp(8) }
                    })
                    fila.setOnClickListener {
                        video.selectedHeight = opt.height
                        poblarHoja()
                    }
                    card.addView(fila)
                }
                val btn = Button(this).apply {
                    text = "Transmitir este video a la tele"
                    background = AppStyle.gradientButton(density)
                    setTextColor(0xFFFFFFFF.toInt())
                    textSize = 15f
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(48)
                    ).apply { topMargin = dp(10) }
                    setOnClickListener { transmitirVideo(video) }
                }
                card.addView(btn)
            }
        }
        return card
    }

    /** Título visible: el de la página para el principal, el real
     *  (yt-dlp) si lo hay, y la URL corta para los demás. */
    private fun tituloPara(video: DetectedVideo): String {
        return if (video.isMain && pageTitle.isNotBlank()) pageTitle
        else if (video.hasRealTitle && video.title.isNotBlank()) video.title
        else urlCorta(video.url)
    }

    /** Duración + nº de calidades (o «Toca para ver calidades»). */
    private fun subtituloPara(video: DetectedVideo): String {
        val partes = mutableListOf<String>()
        if (video.durationSec > 0) {
            partes += CastHelper.fmtTime(video.durationSec)
        }
        if (video.resolved) {
            val n = video.qualities.size
            partes += if (n == 1) "1 calidad" else "$n calidades"
        } else {
            partes += "Toca para ver calidades"
        }
        return partes.joinToString(" • ")
    }

    /** Carga el póster en la miniatura (sin librerías externas). */
    private fun cargarPoster(imageView: ImageView, url: String) {
        imageView.tag = url
        val ua = binding.webView.settings.userAgentString
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                conn.setRequestProperty("User-Agent", ua)
                conn.connect()
                val bmp = BitmapFactory.decodeStream(conn.inputStream)
                withContext(Dispatchers.Main) {
                    if (bmp != null && imageView.tag == url) {
                        imageView.setImageBitmap(bmp)
                        imageView.clearColorFilter()
                    }
                }
            } catch (e: Exception) {
                // Sin póster: se queda el icono de tele
            }
        }
    }

    // ---------- Selector de calidad ----------

    /** Calidades de un video AGRUPADO: si hay una maestra HLS se
     *  lee su manifiesto y salen TODAS sus variantes; si no, cada
     *  fuente (mp4…) es una calidad por su altura inferida. Sin
     *  duplicados por altura y de menor a mayor. */
    private fun resolverCalidades(video: DetectedVideo, alListo: () -> Unit) {
        val hlsMaster = video.sources.firstOrNull {
            it.lowercase().contains(".m3u8")
        }
        val cookie = try {
            CookieManager.getInstance().getCookie(video.url)
        } catch (e: Exception) {
            null
        }
        lifecycleScope.launch {
            val fromHls = withContext(Dispatchers.IO) {
                if (hlsMaster != null) {
                    HlsParser.parseVariants(
                        hlsMaster, referer = currentUrl, cookie = cookie
                    ).map {
                        YtDlpExtractor.QualityOption(it.height, it.url, "hls")
                    }
                } else {
                    emptyList()
                }
            }
            video.qualities = when {
                fromHls.isNotEmpty() -> fromHls
                    .groupBy { it.height }.map { (_, v) -> v.first() }
                    .sortedBy { it.height }
                video.sources.size > 1 -> video.sources.map {
                    YtDlpExtractor.QualityOption(
                        HlsParser.inferHeightFromUrl(it), it, fmtFor(it)
                    )
                }.groupBy { it.height }.map { (_, v) -> v.first() }
                    .sortedBy { it.height }
                else -> {
                    val h = HlsParser.inferHeightFromUrl(video.url)
                    listOf(YtDlpExtractor.QualityOption(h, video.url, fmtFor(video.url)))
                }
            }
            // DLNA solo reproduce MP4 directo: fuera HLS y DASH.
            if (UniversalCast.activeTarget() is DlnaCastTarget) {
                val mp4 = video.qualities.filter { it.fmt == "mp4" }
                if (mp4.isNotEmpty()) video.qualities = mp4
            }
            // Por defecto, la más alta
            video.selectedHeight = video.qualities.maxByOrNull { it.height }?.height
            video.resolved = true
            alListo()
        }
    }

    // ---------- Menú inferior: pegar enlace, elegir tele ----------

    private fun mostrarMenu() {
        val menu = PopupMenu(this, binding.btnNavMenu)
        menu.menu.add("Pegar enlace")
        menu.menu.add("Elegir tele")
        menu.menu.add("Recargar página")
        menu.menu.add("Ir a DuckDuckGo")
        menu.setOnMenuItemClickListener { item ->
            when (item.title.toString()) {
                "Pegar enlace" -> mostrarDialogoEnlace()
                "Elegir tele" -> abrirSelectorDestinos()
                "Recargar página" -> binding.webView.reload()
                "Ir a DuckDuckGo" -> irA("https://duckduckgo.com")
            }
            true
        }
        menu.show()
    }

    private fun mostrarDialogoEnlace() {
        val input = EditText(this).apply {
            hint = "Pega un enlace (YouTube, TikTok, X…)"
            inputType = InputType.TYPE_TEXT_VARIATION_URI
            setTextColor(AppStyle.TEXT)
            setHintTextColor(AppStyle.MUTED)
        }
        val contenedor = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(4))
            addView(
                input,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }
        AlertDialog.Builder(this)
            .setTitle("Transmitir enlace a la tele")
            .setView(contenedor)
            .setPositiveButton("Transmitir") { _, _ ->
                transmitirEnlace(input.text.toString())
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    /** Calidades de un enlace pegado (yt-dlp): se abre la hoja con
     *  ese video ya desplegado y sus calidades listas. */
    private fun transmitirEnlace(link: String) {
        val l = link.trim()
        if (l.isBlank()) {
            Toast.makeText(this, "Pega un enlace primero.", Toast.LENGTH_SHORT).show()
            return
        }
        Toast.makeText(this, "Extrayendo video…", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            try {
                val q = YtDlpExtractor.listQualities(this@BrowserActivity, l)
                // DLNA solo reproduce MP4 directo.
                val options = if (UniversalCast.activeTarget() is DlnaCastTarget) {
                    q.options.filter { it.fmt == "mp4" }.ifEmpty { q.options }
                } else {
                    q.options
                }
                val video = DetectedVideo(
                    key = groupKeyFor(l),
                    title = q.title,
                    sources = linkedSetOf(l),
                    qualities = options.sortedBy { it.height },
                    resolved = true,
                    selectedHeight = options.maxByOrNull { it.height }?.height,
                    expanded = true,
                    selected = true,
                    hasRealTitle = true
                )
                abrirHojaVideos(video)
            } catch (e: YtDlpExtractor.YtDlpException) {
                Toast.makeText(this@BrowserActivity, e.message, Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(
                    this@BrowserActivity,
                    "No pude sacar el video de ese enlace.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    // ---------- Transmitir ----------

    private fun fmtFor(url: String): String {
        val lower = url.lowercase()
        return when {
            lower.contains(".m3u8") -> "hls"
            lower.contains(".mpd") -> "dash"
            lower.contains(".mp3") -> "mp3"
            else -> "mp4"
        }
    }

    /** Headers del video: se mandan SIEMPRE al receptor. */
    private fun videoHeaders(): Map<String, String> {
        val headers = mutableMapOf<String, String>()
        headers["User-Agent"] = binding.webView.settings.userAgentString
        if (currentUrl.isNotBlank()) {
            headers["Referer"] = currentUrl
            try {
                val uri = android.net.Uri.parse(currentUrl)
                if (uri.scheme != null && uri.host != null) {
                    headers["Origin"] = "${uri.scheme}://${uri.host}"
                }
            } catch (e: Exception) {
            }
        }
        return headers
    }

    /** Pausa el video del teléfono antes de transmitir: si el WebView
     *  sigue reproduciendo, la tele se pausa sola. */
    private fun pausarVideoDelTelefono() {
        binding.webView.evaluateJavascript(
            "(function(){document.querySelectorAll('video').forEach(function(v){v.pause();});})()",
            null
        )
        binding.webView.onPause()
    }

    /** Transmite ESE video en la calidad elegida en la hoja, a la
     *  tele vinculada (Roku, Chromecast o DLNA). */
    private fun transmitirVideo(video: DetectedVideo) {
        val target = UniversalCast.activeTarget() ?: run {
            Toast.makeText(this, "Primero conecta una tele.", Toast.LENGTH_SHORT)
                .show()
            return
        }
        // DLNA solo reproduce MP4 directo.
        val opt = if (target is DlnaCastTarget) {
            video.qualities.firstOrNull { it.fmt == "mp4" }
                ?: YtDlpExtractor.pickOption(video.qualities, video.selectedHeight)
        } else {
            YtDlpExtractor.pickOption(video.qualities, video.selectedHeight)
        } ?: run {
            Toast.makeText(this, "Todavía no hay video para transmitir.", Toast.LENGTH_SHORT)
                .show()
            return
        }
        hojaVideos?.dismiss()
        pausarVideoDelTelefono()
        castActive = true
        isPlayingNow = true
        currentTitle = tituloPara(video)
        binding.tvPlayerTitle.text = currentTitle
        binding.castBar.visibility = View.VISIBLE
        binding.tvCastBarStatus.text = "Mandando video a la tele…"
        binding.seekCast.max = 0
        binding.seekCast.progress = 0
        binding.tvCastTime.text = "0:00 / 0:00"
        binding.btnPlayerPlay.setImageResource(R.drawable.ic_pause)
        updateSpeedButton()
        lifecycleScope.launch {
            val res = UniversalCast.castVideo(
                this@BrowserActivity, opt.url, tituloPara(video),
                opt.fmt, headers = videoHeaders()
            )
            binding.tvCastBarStatus.text = res.message
            if (!res.ok) {
                Toast.makeText(this@BrowserActivity, res.message, Toast.LENGTH_LONG).show()
                castActive = false
                binding.castBar.visibility = View.GONE
                detenerSondeoPosicion()
            } else {
                iniciarSondeoPosicion()
            }
        }
    }

    private fun comandoReceptor(cmd: String) {
        lifecycleScope.launch {
            val res = UniversalCast.command(cmd)
            if (binding.castBar.visibility == View.VISIBLE) {
                binding.tvCastBarStatus.text = res.message
            }
            if (!res.ok) {
                Toast.makeText(this@BrowserActivity, res.message, Toast.LENGTH_LONG).show()
            }
            when (cmd) {
                "pause" -> if (res.ok) {
                    isPlayingNow = false
                    binding.btnPlayerPlay.setImageResource(R.drawable.ic_play)
                    updateDialogPlayIcon()
                }
                "resume" -> if (res.ok) {
                    isPlayingNow = true
                    binding.btnPlayerPlay.setImageResource(R.drawable.ic_pause)
                    updateDialogPlayIcon()
                }
                "stop" -> if (res.ok) {
                    castActive = false
                    isPlayingNow = false
                    binding.castBar.visibility = View.GONE
                    detenerSondeoPosicion()
                    playerDialog?.dismiss()
                }
            }
        }
    }

    /** v1.1.1: «Desconectar» — para la reproducción, cierra la
     *  sesión Cast si la hay y olvida la tele vinculada, para que
     *  el receptor salga del modo Cast y no se quede en su pantalla
     *  idle. */
    private fun desconectarTele() {
        lifecycleScope.launch {
            UniversalCast.disconnectAll(this@BrowserActivity)
            castActive = false
            isPlayingNow = false
            detenerSondeoPosicion()
            playerDialog?.dismiss()
            binding.castBar.visibility = View.GONE
            binding.dotConexion.visibility = View.GONE
            binding.tvNavTv.text = "Sin tele"
            Toast.makeText(
                this@BrowserActivity, "Desconectado de la tele.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    /** Salto de ±10 s en lo que se transmite (cualquier protocolo). */
    private fun saltar(deltaSegundos: Int) {
        lifecycleScope.launch {
            val res = UniversalCast.seekBy(deltaSegundos)
            if (binding.castBar.visibility == View.VISIBLE) {
                binding.tvCastBarStatus.text = res.message
            }
            if (!res.ok) {
                Toast.makeText(this@BrowserActivity, res.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    /** Sondea la posición cada 5 s para la barra de progreso
     *  (funciona en los 3 protocolos: Roku, Chromecast y DLNA). */
    private fun iniciarSondeoPosicion() {
        detenerSondeoPosicion()
        positionJob = lifecycleScope.launch {
            while (isActive) {
                delay(5000)
                val state = try {
                    UniversalCast.playbackState()
                } catch (e: Exception) {
                    null
                }
                if (state != null && state.durationSec > 0) {
                    val pos = state.positionSec
                    val dur = state.durationSec
                    val time = "${CastHelper.fmtTime(pos)} / ${CastHelper.fmtTime(dur)}"
                    if (!seeking) {
                        binding.seekCast.max = dur.toInt()
                        binding.seekCast.progress = pos.toInt().coerceIn(0, dur.toInt())
                    }
                    binding.tvCastTime.text = time
                    if (!dialogSeeking) {
                        dlgSeek?.let {
                            it.max = dur.toInt()
                            it.progress = pos.toInt().coerceIn(0, dur.toInt())
                        }
                    }
                    dlgTime?.text = time
                    if (state.playing != isPlayingNow) {
                        isPlayingNow = state.playing
                        binding.btnPlayerPlay.setImageResource(
                            if (isPlayingNow) R.drawable.ic_pause else R.drawable.ic_play
                        )
                        updateDialogPlayIcon()
                    }
                }
            }
        }
    }

    // ---------- Reproductor nuevo (v1.1.0) ----------

    /** Play/pausa del reproductor (un solo botón grande). */
    private fun togglePlayPause() {
        comandoReceptor(if (isPlayingNow) "pause" else "resume")
    }

    /** Velocidad: 1× → 1.25× → 1.5× → 2× → 0.5× → 1×.
     *  Solo Chromecast la soporta; en Roku/DLNA el botón va
     *  apagado y no hace nada. */
    private fun cycleSpeed() {
        if (!UniversalCast.supportsSpeed()) {
            Toast.makeText(
                this,
                "Esta tele no permite cambiar la velocidad.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        val speeds = listOf(1.0f, 1.25f, 1.5f, 2.0f, 0.5f)
        val next = speeds[(speeds.indexOf(currentSpeed) + 1) % speeds.size]
        currentSpeed = next
        updateSpeedButton()
        lifecycleScope.launch {
            val res = UniversalCast.setSpeed(next)
            if (!res.ok) {
                Toast.makeText(this@BrowserActivity, res.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun updateSpeedButton() {
        val supported = UniversalCast.supportsSpeed()
        val label = when (currentSpeed) {
            1.25f -> "1.25×"
            1.5f -> "1.5×"
            2.0f -> "2×"
            0.5f -> "0.5×"
            else -> "1×"
        }
        binding.btnCastVelocidad.text = label
        binding.btnCastVelocidad.alpha = if (supported) 1.0f else 0.4f
        dlgSpeed?.text = label
        dlgSpeed?.alpha = if (supported) 1.0f else 0.4f
    }

    private fun updateDialogPlayIcon() {
        dlgPlay?.setImageResource(
            if (isPlayingNow) R.drawable.ic_pause else R.drawable.ic_play
        )
    }

    /** Pantalla completa: el mismo reproductor en un diálogo grande,
     *  con su propia barra arrastrable sincronizada con el sondeo. */
    private fun showPlayerDialog() {
        if (!castActive) {
            Toast.makeText(this, "Nada transmitiendo ahora.", Toast.LENGTH_SHORT).show()
            return
        }
        val view = layoutInflater.inflate(R.layout.dialog_player, null)
        val title = view.findViewById<TextView>(R.id.dlgPlayerTitle)
        val status = view.findViewById<TextView>(R.id.dlgPlayerStatus)
        val seek = view.findViewById<SeekBar>(R.id.dlgSeek)
        val time = view.findViewById<TextView>(R.id.dlgTime)
        val btnBack = view.findViewById<Button>(R.id.btnDlgBack)
        val btnPlay = view.findViewById<ImageButton>(R.id.btnDlgPlay)
        val btnFwd = view.findViewById<Button>(R.id.btnDlgFwd)
        val btnSpeed = view.findViewById<Button>(R.id.btnDlgSpeed)
        val btnStop = view.findViewById<Button>(R.id.btnDlgStop)
        val btnClose = view.findViewById<ImageButton>(R.id.btnDlgCerrar)
        title.text = currentTitle
        status.text = binding.tvCastBarStatus.text
        time.text = binding.tvCastTime.text
        seek.max = binding.seekCast.max
        seek.progress = binding.seekCast.progress
        seek.progressTintList = ColorStateList.valueOf(AppStyle.PINK)
        seek.thumbTintList = ColorStateList.valueOf(AppStyle.PINK)
        btnPlay.setImageResource(
            if (isPlayingNow) R.drawable.ic_pause else R.drawable.ic_play
        )
        btnSpeed.text = binding.btnCastVelocidad.text
        btnSpeed.alpha = binding.btnCastVelocidad.alpha
        dlgSeek = seek
        dlgTime = time
        dlgStatus = status
        dlgPlay = btnPlay
        dlgSpeed = btnSpeed
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                if (fromUser) {
                    time.text = "${CastHelper.fmtTime(p.toDouble())} / " +
                        CastHelper.fmtTime(seek.max.toDouble())
                }
            }

            override fun onStartTrackingTouch(s: SeekBar?) {
                dialogSeeking = true
            }

            override fun onStopTrackingTouch(s: SeekBar?) {
                dialogSeeking = false
                val pos = s?.progress ?: return
                lifecycleScope.launch { UniversalCast.seekTo(pos) }
            }
        })
        btnBack.setOnClickListener { saltar(-10) }
        btnFwd.setOnClickListener { saltar(10) }
        btnPlay.setOnClickListener { togglePlayPause() }
        btnSpeed.setOnClickListener { cycleSpeed() }
        btnStop.setOnClickListener { comandoReceptor("stop") }
        val dialog = AlertDialog.Builder(this)
            .setView(view)
            .create()
        btnClose.setOnClickListener { dialog.dismiss() }
        dialog.setOnDismissListener {
            playerDialog = null
            dlgSeek = null
            dlgTime = null
            dlgStatus = null
            dlgPlay = null
            dlgSpeed = null
        }
        playerDialog = dialog
        dialog.show()
    }

    /** Hoja para elegir a qué tele transmitir (Roku, Chromecast o
     *  DLNA). Al elegir, esa tele queda vinculada y guardada. */
    private fun abrirSelectorDestinos() {
        Toast.makeText(this, "Buscando teles…", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            val targets = DeviceDiscovery.discoverAll(this@BrowserActivity)
            DestinationPicker.show(
                this@BrowserActivity, targets,
                onPick = { target ->
                    // v1.1.1: soltar la sesión Cast anterior si se
                    // cambia a otra tele distinta.
                    UniversalCast.releasePreviousCast(target)
                    CastSession.activeTarget = target
                    TargetStore.save(this@BrowserActivity, target)
                    if (target is RokuCastTarget) {
                        CastSession.rokuIp = target.ip
                        CastSession.rokuName = target.name
                        client = RokuClient(target.ip)
                    } else {
                        client = null
                    }
                    binding.tvNavTv.text = target.protocol
                    binding.dotConexion.visibility = View.VISIBLE
                    updateSpeedButton()
                    Toast.makeText(
                        this@BrowserActivity,
                        "Tele vinculada: ${target.name} (${target.protocol}).",
                        Toast.LENGTH_LONG
                    ).show()
                },
                onSearch = { abrirSelectorDestinos() }
            )
        }
    }

    private fun detenerSondeoPosicion() {
        positionJob?.cancel()
        positionJob = null
    }

    // ---------- Utilidades ----------

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    /** Host + último tramo de la URL, para identificar el video. */
    private fun urlCorta(url: String): String {
        return try {
            val uri = android.net.Uri.parse(url)
            val host = uri.host ?: return url.take(60)
            val tail = (uri.path ?: "").substringAfterLast('/').takeIf { it.isNotBlank() }
            if (tail == null) host else "$host/…/$tail"
        } catch (e: Exception) {
            url.take(60)
        }
    }

    companion object {
        /** User-agent de Chrome móvil real (v1.0.4): sin el «wv» del
         *  WebView, que TikTok bloquea o manda al muro de login. */
        const val CHROME_MOBILE_UA =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
    }
}
