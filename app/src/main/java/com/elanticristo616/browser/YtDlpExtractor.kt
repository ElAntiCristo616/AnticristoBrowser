package com.elanticristo616.browser

import android.content.Context
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.mapper.VideoFormat
import com.yausername.youtubedl_android.mapper.VideoInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Saca el video directo de un enlace con yt-dlp (el mismo motor que
 * usa Seal y la descargadora), SIN descargar nada: solo pide la
 * información del video y elige el formato según la calidad elegida.
 * Funciona con YouTube, TikTok, X, Instagram, Facebook y los más de
 * 1000 sitios que yt-dlp conoce.
 *
 * La calidad por defecto es SIEMPRE la más alta disponible, nunca
 * la más baja: el usuario ve las calidades y elige, y si no toca
 * nada se manda la más alta.
 *
 * OJO: la librería youtubedl-android es GPL-3.0, así que esta app es
 * código abierto (GPL-3.0). yt-dlp en sí es de dominio público
 * (Unlicense).
 */
object YtDlpExtractor {

    class YtDlpException(message: String) : Exception(message)

    /** Video listo para mandar a la tele. fmt: mp4 | hls | dash | mp3. */
    data class ExtractedVideo(
        val title: String,
        val url: String,
        val fmt: String,
        val height: Int
    )

    /** Una calidad disponible para elegir.
     *  Altura 0 = solo audio SOLO si el formato es de audio (mp3/m4a);
     *  un video directo sin altura conocida se etiqueta "Video", nunca
     *  "Audio" (ese era el bug que se veía en xHamster). */
    data class QualityOption(val height: Int, val url: String, val fmt: String) {
        val label: String get() = when {
            height > 0 -> "${height}p"
            fmt == "mp3" || fmt == "m4a" -> "Solo audio"
            else -> "Video"
        }
    }

    /** Título + calidades disponibles, de mayor a menor. */
    data class VideoQualities(val title: String, val options: List<QualityOption>)

    @Volatile
    private var initialized = false
    private val initLock = Any()

    /** Arranca yt-dlp una sola vez por proceso (extrae Python a archivos
     *  privados de la app; tarda unos segundos la primera vez). */
    private fun ensureInit(context: Context) {
        if (initialized) return
        synchronized(initLock) {
            if (initialized) return
            try {
                YoutubeDL.getInstance().init(context.applicationContext)
                initialized = true
            } catch (e: Exception) {
                throw YtDlpException("No pude arrancar yt-dlp: ${e.message ?: "error desconocido"}")
            }
        }
    }

    private suspend fun getInfo(context: Context, link: String): VideoInfo =
        withContext(Dispatchers.IO) {
            ensureInit(context)
            val request = YoutubeDLRequest(link.trim()).apply {
                addOption("--no-playlist")
                addOption("--socket-timeout", "10")
                addOption("-R", "1")
            }
            try {
                YoutubeDL.getInstance().getInfo(request)
            } catch (e: Exception) {
                throw YtDlpException(friendlyError(e))
            }
        }

    /** Calidades disponibles de un enlace (título + opciones). */
    suspend fun listQualities(context: Context, link: String): VideoQualities {
        val info = getInfo(context, link)
        val title = info.title?.takeIf { it.isNotBlank() }
            ?: info.fulltitle?.takeIf { it.isNotBlank() }
            ?: "Video"
        val options = buildOptions(info)
        if (options.isEmpty()) {
            throw YtDlpException("Ese enlace no trae un video que la tele pueda reproducir.")
        }
        return VideoQualities(title, options)
    }

    /** Extrae el video en la calidad elegida (o la más alta si no se
     *  pasa altura). Lanza YtDlpException con motivo en español si falla. */
    suspend fun extract(context: Context, link: String, height: Int? = null): ExtractedVideo {
        val q = listQualities(context, link)
        val opt = pickOption(q.options, height)
            ?: throw YtDlpException("Ese enlace no trae un video que la tele pueda reproducir.")
        return ExtractedVideo(q.title, opt.url, opt.fmt, opt.height)
    }

    /** Elige la opción: la altura pedida; si no existe, la más alta
     *  que no la pase; si no hay ninguna por debajo, la más baja
     *  disponible. Sin altura pedida: la más alta de todas. */
    fun pickOption(options: List<QualityOption>, height: Int?): QualityOption? {
        if (options.isEmpty()) return null
        val sorted = options.sortedByDescending { it.height }
        if (height == null) return sorted.first()
        return sorted.firstOrNull { it.height == height }
            ?: sorted.firstOrNull { it.height < height }
            ?: sorted.last()
    }

    /** Construye las opciones desde la info de yt-dlp:
     *  1) MP4 combinado (video+audio) por altura, el mejor de cada una;
     *  2) HLS: se expande el manifiesto maestro a sus variantes;
     *  3) DASH (.mpd) como opción única; 4) cualquier combinado;
     *  5) solo audio. */
    private fun buildOptions(info: VideoInfo): List<QualityOption> {
        val formats = try {
            info.formats ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
        if (formats.isEmpty()) return emptyList()
        val out = mutableListOf<QualityOption>()

        fun hasUrl(f: VideoFormat) = !f.url.isNullOrBlank()
        fun isCombined(f: VideoFormat) =
            hasUrl(f) && f.vcodec != null && f.vcodec != "none" &&
                f.acodec != null && f.acodec != "none"

        // 1) MP4 combinado, una opción por altura (el primer formato
        // de cada altura: los combinados de la misma altura son
        // equivalentes para la tele).
        formats.filter { isCombined(it) && it.ext == "mp4" && it.height > 0 }
            .groupBy { it.height }
            .forEach { (h, list) ->
                val best = list.first()
                out += QualityOption(h, best.url!!, "mp4")
            }

        // 2) HLS: expandir el manifiesto maestro a variantes
        val hls = formats.firstOrNull { f ->
            (!f.manifestUrl.isNullOrBlank() && f.manifestUrl!!.contains(".m3u8")) ||
                (!f.url.isNullOrBlank() && f.url!!.contains(".m3u8"))
        }
        if (hls != null) {
            val manifest = if (!hls.manifestUrl.isNullOrBlank() &&
                hls.manifestUrl!!.contains(".m3u8")
            ) hls.manifestUrl!! else hls.url!!
            val variants = try {
                HlsParser.parseVariants(manifest)
            } catch (e: Exception) {
                emptyList()
            }
            if (variants.isNotEmpty()) {
                for (v in variants) {
                    if (out.none { it.height == v.height }) {
                        out += QualityOption(v.height, v.url, "hls")
                    }
                }
            } else if (out.none { it.height == hls.height }) {
                out += QualityOption(hls.height, manifest, "hls")
            }
        }

        // 3) DASH
        val dash = formats.firstOrNull { f ->
            !f.manifestUrl.isNullOrBlank() && f.manifestUrl!!.contains(".mpd")
        }
        if (dash != null && out.isEmpty()) {
            out += QualityOption(dash.height, dash.manifestUrl!!, "dash")
        }

        // 4) Cualquier combinado
        if (out.isEmpty()) {
            val any = formats.firstOrNull { isCombined(it) }
            if (any != null) {
                val fmt = if (any.url!!.contains(".mp3")) "mp3" else "mp4"
                out += QualityOption(any.height, any.url!!, fmt)
            }
        }

        // 5) Solo audio
        if (out.isEmpty()) {
            val audio = formats.firstOrNull { f ->
                hasUrl(f) && f.vcodec == "none" && f.acodec != null && f.acodec != "none" &&
                    (f.ext == "mp3" || f.ext == "m4a")
            }
            if (audio != null) {
                out += QualityOption(
                    0, audio.url!!,
                    if (audio.ext == "mp3") "mp3" else "m4a"
                )
            }
        }
        return out.sortedByDescending { it.height }
    }

    private fun friendlyError(e: Exception): String {
        val m = (e.message ?: "").lowercase()
        return when {
            "unsupported url" in m ->
                "Ese enlace no es de un sitio que yt-dlp conozca."
            "private video" in m || "sign in" in m || "log in" in m ||
                "login required" in m || "cookies" in m ->
                "Ese video es privado o pide iniciar sesión."
            "unavailable" in m || "not available" in m ->
                "Ese video no está disponible."
            "timed out" in m || "timeout" in m ->
                "El sitio tardó demasiado en responder. Prueba de nuevo."
            "unable to download" in m || "unable to extract" in m ->
                "No pude leer ese enlace."
            e.message != null ->
                "No pude sacar el video de ese enlace (${e.message!!.take(120)})."
            else -> "No pude sacar el video de ese enlace."
        }
    }
}
