package com.elanticristo616.browser

import android.content.Context
import java.io.BufferedReader
import java.util.Locale

/**
 * CuteNovna Browser v1.1.5 — BLOQUEO DE ANUNCIOS A NIVEL DE PETICIÓN.
 *
 * Lista: `assets/ad-blocklist.txt` (46,986 dominios base, generada
 * con tools/gen_blocklist.py a partir de EasyList —dual CC BY-SA 3.0 /
 * GPLv3— + DuckDuckGo Tracker Blocklist —CC BY-NC-SA 4.0— + redes de
 * anuncios de streaming/porno). Matching por sufijo: si
 * "doubleclick.net" está en la lista, "ads.doubleclick.net" y
 * "g.doubleclick.net" también caen.
 *
 * REGLAS DE SEGURIDAD (el video principal no se toca jamás):
 *  - NUNCA se bloquea una URL de video directa (.mp4/.m3u8/.mpd/.webm/
 *    .mov/.mkv) ni un segmento de video (.ts/.m4s/.m4v/.m4a): el
 *    selector ya filtra los anuncios de su lista, y un falso positivo
 *    aquí mataría el video que el usuario quiere ver.
 *  - NUNCA se bloquean los hosts de video/medios ni login
 *    (googlevideo.com, youtube.com, ytimg.com, ggpht.com, gvt1.com,
 *    bkcdn.net, DuckDuckGo, cuentas de Google, gstatic...): la lista
 *    del asset tampoco los trae, pero esto es una segunda red de
 *    seguridad.
 *  - NUNCA se bloquea la red local (localhost, 127.0.0.1, 10/8,
 *    172.16/12, 192.168/16): ahí viven el servidor local de Roku y
 *    el flujo Cast/DLNA. (Igual el Cast no pasa por el WebView, pero
 *    por si acaso.)
 *  - Las peticiones del marco principal (main frame) NO se bloquean
 *    aquí: esas las veta shouldOverrideUrlLoading y el usuario se
 *    queda en su página en vez de ver una página en blanco.
 *
 * La lista se carga perezosamente la primera vez que se usa (el asset
 * es de ~850 KB, unas decenas de miles de dominios: la carga es de
 * milisegundos) y es segura para hilos: shouldInterceptRequest ya
 * corre en un hilo de fondo.
 */
object Blocklist {

    private const val ASSET_NAME = "ad-blocklist.txt"

    /** Hosts que NUNCA se bloquean, ni por lista ni por marcadores. */
    private val NEVER_BLOCK_SUFFIXES = listOf(
        "googlevideo.com", "ytimg.com", "ggpht.com", "gvt1.com",
        "youtube.com", "youtube-nocookie.com", "youtu.be",
        "bkcdn.net", "bunnycdn.com",
        "duckduckgo.com", "accounts.google.com",
        "ssl.gstatic.com", "fonts.gstatic.com", "fonts.googleapis.com",
        "gstatic.com", "localhost"
    )

    /** Extensiones de video directo y de segmentos de streaming. */
    private val MEDIA_EXTENSIONS = listOf(
        ".mp4", ".m3u8", ".mpd", ".webm", ".mov", ".mkv",
        ".ts", ".m4s", ".m4v", ".m4a"
    )

    /** Extensiones de video directo (sin segmentos: un .ts solo no
     *  se puede transmitir como el video completo). */
    private val VIDEO_EXTENSIONS = listOf(
        ".mp4", ".m3u8", ".mpd", ".webm", ".mov", ".mkv"
    )

    /** Claves de parámetro que suelen llevar un archivo de video
     *  como valor (?file=...mp4, ?src=...). */
    private val FILE_PARAM_KEYS = setOf(
        "file", "src", "url", "media", "video", "hls", "u"
    )

    /** ¿La URL trae una de estas extensiones como archivo de video?
     *  Formas reales de los reproductores: al final de la ruta
     *  (video.mp4), con barra final (KVS: .../video_720p.mp4/) o en
     *  el valor de un parámetro (?file=...mp4&token=...). La
     *  extensión debe ir al final del valor — una miniatura
     *  "video.mp4.jpg" no cuenta, y una búsqueda "?q=video.mp4"
     *  tampoco (el valor no parece ruta ni es clave de archivo). */
    private fun hasVideoExtension(lower: String, exts: List<String>): Boolean {
        val path = lower.substringBefore('?').substringBefore('#').trimEnd('/')
        if (exts.any { path.endsWith(it) }) return true
        val query = lower.substringAfter('?', "").substringBefore('#')
        if (query.isNotEmpty()) {
            for (param in query.split('&')) {
                val eq = param.indexOf('=')
                if (eq < 0) continue
                val key = param.substring(0, eq)
                val value = param.substring(eq + 1)
                    .substringBefore('?').trimEnd('/')
                if (value.isEmpty()) continue
                val pareceArchivo = value.contains('/') || key in FILE_PARAM_KEYS
                if (pareceArchivo && exts.any { value.endsWith(it) }) return true
            }
        }
        return false
    }

    /** ¿La URL apunta a un archivo de video directo? (Para el
     *  selector y el intercept: NO incluye segmentos .ts/.m4s.) */
    internal fun isVideoFileUrl(url: String): Boolean {
        val lower = url.lowercase(Locale.ROOT)
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) return false
        if (hasVideoExtension(lower, VIDEO_EXTENSIONS)) return true
        return lower.contains("media=hls")
    }

    @Volatile
    private var domains: Set<String>? = null

    /** Carga la lista desde el asset (una vez). */
    fun init(context: Context) {
        if (domains != null) return
        synchronized(this) {
            if (domains != null) return
            domains = try {
                context.assets.open(ASSET_NAME).bufferedReader().use(::loadFromReader)
            } catch (e: Exception) {
                emptySet()
            }
        }
    }

    /** Carga la lista desde un lector (para tests y para init). */
    internal fun loadFromReader(reader: BufferedReader): Set<String> {
        val out = HashSet<String>()
        reader.forEachLine { line ->
            val d = line.trim().lowercase(Locale.ROOT)
            if (d.isNotEmpty() && !d.startsWith("#")) out.add(d)
        }
        return out
    }

    /** Solo para tests: fija la lista en memoria. */
    internal fun setDomainsForTest(domains: Set<String>) {
        this.domains = domains
    }

    /** ¿El host es de la red local (servidor Roku, Cast, DLNA)? */
    internal fun isLocalHost(host: String): Boolean {
        val h = host.lowercase(Locale.ROOT)
        if (h == "localhost" || h.endsWith(".localhost")) return true
        val parts = h.split(".")
        if (parts.size != 4) return false
        val nums = parts.map { it.toIntOrNull() ?: return false }
        return when {
            nums[0] == 127 -> true
            nums[0] == 10 -> true
            nums[0] == 172 && nums[1] in 16..31 -> true
            nums[0] == 192 && nums[1] == 168 -> true
            else -> false
        }
    }

    /** ¿El host está en la lista de nunca-bloquear? */
    internal fun isNeverBlock(host: String): Boolean {
        val h = host.lowercase(Locale.ROOT)
        return NEVER_BLOCK_SUFFIXES.any { h == it || h.endsWith(".$it") }
    }

    /** ¿La URL es video directo o segmento de streaming? */
    internal fun isMediaUrl(url: String): Boolean {
        val lower = url.lowercase(Locale.ROOT)
        if (hasVideoExtension(lower, MEDIA_EXTENSIONS)) return true
        return lower.contains("media=hls")
    }

    /** Extrae el host en minúsculas de una URL (o null). Usa
     *  java.net.URI (no android.net.Uri) para que el matcher se pueda
     *  probar en tests unitarios JVM sin Robolectric. */
    internal fun hostOf(url: String): String? {
        return try {
            java.net.URI(url).host?.lowercase(Locale.ROOT)?.trimEnd('.')
        } catch (e: Exception) {
            null
        }
    }

    /** ¿El host está bloqueado? Matching por sufijo contra la lista. */
    fun isBlockedHost(host: String): Boolean {
        val set = domains ?: return false
        if (set.isEmpty()) return false
        var h = host.lowercase(Locale.ROOT).trimEnd('.')
        if (h.isEmpty()) return false
        if (isNeverBlock(h) || isLocalHost(h)) return false
        while (true) {
            if (set.contains(h)) return true
            val dot = h.indexOf('.')
            if (dot < 0) return false
            h = h.substring(dot + 1)
        }
    }

    /**
     * ¿Se bloquea esta petición de subrecurso? (shouldInterceptRequest).
     * Nunca el marco principal, nunca video/medios, nunca red local
     * ni hosts de nunca-bloquear. Además de la lista, se usan los
     * marcadores de AdFilter para cazar URLs de anuncios en dominios
     * que no están listados (p. ej. "imasdk", "vast", "preroll").
     */
    fun shouldBlockRequest(url: String, isMainFrame: Boolean): Boolean {
        if (isMainFrame) return false
        if (isMediaUrl(url)) return false
        val host = hostOf(url) ?: return false
        if (isBlockedHost(host)) return true
        return AdFilter.isAdUrl(url)
    }

    /**
     * ¿Se veta esta navegación? (shouldOverrideUrlLoading).
     *
     * v1.1.4: solo por dominio de la lista. v1.1.5: además, si la
     * navegación es del marco principal a OTRO host y la URL huele a
     * anuncio según AdFilter (marcadores como "vast", "preroll",
     * "ads."...), también se veta. La condición del host distinto es
     * la que protege las URLs legítimas: una página puede llevar
     * "promo" o "banner" en su propia URL sin ser un anuncio. Las
     * URLs de video directo nunca se vetan, y los hosts de
     * nunca-bloquear (video, login, red local) tampoco.
     *
     * @param currentHost host de la página actual (o null si no se
     *   conoce): sin él solo aplica el veto por dominio de la lista.
     */
    fun shouldVetoNavigation(url: String, currentHost: String? = null): Boolean {
        if (isMediaUrl(url)) return false
        val host = hostOf(url) ?: return false
        if (isNeverBlock(host) || isLocalHost(host)) return false
        if (isBlockedHost(host)) return true
        val cur = currentHost?.lowercase(Locale.ROOT)?.trimEnd('.')
        if (!cur.isNullOrEmpty() && host != cur && AdFilter.isAdUrl(url)) {
            return true
        }
        return false
    }
}
