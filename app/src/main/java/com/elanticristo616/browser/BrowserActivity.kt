package com.elanticristo616.browser

import android.annotation.SuppressLint
import android.content.res.ColorStateList
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
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.elanticristo616.browser.databinding.ActivityBrowserBinding
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray

/**
 * CuteNovna Browser v1.0.3: navegador mínimo estilo Brave (Opción B).
 *  - Barra superior delgada, WebView a pantalla completa, barra
 *    inferior delgada (atrás, adelante, transmitir, menú). Nada tapa
 *    la página: no hay hub gigante debajo del WebView.
 *  - Detección de video: solo una barrita píldora con el CONTEO de
 *    todos los videos reales de la página ("3 videos ▾"). Al tocarla
 *    se abre un bottom sheet con la LISTA de videos; cada video se
 *    despliega para ver SUS calidades y transmitir ESE video.
 *  - Los anuncios NO aparecen: se filtran por marcadores en la URL
 *    (doubleclick, vast, preroll…), por contenedores/iframes con
 *    clase o id de anuncio en el DOM, y por duración muy corta
 *    conocida (< 10 s). Si no queda ningún video real, la barrita
 *    no aparece.
 *  - Antes de transmitir muestra TODAS las calidades disponibles y
 *    deja elegir; por defecto va la MÁS ALTA, nunca la más baja.
 *  - Para enlaces de YouTube, TikTok, X, Instagram o Facebook se usa
 *    yt-dlp, que saca el video directo sin descargar nada.
 *  - Al empezar a transmitir se PAUSA el video del teléfono para que
 *    la tele no se pause sola, y se mandan SIEMPRE los headers del
 *    video (User-Agent, Referer, Origin) al receptor.
 * Sin configuración: no hay menú ni pantalla de ajustes en la app y
 * el buscador es fijo DuckDuckGo. La página de ajustes del propio
 * DuckDuckGo (duckduckgo.com/settings*) está bloqueada: no se carga
 * y se avisa. Los inicios de sesión (p. ej. Gmail) NO se bloquean.
 */
class BrowserActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBrowserBinding
    private var client: RokuClient? = null
    private var pageTitle: String = ""
    private var currentUrl: String = ""
    private var castActive = false

    /** Un video detectado en la página: su URL directa, sus calidades
     *  (se leen al desplegarlo en la hoja) y la calidad elegida. */
    private data class DetectedVideo(
        val url: String,
        var title: String,
        var qualities: List<YtDlpExtractor.QualityOption> = emptyList(),
        var resolved: Boolean = false,
        var selectedHeight: Int? = null,
        var expanded: Boolean = false
    )

    /** Todos los videos reales de la página, en orden de aparición. */
    private val detectedVideos = LinkedHashMap<String, DetectedVideo>()

    /** Hoja de videos (bottom sheet) y su contenido actual. */
    private var hojaVideos: BottomSheetDialog? = null
    private var hojaContainer: LinearLayout? = null
    private var hojaLista: List<DetectedVideo> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBrowserBinding.inflate(layoutInflater)
        setContentView(binding.root)

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

    override fun onBackPressed() {
        if (binding.webView.canGoBack()) {
            binding.webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        binding.webView.apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            webViewClient = object : WebViewClient() {
                /** La página de ajustes de DuckDuckGo no se carga. */
                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest
                ): Boolean {
                    if (isSettingsBloqueada(request.url.toString())) {
                        avisoConfigBloqueada()
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
        "trafficjunky", "juicyads"
    )

    /** ¿La URL huele a anuncio? */
    private fun isAdUrl(u: String): Boolean {
        val lower = u.lowercase()
        return AD_URL_MARKERS.any { lower.contains(it) }
    }

    // ---------- Detección de video ----------

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

    private fun onDirectVideoFound(url: String) {
        if (detectedVideos.containsKey(url)) return
        detectedVideos[url] = DetectedVideo(url, pageTitle.ifBlank { "Video" })
        updatePill()
    }

    /** Busca <video> y <source> en la página ya cargada. Devuelve el
     *  arreglo directo (no JSON.stringify): así evaluateJavascript lo
     *  entrega como JSON y no como texto entrecomillado.
     *  Cada video trae: src, ad (si vive dentro de un contenedor o
     *  iframe de anuncio) y dur (duración en segundos, -1 si no se
     *  conoce). Los anuncios y los videos de menos de 10 s se
     *  descartan: nunca llegan a la lista. */
    private fun scanPageForVideos() {
        val js = "(function(){" +
            "function esAnuncio(el){" +
            "var n=el,d=0;" +
            "while(n&&d<6){" +
            "var c='';" +
            "try{c=(n.className&&n.className.baseVal!==undefined)?n.className.baseVal:(n.className||'');}catch(e){}" +
            "var s=(''+c+' '+(n.id||'')).toLowerCase();" +
            "if(/(^|[\\s\\-_])(ad|ads|advert|advertisement|sponsor|preroll|banner)([\\s\\-_]|$)/.test(s))return true;" +
            "if(n.tagName==='IFRAME'){" +
            "var src=(n.src||'').toLowerCase();" +
            "if(src.indexOf('doubleclick')>=0||src.indexOf('googlesyndication')>=0||src.indexOf('ads')>=0||src.indexOf('vast')>=0)return true;" +
            "}" +
            "n=n.parentElement;d++;" +
            "}" +
            "return false;" +
            "}" +
            "var out=[];" +
            "document.querySelectorAll('video').forEach(function(el){" +
            "var srcs=[];" +
            "if(el.currentSrc)srcs.push(el.currentSrc);" +
            "if(el.src)srcs.push(el.src);" +
            "el.querySelectorAll('source').forEach(function(s){if(s.src)srcs.push(s.src);});" +
            "var dur=-1;try{if(isFinite(el.duration))dur=el.duration;}catch(e){}" +
            "var ad=esAnuncio(el);" +
            "srcs.forEach(function(s){out.push({src:s,ad:ad,dur:dur});});" +
            "});" +
            "return out;})()"
        binding.webView.evaluateJavascript(js) { result ->
            try {
                val arr = JSONArray(result)
                var cambio = false
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val u = o.optString("src", "")
                    if (u.isBlank()) continue
                    if (o.optBoolean("ad", false)) continue
                    val dur = o.optDouble("dur", -1.0)
                    if (dur > 0 && dur < 10) continue
                    if (isAdUrl(u)) continue
                    if (!isDirectVideoUrl(u)) continue
                    if (!detectedVideos.containsKey(u)) {
                        detectedVideos[u] =
                            DetectedVideo(u, pageTitle.ifBlank { "Video" })
                        cambio = true
                    }
                }
                if (cambio) updatePill()
            } catch (e: Exception) {
                // La página no trajo videos directos: nada que mostrar
            }
        }
    }

    /** La barrita píldora: el conteo de TODOS los videos reales de la
     *  página. Sin videos reales, no aparece. */
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

    /** Abre la hoja con la lista de videos. Si se pasa un video (enlace
     *  pegado con yt-dlp), la hoja trae solo ese video. */
    private fun abrirHojaVideos(unVideo: DetectedVideo? = null) {
        val videos = if (unVideo != null) listOf(unVideo)
        else detectedVideos.values.toList()
        if (videos.isEmpty()) {
            Toast.makeText(this, "No hay video en esta página.", Toast.LENGTH_SHORT).show()
            return
        }
        hojaVideos?.dismiss()
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_videos, null)
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

    /** Un renglón de la hoja: encabezado del video + (al desplegarlo)
     *  sus calidades con radio y su botón de transmitir. */
    private fun crearItemVideo(video: DetectedVideo): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.card_bg)
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
        val icon = ImageView(this).apply {
            setImageResource(R.drawable.ic_tv)
            setColorFilter(0xFFCD2D32.toInt())
            layoutParams = LinearLayout.LayoutParams(dp(24), dp(24))
        }
        header.addView(icon)
        val textos = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
            ).apply { marginStart = dp(10) }
        }
        val tvTitulo = TextView(this).apply {
            text = video.title
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 14f
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        }
        val tvUrl = TextView(this).apply {
            text = urlCorta(video.url)
            setTextColor(0xFFA8C3B5.toInt())
            textSize = 12f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.MIDDLE
        }
        textos.addView(tvTitulo)
        textos.addView(tvUrl)
        header.addView(textos)
        val flecha = TextView(this).apply {
            text = if (video.expanded) "▴" else "▾"
            setTextColor(0xFFA8C3B5.toInt())
            textSize = 18f
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = dp(8) }
        }
        header.addView(flecha)
        card.addView(header)

        header.setOnClickListener {
            video.expanded = !video.expanded
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
                    setTextColor(0xFFA8C3B5.toInt())
                    textSize = 13f
                    setPadding(0, dp(10), 0, 0)
                })
            } else {
                val group = RadioGroup(this).apply {
                    orientation = RadioGroup.VERTICAL
                    setPadding(0, dp(8), 0, 0)
                }
                val mayor = video.qualities.maxByOrNull { it.height }?.height
                for (opt in video.qualities) {
                    val rb = RadioButton(this).apply {
                        text = opt.label +
                            if (opt.height == mayor && video.qualities.size > 1) {
                                " • Recomendado"
                            } else ""
                        setTextColor(0xFFFFFFFF.toInt())
                        textSize = 14f
                        buttonTintList = ColorStateList.valueOf(0xFFCD2D32.toInt())
                        isChecked = opt.height == video.selectedHeight
                        id = View.generateViewId()
                        tag = opt.height
                    }
                    group.addView(rb)
                }
                group.setOnCheckedChangeListener { _, checkedId ->
                    val rb = group.findViewById<RadioButton>(checkedId)
                    video.selectedHeight = rb?.tag as? Int
                }
                card.addView(group)
                val btn = Button(this).apply {
                    text = "Transmitir este video a la tele"
                    setBackgroundResource(R.drawable.bg_red_pill)
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

    // ---------- Selector de calidad ----------

    /** Calidades de un video directo: si es HLS se lee el manifiesto
     *  maestro y se sacan TODAS sus variantes; si no, una sola opción
     *  con su altura real (de la URL si se puede). */
    private fun resolverCalidades(video: DetectedVideo, alListo: () -> Unit) {
        val lower = video.url.lowercase()
        val esHls = lower.contains(".m3u8") || lower.contains("media=hls")
        val cookie = try {
            CookieManager.getInstance().getCookie(video.url)
        } catch (e: Exception) {
            null
        }
        lifecycleScope.launch {
            val options = withContext(Dispatchers.IO) {
                if (esHls) {
                    HlsParser.parseVariants(
                        video.url, referer = currentUrl, cookie = cookie
                    ).map {
                        YtDlpExtractor.QualityOption(it.height, it.url, "hls")
                    }
                } else {
                    emptyList()
                }
            }
            video.qualities = if (options.isNotEmpty()) {
                options
            } else {
                val h = HlsParser.inferHeightFromUrl(video.url)
                listOf(YtDlpExtractor.QualityOption(h, video.url, fmtFor(video.url)))
            }
            // Por defecto, la más alta
            video.selectedHeight = video.qualities.maxByOrNull { it.height }?.height
            video.resolved = true
            alListo()
        }
    }

    // ---------- Menú inferior: pegar enlace ----------

    private fun mostrarMenu() {
        val menu = PopupMenu(this, binding.btnNavMenu)
        menu.menu.add("Pegar enlace")
        menu.menu.add("Recargar página")
        menu.menu.add("Ir a DuckDuckGo")
        menu.setOnMenuItemClickListener { item ->
            when (item.title.toString()) {
                "Pegar enlace" -> mostrarDialogoEnlace()
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
            setTextColor(0xFFFFFFFF.toInt())
            setHintTextColor(0xFFA8C3B5.toInt())
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
                    url = l,
                    title = q.title,
                    qualities = q.options,
                    resolved = true,
                    selectedHeight = q.options.maxByOrNull { it.height }?.height,
                    expanded = true
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
        lifecycleScope.launch {
            val res = CastHelper.castUrl(
                this@BrowserActivity, c, opt.url, video.title,
                "video", opt.fmt, headers = videoHeaders()
            )
            binding.tvCastBarStatus.text = res.message
            if (!res.ok) {
                Toast.makeText(this@BrowserActivity, res.message, Toast.LENGTH_LONG).show()
                castActive = false
                binding.castBar.visibility = View.GONE
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
            }
        }
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
}
