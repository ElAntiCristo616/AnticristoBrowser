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
import java.net.HttpURLConnection
import java.net.URL

/**
 * CuteNovna Browser v1.0.4: navegador mínimo estilo Brave (Opción B).
 *  - Barra superior delgada, WebView a pantalla completa, barra
 *    inferior delgada (atrás, adelante, transmitir, menú). Nada tapa
 *    la página: no hay hub gigante debajo del WebView.
 *  - Detección de video AGRUPADA: un mismo video sale como UNA
 *    tarjeta (sus variantes hls.m3u8, hls-360p, hls-480p, hls-720p
 *    y mp4 se fusionan por URL normalizada o por póster igual) y
 *    DENTRO van sus calidades. El video principal (el reproductor
 *    más grande/visible, o el que coincide con el título) va
 *    PRIMERO con su insignia dorada «Video principal». Los
 *    anuncios quedan fuera: marcadores de URL, contenedores de
 *    anuncios, videos ocultos o muy cortos (< 10 s).
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
 *  - Controles del reproductor mientras se transmite (barrita de
 *    transmisión): Pausa / Seguir / Parar + saltos −10 s / +10 s y
 *    barra de progreso. Los saltos usan el protocolo del canal
 *    (positionSet, exacto) con respaldo ECP Fwd/Rew; la posición
 *    llega por los eventos «position» del canal y se sondea cada
 *    5 s. Solo se puede probar en la tele real.
 *  - TikTok: user-agent de Chrome móvil real (sin «wv»), cookies
 *    de terceros aceptadas y entrada por www.tiktok.com; los
 *    enlaces tiktok:// o intent:// se abren fuera o se ignoran.
 *  - Temas: paleta + partículas del tema guardado (ThemeManager);
 *    el selector está en el menú ⋮ («Tema»).
 * Sin configuración: no hay menú ni pantalla de ajustes en la app y
 * el buscador es fijo DuckDuckGo. La página de ajustes del propio
 * DuckDuckGo (duckduckgo.com/settings*) está bloqueada: no se carga
 * y se avisa. Los inicios de sesión (p. ej. Gmail) NO se bloquean.
 */
class BrowserActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBrowserBinding
    private lateinit var theme: ThemeManager.BrowserTheme
    private var client: RokuClient? = null
    private var pageTitle: String = ""
    private var currentUrl: String = ""
    private var castActive = false

    /** Sondeo de posición mientras se transmite (cada 5 s). */
    private var positionJob: Job? = null

    /** El usuario está arrastrando la barra de progreso. */
    private var seeking = false

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
        theme = ThemeManager.current(this)
        aplicarTema()

        val ip = CastSession.rokuIp
        if (ip == null) {
            Toast.makeText(
                this,
                "Primero conecta tu tele en la pantalla principal.",
                Toast.LENGTH_LONG
            ).show()
            finish()
            return
        }
        client = RokuClient(ip)
        // Punto verde en el botón de transmitir: conectado a la tele.
        binding.dotConexion.visibility = View.VISIBLE

        binding.btnVolver.setOnClickListener { finish() }
        binding.btnIr.setOnClickListener { irA(binding.etDireccion.text.toString()) }
        binding.etDireccion.setOnEditorActionListener { _, _, _ ->
            irA(binding.etDireccion.text.toString()); true
        }
        binding.pillDetected.setOnClickListener { abrirHojaVideos() }
        binding.btnPillTransmitir.setOnClickListener { abrirHojaVideos() }
        binding.btnNavAtras.setOnClickListener {
            if (binding.webView.canGoBack()) binding.webView.goBack() else finish()
        }
        binding.btnNavAdelante.setOnClickListener {
            if (binding.webView.canGoForward()) binding.webView.goForward()
        }
        binding.btnNavTransmitir.setOnClickListener {
            if (detectedVideos.isNotEmpty()) {
                abrirHojaVideos()
            } else {
                Toast.makeText(
                    this,
                    "Conectado a ${CastSession.rokuName ?: "Roku TV"}: " +
                        "no hay video en esta página.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        binding.btnNavMenu.setOnClickListener { mostrarMenu() }
        binding.btnCastPausa.setOnClickListener { comandoReceptor("pause") }
        binding.btnCastSeguir.setOnClickListener { comandoReceptor("resume") }
        binding.btnCastParar.setOnClickListener { comandoReceptor("stop") }
        binding.btnSeekBack.setOnClickListener { saltar(-10) }
        binding.btnSeekFwd.setOnClickListener { saltar(10) }
        binding.seekCast.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(
                    seekBar: SeekBar?, progress: Int, fromUser: Boolean
                ) {
                    if (fromUser) {
                        binding.tvCastTime.text =
                            "${CastHelper.fmtTime(progress.toDouble())} / " +
                                CastHelper.fmtTime(CastSession.receiverDuration)
                    }
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {
                    seeking = true
                }

                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    seeking = false
                    val pos = seekBar?.progress ?: return
                    lifecycleScope.launch {
                        val res = CastHelper.receiverSeekTo(pos)
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
        super.onDestroy()
    }

    override fun onBackPressed() {
        if (binding.webView.canGoBack()) {
            binding.webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    // ---------- Tema ----------

    /** Paleta + partículas del tema guardado en todo el chrome. */
    private fun aplicarTema() {
        val density = resources.displayMetrics.density
        binding.root.background = ThemeManager.gradient(theme)
        binding.particleView.setTheme(theme.particles, theme.particleColor)
        binding.btnVolver.backgroundTintList = ColorStateList.valueOf(theme.surfaceLight)
        binding.btnVolver.setTextColor(theme.text)
        binding.etDireccion.background =
            ThemeManager.roundedSurface(theme, 16f, density)
        binding.etDireccion.setTextColor(theme.text)
        binding.etDireccion.setHintTextColor(theme.muted)
        binding.btnIr.backgroundTintList = ColorStateList.valueOf(theme.accent)
        binding.btnIr.setTextColor(0xFFFFFFFF.toInt())
        binding.navBar.setBackgroundColor(theme.surface)
        binding.btnNavAtras.imageTintList = ColorStateList.valueOf(theme.muted)
        binding.btnNavAdelante.imageTintList = ColorStateList.valueOf(theme.muted)
        binding.btnNavTransmitir.imageTintList = ColorStateList.valueOf(theme.text)
        binding.tvNavTv.setTextColor(theme.muted)
        binding.btnNavMenu.imageTintList = ColorStateList.valueOf(theme.muted)
        binding.pillDetected.background =
            ThemeManager.roundedSurface(theme, 16f, density)
        binding.tvPillText.setTextColor(theme.text)
        binding.btnPillTransmitir.background = ThemeManager.accentPill(theme, density)
        binding.castBar.background =
            ThemeManager.roundedSurface(theme, 16f, density)
        binding.tvCastBarStatus.setTextColor(theme.muted)
        binding.tvCastTime.setTextColor(theme.muted)
        for (b in listOf(
            binding.btnCastPausa, binding.btnCastSeguir, binding.btnCastParar,
            binding.btnSeekBack, binding.btnSeekFwd
        )) {
            b.backgroundTintList = ColorStateList.valueOf(theme.surfaceLight)
            b.setTextColor(theme.text)
        }
        binding.seekCast.progressTintList = ColorStateList.valueOf(theme.accent)
        binding.seekCast.thumbTintList = ColorStateList.valueOf(theme.accent)
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
                    return super.shouldInterceptRequest(view, request)
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onReceivedTitle(view: WebView, title: String?) {
                    pageTitle = title ?: ""
                }
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

    /** Marcadores de publicidad en URLs de video: redes de anuncios,
     *  servidores de ads, VAST y preroll/midroll. Nunca se usa "ad"
     *  solo: rompería URLs normales ("load", "download"...). */
    private val AD_URL_MARKERS = listOf(
        "doubleclick", "googlesyndication", "googleads", "imasdk", "ima3",
        "vast", "vpaid", "preroll", "midroll", "postroll", "adserver",
        "adservice", "adsense", "adtech", "advert", "sponsor", "criteo",
        "taboola", "outbrain", "adnxs", "moatads", "adbreak", "ads.",
        ".ads.", "/ads/", "pubmatic", "rubiconproject", "smartadserver",
        "adform", "flashtalking", "exoclick", "adcash", "popads",
        "trafficjunky", "juicyads", "adsterra", "propellerads",
        "hilltopads", "clickadu", "popunder", "popup", "banner",
        "promo", "teaser", "prebid", "dfp", "adroll", "adsystem"
    )

    /** ¿La URL huele a anuncio? */
    private fun isAdUrl(u: String): Boolean {
        val lower = u.lowercase()
        return AD_URL_MARKERS.any { lower.contains(it) }
    }

    // ---------- Detección de video (agrupada) ----------

    /** ¿La URL apunta directo a un archivo o flujo de video? */
    private fun isDirectVideoUrl(u: String): Boolean {
        val lower = u.lowercase()
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) return false
        val noQuery = lower.substringBefore('?').substringBefore('#')
        return noQuery.endsWith(".mp4") || noQuery.endsWith(".m3u8") ||
            noQuery.endsWith(".mpd") || noQuery.endsWith(".mov") ||
            noQuery.endsWith(".mkv") || noQuery.endsWith(".webm") ||
            lower.contains(".mp4?") || lower.contains(".m3u8") ||
            lower.contains(".mpd?") || lower.contains("media=hls")
    }

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

    /** Busca los <video> de la página y los agrupa. El video
     *  PRINCIPAL es el visible más grande (el reproductor); si
     *  ninguno se ve, el más grande. Un reproductor con src blob
     *  (HLS por MSE, como el de xvideos) no trae URL directa: sus
     *  datos (póster, duración, principal) van al grupo con más
     *  fuentes, que es el video real (los anuncios traen una sola).
     *  Fuera: anuncios (contenedor o URL), videos ocultos o de
     *  menos de 10 s, y URLs blob:/data: como entradas (no se
     *  pueden transmitir). */
    private fun scanPageForVideos() {
        val js = "(function(){" +
            "function clsStr(el){" +
            "var c='';" +
            "try{c=(el.className&&el.className.baseVal!==undefined)?el.className.baseVal:(el.className||'');}catch(e){}" +
            "return (''+c+' '+(el.id||'')).toLowerCase();" +
            "}" +
            "function esAnuncio(el){" +
            "var n=el,d=0;" +
            "while(n&&d<6){" +
            "var s=clsStr(n);" +
            "if(/(^|[\\s\\-_])(ad|ads|advert|advertisement|sponsor|preroll|midroll|postroll|banner|promo|teaser|popup|popunder)([\\s\\-_]|$)/.test(s))return true;" +
            "if(n.tagName==='IFRAME'){" +
            "var src=(n.src||'').toLowerCase();" +
            "if(src.indexOf('doubleclick')>=0||src.indexOf('googlesyndication')>=0||src.indexOf('ads')>=0||src.indexOf('vast')>=0||src.indexOf('exoclick')>=0||src.indexOf('popads')>=0)return true;" +
            "}" +
            "n=n.parentElement;d++;" +
            "}" +
            "return false;" +
            "}" +
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
            "return out;})()"
        binding.webView.evaluateJavascript(js) { result ->
            try {
                val arr = JSONArray(result)
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
                if (elements.isEmpty()) return@evaluateJavascript
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
            } catch (e: Exception) {
                // La página no trajo videos directos: nada que mostrar
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

    /** Abre la hoja con la lista de videos. El video principal va
     *  PRIMERO; los demás después, en orden de aparición. Si se pasa
     *  un video (enlace pegado con yt-dlp), la hoja trae solo ese. */
    private fun abrirHojaVideos(unVideo: DetectedVideo? = null) {
        val videos = if (unVideo != null) listOf(unVideo)
        else detectedVideos.values.sortedWith(compareByDescending { it.isMain })
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
            setColor(theme.surface)
            cornerRadii = floatArrayOf(
                24f * density, 24f * density, 24f * density, 24f * density,
                0f, 0f, 0f, 0f
            )
        }
        view.findViewById<TextView>(R.id.sheetTitle).setTextColor(theme.text)
        view.findViewById<TextView>(R.id.sheetSubtitle).setTextColor(theme.muted)
        view.findViewById<Button>(R.id.btnSheetCerrar).apply {
            backgroundTintList = ColorStateList.valueOf(theme.surfaceLight)
            setTextColor(theme.text)
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
                setColor(if (video.isMain) theme.surfaceLight else theme.surface)
                cornerRadius = 16f * density
                if (video.selected) {
                    setStroke((3f * density).toInt(), gold)
                } else if (video.isMain) {
                    setStroke((1f * density).toInt(), gold)
                } else {
                    setStroke((1f * density).toInt(), theme.muted)
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
                setColor(theme.surfaceLight)
                cornerRadius = 8f * density
            }
            clipToOutline = true
            setImageResource(R.drawable.ic_tv)
            setColorFilter(theme.accent)
        }
        header.addView(thumb)
        if (!video.poster.isNullOrBlank()) cargarPoster(thumb, video.poster)
        val textos = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
            ).apply { marginStart = dp(10) }
        }
        val tvTitulo = TextView(this).apply {
            text = tituloPara(video)
            setTextColor(theme.text)
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
            setTextColor(theme.muted)
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
                    setTextColor(theme.muted)
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
                                setColor(theme.surfaceLight)
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
                        buttonTintList = ColorStateList.valueOf(theme.accent)
                        isClickable = false
                        isFocusable = false
                    }
                    fila.addView(rb)
                    fila.addView(TextView(this).apply {
                        text = opt.label +
                            if (opt.height == mayor && video.qualities.size > 1) {
                                " • Recomendado"
                            } else ""
                        setTextColor(theme.text)
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
                    background = ThemeManager.accentPill(theme, density)
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
            // Por defecto, la más alta
            video.selectedHeight = video.qualities.maxByOrNull { it.height }?.height
            video.resolved = true
            alListo()
        }
    }

    // ---------- Menú inferior: pegar enlace, tema ----------

    private fun mostrarMenu() {
        val menu = PopupMenu(this, binding.btnNavMenu)
        menu.menu.add("Pegar enlace")
        menu.menu.add("Tema")
        menu.menu.add("Recargar página")
        menu.menu.add("Ir a DuckDuckGo")
        menu.setOnMenuItemClickListener { item ->
            when (item.title.toString()) {
                "Pegar enlace" -> mostrarDialogoEnlace()
                "Tema" -> ThemeManager.showPicker(this)
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
            setTextColor(theme.text)
            setHintTextColor(theme.muted)
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
                val video = DetectedVideo(
                    key = groupKeyFor(l),
                    title = q.title,
                    sources = linkedSetOf(l),
                    qualities = q.options.sortedBy { it.height },
                    resolved = true,
                    selectedHeight = q.options.maxByOrNull { it.height }?.height,
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

    /** Transmite ESE video en la calidad elegida en la hoja. */
    private fun transmitirVideo(video: DetectedVideo) {
        val c = client ?: return
        val opt = YtDlpExtractor.pickOption(video.qualities, video.selectedHeight) ?: run {
            Toast.makeText(this, "Todavía no hay video para transmitir.", Toast.LENGTH_SHORT)
                .show()
            return
        }
        hojaVideos?.dismiss()
        pausarVideoDelTelefono()
        castActive = true
        binding.castBar.visibility = View.VISIBLE
        binding.tvCastBarStatus.text = "Mandando video a la tele…"
        binding.seekCast.max = 0
        binding.seekCast.progress = 0
        binding.tvCastTime.text = "0:00 / 0:00"
        lifecycleScope.launch {
            val res = CastHelper.castUrl(
                this@BrowserActivity, c, opt.url, tituloPara(video),
                "video", opt.fmt, headers = videoHeaders()
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
            val res = CastHelper.receiverCommand(cmd)
            if (binding.castBar.visibility == View.VISIBLE) {
                binding.tvCastBarStatus.text = res.message
            }
            if (!res.ok) {
                Toast.makeText(this@BrowserActivity, res.message, Toast.LENGTH_LONG).show()
            }
            if (cmd == "stop" && res.ok) {
                castActive = false
                binding.castBar.visibility = View.GONE
                detenerSondeoPosicion()
            }
        }
    }

    /** Salto de ±10 s en lo que se transmite (positionSet del canal,
     *  con respaldo ECP Fwd/Rew). */
    private fun saltar(deltaSegundos: Int) {
        lifecycleScope.launch {
            val res = CastHelper.receiverSeek(client, deltaSegundos)
            if (binding.castBar.visibility == View.VISIBLE) {
                binding.tvCastBarStatus.text = res.message
            }
            if (!res.ok) {
                Toast.makeText(this@BrowserActivity, res.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    /** Sondea la posición cada 5 s para la barra de progreso. */
    private fun iniciarSondeoPosicion() {
        detenerSondeoPosicion()
        positionJob = lifecycleScope.launch {
            while (isActive) {
                delay(5000)
                try {
                    CastSession.receiver?.positionGet()
                } catch (e: Exception) {
                }
            }
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
