package com.elanticristo616.browser

import android.content.Context
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * CuteNovna Browser v1.1.0 — TRANSMISIÓN UNIVERSAL.
 *
 * Una tele es un `CastTarget` sea cual sea su protocolo:
 * Roku, Chromecast o DLNA. El resto de la app solo habla con
 * esta interfaz: mandar video, pausar, seguir, parar, saltar a
 * una posición, adelantar/atrasar y leer el estado. Cada
 * implementación resuelve eso con su protocolo.
 */

/** Estado de reproducción común para los 3 protocolos. */
data class PlaybackState(
    val positionSec: Double,
    val durationSec: Double,
    val playing: Boolean
)

/** Una tele a la que se puede transmitir. */
sealed interface CastTarget {
    val id: String
    val name: String
    /** "Roku" | "Chromecast" | "DLNA". */
    val protocol: String
    /** IP o LOCATION, para recordarla y para el estado. */
    val detail: String

    suspend fun castVideo(
        context: Context,
        url: String,
        title: String,
        fmt: String,
        headers: Map<String, String>? = null
    ): CastHelper.CastResult

    suspend fun command(cmd: String): CastHelper.CastResult
    suspend fun seekTo(seconds: Int): CastHelper.CastResult
    suspend fun seekBy(deltaSeconds: Int): CastHelper.CastResult
    suspend fun setSpeed(rate: Float): CastHelper.CastResult
    suspend fun playbackState(): PlaybackState?
    fun supportsSpeed(): Boolean
}

/** Tele Roku: todo pasa por CastHelper como en v1.0.4. */
class RokuCastTarget(val ip: String, override val name: String) : CastTarget {
    override val id = "roku:$ip"
    override val protocol = "Roku"
    override val detail = ip

    private fun client() = RokuClient(ip)

    override suspend fun castVideo(
        context: Context, url: String, title: String, fmt: String,
        headers: Map<String, String>?
    ): CastHelper.CastResult =
        CastHelper.castUrl(context, client(), url, title, "video", fmt, headers)

    override suspend fun command(cmd: String): CastHelper.CastResult =
        CastHelper.receiverCommand(cmd)

    override suspend fun seekTo(seconds: Int): CastHelper.CastResult =
        CastHelper.receiverSeekTo(seconds)

    override suspend fun seekBy(deltaSeconds: Int): CastHelper.CastResult =
        CastHelper.receiverSeek(client(), deltaSeconds)

    override suspend fun setSpeed(rate: Float): CastHelper.CastResult =
        CastHelper.CastResult(
            false,
            "Esta tele no permite cambiar la velocidad."
        )

    override suspend fun playbackState(): PlaybackState? {
        try { CastSession.receiver?.positionGet() } catch (e: Exception) { }
        val pos = CastSession.receiverPosition
        val dur = CastSession.receiverDuration
        if (pos < 0 && dur < 0) return null
        return PlaybackState(
            positionSec = pos.coerceAtLeast(0.0),
            durationSec = dur.coerceAtLeast(0.0),
            playing = CastSession.receiverState == "playing"
        )
    }

    override fun supportsSpeed(): Boolean = false
}

/** Tele Chromecast / Google Cast: SDK oficial. */
class GoogleCastTarget(val route: GoogleCastManager.CastRoute) : CastTarget {
    override val id = "cast:${route.id}"
    override val name = route.name
    override val protocol = "Chromecast"
    override val detail = ""

    private fun ok(result: Boolean, okMsg: String, errMsg: String) =
        if (result) CastHelper.CastResult(true, okMsg)
        else CastHelper.CastResult(false, errMsg)

    override suspend fun castVideo(
        context: Context, url: String, title: String, fmt: String,
        headers: Map<String, String>?
    ): CastHelper.CastResult {
        if (!GoogleCastManager.hasSession()) {
            if (!GoogleCastManager.connect(context, route)) {
                return CastHelper.CastResult(
                    false,
                    "No se pudo conectar con «$name». Revisa que el teléfono y la tele estén en el mismo Wi-Fi."
                )
            }
        }
        // v1.1.1: si la carga falla, sacar al receptor del modo
        // Cast (stop + disconnect) para que no se quede en su
        // pantalla idle con el ícono, como le pasó a la MundoGoTV.
        val loaded = GoogleCastManager.load(url, title, fmt)
        if (!loaded) {
            try { GoogleCastManager.stop() } catch (e: Exception) { }
            GoogleCastManager.disconnect()
            return CastHelper.CastResult(
                false,
                "La tele Chromecast no respondió o no puede con ese video."
            )
        }
        return CastHelper.CastResult(
            true,
            "Mandando «${title.take(60)}» a «$name»."
        )
    }

    override suspend fun command(cmd: String): CastHelper.CastResult {
        return when (cmd) {
            "pause" -> ok(
                GoogleCastManager.pause(),
                "En pausa en la tele.",
                "No se pudo pausar."
            )
            "resume" -> ok(
                GoogleCastManager.resume(),
                "Siguiendo en la tele.",
                "No se pudo seguir."
            )
            "stop" -> ok(
                GoogleCastManager.stop(),
                "Parado en la tele.",
                "No se pudo parar."
            )
            else -> CastHelper.CastResult(false, "Comando desconocido.")
        }
    }

    override suspend fun seekTo(seconds: Int): CastHelper.CastResult =
        ok(
            GoogleCastManager.seekTo(seconds),
            "",
            "No se pudo saltar a esa posición."
        )

    override suspend fun seekBy(deltaSeconds: Int): CastHelper.CastResult {
        val pos = GoogleCastManager.position()?.first ?: 0.0
        return seekTo((pos + deltaSeconds).toInt().coerceAtLeast(0))
    }

    override suspend fun setSpeed(rate: Float): CastHelper.CastResult =
        ok(
            GoogleCastManager.setSpeed(rate),
            "",
            "Esta tele no permite cambiar la velocidad."
        )

    override suspend fun playbackState(): PlaybackState? {
        val pos = GoogleCastManager.position() ?: return null
        return PlaybackState(
            positionSec = pos.first,
            durationSec = pos.second,
            playing = GoogleCastManager.isPlaying()
        )
    }

    override fun supportsSpeed(): Boolean = true
}

/** Tele DLNA / UPnP: SOAP AVTransport. */
class DlnaCastTarget(val device: DlnaClient.DlnaDevice) : CastTarget {
    override val id = "dlna:${device.ip}"
    override val name = device.name
    override val protocol = "DLNA"
    override val detail = device.ip

    private val client get() = DlnaClient(device.location, device.name)

    private fun ok(result: Boolean, okMsg: String, errMsg: String) =
        if (result) CastHelper.CastResult(true, okMsg)
        else CastHelper.CastResult(false, errMsg)

    override suspend fun castVideo(
        context: Context, url: String, title: String, fmt: String,
        headers: Map<String, String>?
    ): CastHelper.CastResult = ok(
        client.play(url, title),
        "Mandando «${title.take(60)}» a «$name».",
        "La tele DLNA no respondió o no puede con ese video. " +
            "Prueba con una calidad MP4."
    )

    override suspend fun command(cmd: String): CastHelper.CastResult {
        return when (cmd) {
            "pause" -> ok(client.pause(), "En pausa en la tele.", "No se pudo pausar.")
            "resume" -> ok(client.resume(), "Siguiendo en la tele.", "No se pudo seguir.")
            "stop" -> ok(client.stop(), "Parado en la tele.", "No se pudo parar.")
            else -> CastHelper.CastResult(false, "Comando desconocido.")
        }
    }

    override suspend fun seekTo(seconds: Int): CastHelper.CastResult =
        ok(
            client.seek(seconds),
            "",
            "No se pudo saltar a esa posición."
        )

    override suspend fun seekBy(deltaSeconds: Int): CastHelper.CastResult {
        val pos = client.position()?.first ?: 0.0
        return seekTo((pos + deltaSeconds).toInt().coerceAtLeast(0))
    }

    override suspend fun setSpeed(rate: Float): CastHelper.CastResult =
        CastHelper.CastResult(
            false,
            "Esta tele no permite cambiar la velocidad."
        )

    override suspend fun playbackState(): PlaybackState? {
        val pos = client.position() ?: return null
        val state = try { client.transportState() } catch (e: Exception) { null }
        return PlaybackState(
            positionSec = pos.first,
            durationSec = pos.second,
            playing = state == "PLAYING"
        )
    }

    override fun supportsSpeed(): Boolean = false
}

/** La tele vinculada se recuerda por protocolo, en local. */
object TargetStore {
    private const val PREFS = "cutenovna_target"
    private const val KEY_PROTOCOL = "protocol"
    private const val KEY_ID = "id"
    private const val KEY_NAME = "name"
    private const val KEY_DETAIL = "detail"

    data class SavedTarget(
        val protocol: String,
        val id: String,
        val name: String,
        val detail: String
    )

    fun save(context: Context, target: CastTarget) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_PROTOCOL, target.protocol)
            .putString(KEY_ID, target.id)
            .putString(KEY_NAME, target.name)
            .putString(KEY_DETAIL, target.detail)
            .apply()
    }

    fun load(context: Context): SavedTarget? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val protocol = prefs.getString(KEY_PROTOCOL, null) ?: return null
        val id = prefs.getString(KEY_ID, null) ?: return null
        val name = prefs.getString(KEY_NAME, null) ?: return null
        val detail = prefs.getString(KEY_DETAIL, null) ?: return null
        return SavedTarget(protocol, id, name, detail)
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }
}

/** Descubrimiento unificado: Roku (SSDP) + Chromecast (SDK)
 *  + DLNA (SSDP), en paralelo. */
object DeviceDiscovery {
    suspend fun discoverAll(context: Context): List<CastTarget> = coroutineScope {
        // El SDK de Cast solo se inicia en el hilo principal.
        val castRoutes = try {
            if (GoogleCastManager.ensure(context)) GoogleCastManager.cachedRoutes()
            else emptyList()
        } catch (e: Exception) {
            emptyList()
        }
        val rokuDeferred = async {
            runCatching { RokuClient.discover(context) }.getOrDefault(emptyList())
        }
        val dlnaDeferred = async {
            runCatching { DlnaClient.discover(context) }.getOrDefault(emptyList())
        }
        val rokuDevices = rokuDeferred.await()
        val dlnaDevices = dlnaDeferred.await()
        val out = mutableListOf<CastTarget>()
        for (device in rokuDevices) out += RokuCastTarget(device.ip, device.name)
        for (route in castRoutes) out += GoogleCastTarget(route)
        // v1.1.1: deduplicar — una misma cajita (p. ej. una Android
        // TV como la MundoGoTV) expone Cast y DLNA a la vez y salía
        // dos veces en la hoja. Se prefiere Chromecast; un DLNA con
        // la IP de una Roku o con el mismo nombre de un Cast es el
        // mismo aparato y no se agrega. Tampoco se repite un DLNA
        // por IP.
        val castNames = castRoutes
            .map { it.name.trim().lowercase() }.toSet()
        val rokuIps = rokuDevices.map { it.ip }.toSet()
        val seenDlnaIps = mutableSetOf<String>()
        for (device in dlnaDevices) {
            if (device.ip in rokuIps) continue
            if (device.name.trim().lowercase() in castNames) continue
            if (!seenDlnaIps.add(device.ip)) continue
            out += DlnaCastTarget(device)
        }
        // La Roku de la puerta siempre está aunque el SSDP no la vea.
        val gateIp = CastSession.rokuIp
        if (gateIp != null &&
            out.none { it is RokuCastTarget && it.ip == gateIp }
        ) {
            out.add(0, RokuCastTarget(gateIp, CastSession.rokuName ?: "Roku TV"))
        }
        out
    }
}
