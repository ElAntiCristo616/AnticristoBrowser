package com.elanticristo616.browser

import java.net.HttpURLConnection
import java.net.URL

/**
 * Lee un manifiesto HLS maestro (.m3u8) y saca sus variantes
 * (renditions) con su altura: 144p, 360p, 720p, 1080p…
 * Así el usuario elige la calidad y se manda a la tele la variante
 * exacta, en vez de dejar que el receptor agarre la más baja.
 */
object HlsParser {

    data class Variant(val height: Int, val url: String, val bandwidth: Long = 0)

    /** Variantes del manifiesto maestro, ordenadas de mayor a menor altura.
     *  referer: URL de la página (muchos CDN lo exigen); cookie: cookies
     *  del WebView para ese dominio (algunos manifiestos piden sesión).
     *  Si el manifiesto es una LISTA DE MEDIOS (sin #EXT-X-STREAM-INF
     *  pero con #EXTINF), se infiere la altura de la propia URL
     *  (p. ej. multi=740x480 → 480p): así nunca cae a una sola opción
     *  sin altura, que antes se etiquetaba mal como "Audio". */
    fun parseVariants(
        masterUrl: String,
        referer: String? = null,
        cookie: String? = null
    ): List<Variant> {
        val text = fetch(masterUrl, referer, cookie) ?: return emptyList()
        if (!text.contains("#EXTM3U")) return emptyList()
        val out = mutableListOf<Variant>()
        val lines = text.lines()
        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()
            if (line.startsWith("#EXT-X-STREAM-INF")) {
                val res = Regex("""RESOLUTION=(\d+)x(\d+)""").find(line)
                val bw = Regex("""BANDWIDTH=(\d+)""").find(line)
                    ?.groupValues?.getOrNull(1)?.toLongOrNull() ?: 0L
                val height = res?.groupValues?.getOrNull(2)?.toIntOrNull() ?: 0
                // La URI viene en la siguiente línea que no es comentario
                var j = i + 1
                while (j < lines.size && lines[j].trim().startsWith("#")) j++
                val uri = lines.getOrNull(j)?.trim().orEmpty()
                if (uri.isNotBlank() && height > 0) {
                    out += Variant(height, resolve(masterUrl, uri), bw)
                }
                i = j
            }
            i++
        }
        if (out.isNotEmpty()) {
            // Una variante por altura (la de más bitrate si se repite)
            return out.groupBy { it.height }
                .map { (_, v) -> v.maxByOrNull { it.bandwidth }!! }
                .sortedByDescending { it.height }
        }
        // Lista de medios: un solo flujo; la altura sale de la URL.
        if (text.contains("#EXTINF")) {
            val height = inferHeightFromUrl(masterUrl)
            if (height > 0) return listOf(Variant(height, masterUrl))
        }
        return emptyList()
    }

    /** Altura (p. ej. 480) sacada de la URL: multi=740x480, 1280x720… */
    fun inferHeightFromUrl(url: String): Int {
        val m = Regex("""(\d{3,4})x(\d{3,4})""").find(url) ?: return 0
        return m.groupValues[2].toIntOrNull() ?: 0
    }

    private fun fetch(url: String, referer: String?, cookie: String?): String? {
        return try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36"
            )
            if (!referer.isNullOrBlank()) {
                conn.setRequestProperty("Referer", referer)
            }
            if (!cookie.isNullOrBlank()) {
                conn.setRequestProperty("Cookie", cookie)
            }
            try {
                if (conn.responseCode != 200) return null
                conn.inputStream.bufferedReader(Charsets.UTF_8).readText()
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun resolve(base: String, uri: String): String {
        if (uri.startsWith("http://") || uri.startsWith("https://")) return uri
        return try {
            URL(URL(base), uri).toString()
        } catch (e: Exception) {
            uri
        }
    }
}
