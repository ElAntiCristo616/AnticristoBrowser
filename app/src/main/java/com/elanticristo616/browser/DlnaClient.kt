package com.elanticristo616.browser

import android.content.Context
import android.net.wifi.WifiManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.nio.charset.StandardCharsets

/**
 * CuteNovna Browser v1.1.0 — DLNA / UPnP (renderer de la tele).
 *
 * Casi todas las smart TV traen un reproductor DLNA: se descubre por
 * SSDP (M-SEARCH con ST MediaRenderer), se lee su descripción para
 * sacar la URL de control de AVTransport y se le mandan comandos
 * SOAP: SetAVTransportURI + Play para el video, Pause, Stop, Seek
 * (REL_TIME) para adelantar/atrasar y GetPositionInfo para la
 * posición. Todo en la red local, sin cuentas ni servidores.
 *
 * Solo video directo (MP4): HLS (.m3u8) no lo reproducen casi
 * ningún renderer, así que la app elige calidades MP4 cuando la
 * tele es DLNA.
 */
class DlnaClient(
    val location: String,
    val name: String
) {

    data class DlnaDevice(
        val ip: String,
        val name: String,
        val location: String,
        val controlUrl: String
    )

    /** URL de control de AVTransport, resuelta al pedir la
     *  descripción del aparato (se guarda en caché). */
    private var cachedControlUrl: String? = null

    private suspend fun controlUrl(): String? {
        cachedControlUrl?.let { return it }
        val resolved = withContext(Dispatchers.IO) { fetchControlUrl(location) }
        cachedControlUrl = resolved
        return resolved
    }

    /** Manda reproducir un video directo (MP4). */
    suspend fun play(url: String, title: String): Boolean {
        val ctrl = controlUrl() ?: return false
        val meta = didlLite(url, title)
        val set = soap(
            ctrl, "SetAVTransportURI",
            "<InstanceID>0</InstanceID>" +
                "<CurrentURI>${escapeXml(url)}</CurrentURI>" +
                "<CurrentURIMetaData>${escapeXml(meta)}</CurrentURIMetaData>"
        ) ?: return false
        if ("<s:Fault>" in set || "<fault" in set.lowercase()) return false
        val play = soap(ctrl, "Play", "<InstanceID>0</InstanceID><Speed>1</Speed>")
            ?: return false
        return "<s:Fault>" !in play && "<fault" !in play.lowercase()
    }

    suspend fun pause(): Boolean {
        val ctrl = controlUrl() ?: return false
        val res = soap(ctrl, "Pause", "<InstanceID>0</InstanceID>") ?: return false
        return "<s:Fault>" !in res && "<fault" !in res.lowercase()
    }

    /** Reanudar: Play con velocidad 1. */
    suspend fun resume(): Boolean {
        val ctrl = controlUrl() ?: return false
        val res = soap(ctrl, "Play", "<InstanceID>0</InstanceID><Speed>1</Speed>")
            ?: return false
        return "<s:Fault>" !in res && "<fault" !in res.lowercase()
    }

    suspend fun stop(): Boolean {
        val ctrl = controlUrl() ?: return false
        val res = soap(ctrl, "Stop", "<InstanceID>0</InstanceID>") ?: return false
        return "<s:Fault>" !in res && "<fault" !in res.lowercase()
    }

    /** Salta a una posición en segundos (REL_TIME). */
    suspend fun seek(seconds: Int): Boolean {
        val ctrl = controlUrl() ?: return false
        val target = secondsToRelTime(seconds.coerceAtLeast(0))
        val res = soap(
            ctrl, "Seek",
            "<InstanceID>0</InstanceID><Unit>REL_TIME</Unit>" +
                "<Target>$target</Target>"
        ) ?: return false
        return "<s:Fault>" !in res && "<fault" !in res.lowercase()
    }

    /** Posición y duración en segundos, o null si no responde. */
    suspend fun position(): Pair<Double, Double>? {
        val ctrl = controlUrl() ?: return null
        val res = soap(ctrl, "GetPositionInfo", "<InstanceID>0</InstanceID>")
            ?: return null
        return parsePositionInfo(res)
    }

    /** Estado actual: PLAYING, PAUSED_PLAYBACK, STOPPED... */
    suspend fun transportState(): String? {
        val ctrl = controlUrl() ?: return null
        val res = soap(ctrl, "GetTransportInfo", "<InstanceID>0</InstanceID>")
            ?: return null
        return Regex("<CurrentTransportState>([^<]+)</CurrentTransportState>")
            .find(res)?.groupValues?.getOrNull(1)?.trim()
    }

    /** Llamada SOAP a AVTransport. Devuelve el cuerpo o null. */
    private fun soap(controlUrl: String, action: String, inner: String): String? {
        return try {
            val body = "<?xml version=\"1.0\" encoding=\"utf-8\"?>" +
                "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" " +
                "s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">" +
                "<s:Body><u:$action xmlns:u=\"urn:schemas-upnp-org:service:AVTransport:1\">" +
                inner +
                "</u:$action></s:Body></s:Envelope>"
            val conn = URL(controlUrl).openConnection() as HttpURLConnection
            try {
                conn.requestMethod = "POST"
                conn.connectTimeout = 5000
                conn.readTimeout = 8000
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "text/xml; charset=\"utf-8\"")
                conn.setRequestProperty(
                    "SOAPACTION",
                    "\"urn:schemas-upnp-org:service:AVTransport:1#$action\""
                )
                conn.outputStream.use {
                    it.write(body.toByteArray(StandardCharsets.UTF_8))
                }
                if (conn.responseCode !in 200..299) return null
                BufferedReader(InputStreamReader(conn.inputStream, StandardCharsets.UTF_8))
                    .readText()
            } finally {
                try { conn.disconnect() } catch (e: Exception) { }
            }
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        /** Busca renderers DLNA en el Wi-Fi local con SSDP.
         *  Manda M-SEARCH (ST MediaRenderer) 3 veces y escucha 5 s.
         *  Usa MulticastLock: sin él, algunos celulares no reciben
         *  las respuestas. */
        suspend fun discover(context: Context, timeoutMs: Int = 5000): List<DlnaDevice> =
            withContext(Dispatchers.IO) {
                val found = LinkedHashMap<String, DlnaDevice>()
                var socket: DatagramSocket? = null
                var lock: WifiManager.MulticastLock? = null
                try {
                    val wifi = context.applicationContext
                        .getSystemService(Context.WIFI_SERVICE) as? WifiManager
                    lock = wifi?.createMulticastLock("anticristo-dlna")
                    lock?.setReferenceCounted(true)
                    try { lock?.acquire() } catch (e: Exception) { }
                    socket = DatagramSocket()
                    socket.soTimeout = 500
                    val group = InetAddress.getByName("239.255.255.250")
                    val request = (
                        "M-SEARCH * HTTP/1.1\r\n" +
                            "HOST: 239.255.255.250:1900\r\n" +
                            "MAN: \"ns=1\"\r\n" +
                            "MX: 3\r\n" +
                            "ST: urn:schemas-upnp-org:device:MediaRenderer:1\r\n" +
                            "\r\n"
                        ).toByteArray(StandardCharsets.UTF_8)
                    val buf = ByteArray(4096)
                    val deadline = System.currentTimeMillis() + timeoutMs
                    var sent = 0
                    var nextSend = 0L
                    while (System.currentTimeMillis() < deadline) {
                        val now = System.currentTimeMillis()
                        if (sent < 3 && now >= nextSend) {
                            try {
                                socket.send(
                                    DatagramPacket(request, request.size, group, 1900)
                                )
                            } catch (e: Exception) { }
                            sent++
                            nextSend = now + 500
                        }
                        try {
                            val recv = DatagramPacket(buf, buf.size)
                            socket.receive(recv)
                            val text = String(recv.data, 0, recv.length, StandardCharsets.UTF_8)
                            val location = Regex("LOCATION:\\s*(\\S+)", RegexOption.IGNORE_CASE)
                                .find(text)?.groupValues?.getOrNull(1) ?: continue
                            val ip = Regex("://([0-9.]+)").find(location)
                                ?.groupValues?.getOrNull(1)
                                ?: recv.address.hostAddress ?: continue
                            if (found.containsKey(ip)) continue
                            val desc = fetchDescription(location) ?: continue
                            val ctrl = parseControlUrl(location, desc) ?: continue
                            found[ip] = DlnaDevice(
                                ip = ip,
                                name = parseFriendlyName(desc) ?: "Tele DLNA",
                                location = location,
                                controlUrl = ctrl
                            )
                        } catch (e: java.net.SocketTimeoutException) {
                            // Sin respuesta en este intervalo: se sigue esperando
                        }
                    }
                } catch (e: Exception) {
                    // Sin red o sin permiso: se devuelve lo encontrado
                } finally {
                    try { socket?.close() } catch (e: Exception) { }
                    try { if (lock?.isHeld == true) lock.release() } catch (e: Exception) { }
                }
                found.values.toList()
            }

        /** Lee la descripción del aparato (GET a LOCATION). */
        private fun fetchDescription(location: String): String? {
            return try {
                val conn = URL(location).openConnection() as HttpURLConnection
                try {
                    conn.requestMethod = "GET"
                    conn.connectTimeout = 5000
                    conn.readTimeout = 5000
                    if (conn.responseCode != 200) return null
                    BufferedReader(InputStreamReader(conn.inputStream, StandardCharsets.UTF_8))
                        .readText()
                } finally {
                    try { conn.disconnect() } catch (e: Exception) { }
                }
            } catch (e: Exception) {
                null
            }
        }

        /** URL de control del servicio AVTransport, resuelta contra
         *  la LOCATION (puede ser relativa). */
        fun parseControlUrl(location: String, descriptionXml: String): String? {
            val serviceBlock = Regex(
                "<service>.*?urn:schemas-upnp-org:service:AVTransport:1.*?</service>",
                setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
            ).find(descriptionXml)?.value ?: return null
            val ctrl = Regex("<controlURL>([^<]+)</controlURL>", RegexOption.IGNORE_CASE)
                .find(serviceBlock)?.groupValues?.getOrNull(1)?.trim() ?: return null
            return try {
                URL(URL(location), ctrl).toString()
            } catch (e: Exception) {
                ctrl
            }
        }

        fun parseFriendlyName(descriptionXml: String): String? {
            return Regex("<friendlyName>([^<]+)</friendlyName>", RegexOption.IGNORE_CASE)
                .find(descriptionXml)?.groupValues?.getOrNull(1)?.trim()
                ?.takeIf { it.isNotBlank() }
        }

        /** Saca la URL de control de AVTransport de una descripción
         *  (se usa al restaurar una tele DLNA guardada). */
        fun fetchControlUrl(location: String): String? {
            val desc = fetchDescription(location) ?: return null
            return parseControlUrl(location, desc)
        }

        /** Posición y duración (s) de una respuesta GetPositionInfo. */
        fun parsePositionInfo(xml: String): Pair<Double, Double>? {
            val rel = Regex("<RelTime>([^<]*)</RelTime>")
                .find(xml)?.groupValues?.getOrNull(1) ?: return null
            val dur = Regex("<TrackDuration>([^<]*)</TrackDuration>")
                .find(xml)?.groupValues?.getOrNull(1) ?: ""
            val pos = parseTimeToSeconds(rel)
            val duration = parseTimeToSeconds(dur)
            if (pos < 0) return null
            return pos to duration
        }

        /** "0:01:30" o "1:30" → 90. "NOT_IMPLEMENTED" → -1. */
        fun parseTimeToSeconds(t: String): Double {
            val clean = t.trim()
            if (clean.isEmpty() || clean.equals("NOT_IMPLEMENTED", ignoreCase = true)) {
                return -1.0
            }
            val parts = clean.split(":")
            return try {
                when (parts.size) {
                    3 -> parts[0].toDouble() * 3600 + parts[1].toDouble() * 60 +
                        parts[2].toDouble()
                    2 -> parts[0].toDouble() * 60 + parts[1].toDouble()
                    1 -> parts[0].toDouble()
                    else -> -1.0
                }
            } catch (e: Exception) {
                -1.0
            }
        }

        /** 90 → "0:01:30" (formato REL_TIME de UPnP). */
        fun secondsToRelTime(seconds: Int): String {
            val s = seconds.coerceAtLeast(0)
            val h = s / 3600
            val m = (s % 3600) / 60
            val sec = s % 60
            return "%d:%02d:%02d".format(h, m, sec)
        }

        private fun escapeXml(s: String): String =
            s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;")

        /** Metadatos DIDL-Lite mínimos para SetAVTransportURI. */
        fun didlLite(url: String, title: String): String {
            val mime = when {
                url.lowercase().contains(".mp3") -> "audio/mpeg"
                url.lowercase().contains(".m4a") -> "audio/mp4"
                else -> "video/mp4"
            }
            val cls = if (mime.startsWith("audio")) {
                "object.item.audioItem.musicTrack"
            } else {
                "object.item.videoItem"
            }
            return "<DIDL-Lite xmlns=\"urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/\" " +
                "xmlns:dc=\"http://purl.org/dc/elements/1.1/\" " +
                "xmlns:upnp=\"urn:schemas-upnp-org:metadata-1-0/upnp/\">" +
                "<item id=\"0\" parentID=\"0\" restricted=\"1\">" +
                "<dc:title>${escapeXml(title)}</dc:title>" +
                "<upnp:class>$cls</upnp:class>" +
                "<res protocolInfo=\"http-get:*:${mime}:*\">${escapeXml(url)}</res>" +
                "</item></DIDL-Lite>"
        }
    }
}
