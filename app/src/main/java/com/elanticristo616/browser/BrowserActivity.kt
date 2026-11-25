package com.elanticristo616.browser

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.elanticristo616.browser.databinding.ActivityBrowserBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray

/**
 * Navegador con detección de video, selector de calidad y transmisión
 * a la tele por el canal receptor «Web Video Caster - Receiver».
 *  - Abre páginas en un WebView y detecta videos de dos formas:
 *    a) oliendo la red (peticiones .mp4/.m3u8/.mpd),
 *    b) escaneando el DOM (<video> y <source>).
 *  - Antes de transmitir muestra las calidades disponibles y deja
 *    elegir; por defecto va la MÁS ALTA, nunca la más baja.
 *  - Para enlaces de YouTube, TikTok, X, Instagram o Facebook se usa
 *    yt-dlp, que saca el video directo sin descargar nada.
 *  - Al empezar a transmitir se PAUSA el video del teléfono para que
 *    la tele no se pause sola, y se mandan SIEMPRE los headers del
 *    video (User-Agent, Referer, Origin) al receptor.
 */
class BrowserActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBrowserBinding
    private var client: RokuClient? = null
    private var pageTitle: String = ""
    private var currentUrl: String = ""
    private val detectedUrls = LinkedHashSet<String>()

    /** Calidades del video pendiente de transmitir (detectado o de enlace). */
    private var pendingOptions: List<YtDlpExtractor.QualityOption> = emptyList()
    private var pendingTitle: String = "Video"
    private var selectedHeight: Int? = null

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

        binding.btnVolver.setOnClickListener { finish() }
        binding.btnIr.setOnClickListener { irA(binding.etDireccion.text.toString()) }
        binding.etDireccion.setOnEditorActionListener { _, _, _ ->
            irA(binding.etDireccion.text.toString()); true
        }
        binding.btnTransmitirDetectado.setOnClickListener { transmitirSeleccionado() }
        binding.btnTransmitirEnlace.setOnClickListener { transmitirEnlace() }
        binding.btnCastPausa.setOnClickListener { comandoReceptor("pause") }
        binding.btnCastSeguir.setOnClickListener { comandoReceptor("resume") }
        binding.btnCastParar.setOnClickListener { comandoReceptor("stop") }

        setupWebView()
        val startUrl = intent.getStringExtra("url") ?: "https://duckduckgo.com"
        binding.webView.loadUrl(startUrl)
        binding.etDireccion.setText(startUrl)
    }

    override fun onResume() {
        super.onResume()
        CastSession.onReceiverEvent = { event, body ->
            runOnUiThread {
                CastHelper.describeReceiverEvent(event, body)?.let {
                    binding.tvCastStatus.text = it
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
                override fun onPageFinished(view: WebView, url: String) {
                    currentUrl = url
                    binding.etDireccion.setText(url)
                    detectedUrls.clear()
                    pendingOptions = emptyList()
                    updateDetectedCard()
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
                    if (isDirectVideoUrl(u)) {
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
        binding.webView.loadUrl(url)
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
            lower.contains(".mp4?") || lower.contains(".m3u8?") ||
            lower.contains(".mpd?")
    }

    private fun onDirectVideoFound(url: String) {
        if (detectedUrls.add(url)) {
            updateDetectedCard()
            resolverCalidadesDirecto(url)
        }
    }

    /** Busca <video> y <source> en la página ya cargada. */
    private fun scanPageForVideos() {
        val js = "(function(){var v=[];" +
            "document.querySelectorAll('video').forEach(function(el){" +
            "if(el.currentSrc)v.push(el.currentSrc);" +
            "if(el.src)v.push(el.src);" +
            "el.querySelectorAll('source').forEach(function(s){if(s.src)v.push(s.src);});" +
            "});return JSON.stringify(v);})()"
        binding.webView.evaluateJavascript(js) { result ->
            try {
                val arr = JSONArray(result)
                for (i in 0 until arr.length()) {
                    val u = arr.optString(i, "")
                    if (isDirectVideoUrl(u)) detectedUrls.add(u)
                }
                updateDetectedCard()
                detectedUrls.firstOrNull()?.let { resolverCalidadesDirecto(it) }
            } catch (e: Exception) {
                // La página no trajo videos directos: nada que mostrar
            }
        }
    }

    // ---------- Selector de calidad ----------

    /** Calidades de un video directo: si es HLS se lee el manifiesto
     *  maestro y se sacan sus variantes; si no, una sola opción. */
    private fun resolverCalidadesDirecto(url: String) {
        pendingTitle = pageTitle.ifBlank { "Video" }
        lifecycleScope.launch {
            val options = withContext(Dispatchers.IO) {
                if (url.lowercase().contains(".m3u8")) {
                    HlsParser.parseVariants(url).map {
                        YtDlpExtractor.QualityOption(it.height, it.url, "hls")
                    }
                } else {
                    emptyList()
                }
            }
            pendingOptions = if (options.isNotEmpty()) {
                options
            } else {
                listOf(YtDlpExtractor.QualityOption(0, url, fmtFor(url)))
            }
            // Por defecto, la más alta
            selectedHeight = pendingOptions.maxByOrNull { it.height }?.height
            mostrarCalidades()
        }
    }

    /** Calidades de un enlace pegado (yt-dlp): se muestran los chips
     *  y se transmite al tocar el botón rojo. */
    private fun transmitirEnlace() {
        val link = binding.etEnlace.text.toString().trim()
        if (link.isBlank()) {
            Toast.makeText(this, "Pega un enlace primero.", Toast.LENGTH_SHORT).show()
            return
        }
        binding.tvCastStatus.text = "Extrayendo video…"
        lifecycleScope.launch {
            try {
                val q = YtDlpExtractor.listQualities(this@BrowserActivity, link)
                pendingTitle = q.title
                pendingOptions = q.options
                selectedHeight = q.options.maxByOrNull { it.height }?.height
                binding.detectedCard.visibility = View.VISIBLE
                binding.tvDetectedUrl.text = q.title
                mostrarCalidades()
                binding.tvCastStatus.text =
                    "Elige la calidad y toca «Transmitir este video a la tele»."
            } catch (e: YtDlpExtractor.YtDlpException) {
                binding.tvCastStatus.text = e.message
                Toast.makeText(this@BrowserActivity, e.message, Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                binding.tvCastStatus.text = "No pude sacar el video de ese enlace."
            }
        }
    }

    private fun mostrarCalidades() {
        binding.qualityChips.removeAllViews()
        if (pendingOptions.isEmpty()) {
            binding.tvQualityInfo.text = ""
            return
        }
        val labels = pendingOptions.map { it.label }
        binding.tvQualityInfo.text =
            if (pendingOptions.size == 1 && pendingOptions[0].height == 0) {
                "Calidad única (video directo)."
            } else {
                "Calidades disponibles: ${labels.joinToString(", ")}. Por defecto, la más alta."
            }
        for (opt in pendingOptions) {
            val chip = Button(this).apply {
                text = opt.label
                val selected = opt.height == selectedHeight
                backgroundTintList = ColorStateList.valueOf(
                    if (selected) 0xFFCD2D32.toInt() else 0xFF1A4A33.toInt()
                )
                setTextColor(0xFFFFFFFF.toInt())
                setOnClickListener {
                    selectedHeight = opt.height
                    mostrarCalidades()
                }
            }
            val params = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = 8 }
            binding.qualityChips.addView(chip, params)
        }
    }

    private fun updateDetectedCard() {
        val first = detectedUrls.firstOrNull()
        if (first == null && pendingOptions.isEmpty()) {
            binding.detectedCard.visibility = View.GONE
            binding.tvCastStatus.text =
                "Abre una página con video y te aviso para transmitirlo."
        } else if (first != null) {
            binding.detectedCard.visibility = View.VISIBLE
            binding.tvDetectedUrl.text = first
            val extra = if (detectedUrls.size > 1) " (+${detectedUrls.size - 1} más)" else ""
            binding.tvCastStatus.text = "Video detectado$extra. Elige la calidad y transmite."
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

    private fun transmitirSeleccionado() {
        val c = client ?: return
        val opt = YtDlpExtractor.pickOption(pendingOptions, selectedHeight) ?: run {
            Toast.makeText(this, "Todavía no hay video para transmitir.", Toast.LENGTH_SHORT).show()
            return
        }
        pausarVideoDelTelefono()
        binding.tvCastStatus.text = "Mandando video a la tele…"
        lifecycleScope.launch {
            val res = CastHelper.castUrl(
                this@BrowserActivity, c, opt.url, pendingTitle,
                "video", opt.fmt, headers = videoHeaders()
            )
            binding.tvCastStatus.text = res.message
            if (!res.ok) {
                Toast.makeText(this@BrowserActivity, res.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun comandoReceptor(cmd: String) {
        lifecycleScope.launch {
            val res = CastHelper.receiverCommand(cmd)
            binding.tvCastStatus.text = res.message
            if (!res.ok) {
                Toast.makeText(this@BrowserActivity, res.message, Toast.LENGTH_LONG).show()
            }
        }
    }
}
