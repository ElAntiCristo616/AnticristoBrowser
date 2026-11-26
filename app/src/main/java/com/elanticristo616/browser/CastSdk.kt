package com.elanticristo616.browser

import android.content.Context
import androidx.mediarouter.media.MediaRouter
import androidx.mediarouter.media.MediaRouteSelector
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.MediaSeekOptions
import com.google.android.gms.cast.MediaStatus
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession as GmsCastSession
import com.google.android.gms.cast.framework.OptionsProvider
import com.google.android.gms.cast.framework.CastOptions
import com.google.android.gms.cast.framework.SessionManager
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.cast.framework.SessionProvider
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeUnit

/**
 * Proveedor de opciones del SDK oficial de Google Cast.
 * Receptor por defecto (DEFAULT_MEDIA_RECEIVER_APPLICATION_ID):
 * reproduce URLs directas de video (MP4, HLS, DASH) sin app propia
 * en la tele. Se declara en el AndroidManifest.
 */
class CastOptionsProvider : OptionsProvider {
    override fun getCastOptions(context: Context): CastOptions =
        CastOptions.Builder()
            .setReceiverApplicationId(
                CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID
            )
            .build()

    override fun getAdditionalSessionProviders(context: Context): List<SessionProvider>? = null
}

/**
 * CuteNovna Browser v1.1.0 — GOOGLE CAST / CHROMECAST.
 *
 * SDK oficial de Cast (play-services-cast-framework): descubrimiento
 * con MediaRouter, sesión con el receptor por defecto y control con
 * RemoteMediaClient (play/pausa/seek/velocidad/posición).
 *
 * NOTA: YouTube NO se lanza por DIAL en Chromecast aunque el
 * protocolo lo permite: la app de YouTube que DIAL abre NO la puede
 * controlar nuestra app (ni pausa, ni seek, ni velocidad), y el
 * reproductor quedaría inútil, que es justo lo que se arregla en
 * v1.1.0. Por eso YouTube también se manda como URL directa de
 * yt-dlp al receptor por defecto, donde TODO el reproductor sí
 * funciona.
 */
object GoogleCastManager {

    data class CastRoute(val id: String, val name: String, val route: MediaRouter.RouteInfo)

    private var castContext: CastContext? = null
    private var sessionManager: SessionManager? = null
    private var mediaRouter: MediaRouter? = null
    private var selector: MediaRouteSelector? = null
    private var currentSession: GmsCastSession? = null
    private var sessionDeferred: CompletableDeferred<GmsCastSession?>? = null
    private var routesChanged: ((List<CastRoute>) -> Unit)? = null
    private val cachedRoutes = mutableListOf<CastRoute>()

    fun cachedRoutes(): List<CastRoute> = cachedRoutes.toList()

    fun onRoutesChanged(listener: ((List<CastRoute>) -> Unit)?) {
        routesChanged = listener
    }

    /**
     * Inicializa el SDK (HILO PRINCIPAL: CastContext lo exige).
     * False si no hay Google Play Services o falta el provider:
     * en ese teléfono Chromecast no está disponible.
     */
    fun ensure(context: Context): Boolean {
        if (castContext != null) return true
        return try {
            val ctx = CastContext.getSharedInstance(context.applicationContext)
            castContext = ctx
            sessionManager = ctx.sessionManager
            sessionManager?.addSessionManagerListener(
                sessionListener, GmsCastSession::class.java
            )
            mediaRouter = MediaRouter.getInstance(context.applicationContext)
            selector = MediaRouteSelector.Builder()
                .addControlCategory(
                    CastMediaControlIntent.categoryForCast(
                        CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID
                    )
                )
                .build()
            mediaRouter?.addCallback(
                selector!!, routerCallback,
                MediaRouter.CALLBACK_FLAG_PERFORM_ACTIVE_SCAN
            )
            refreshRoutes()
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun refreshRoutes() {
        val sel = selector ?: return
        val router = mediaRouter ?: return
        cachedRoutes.clear()
        for (route in router.routes) {
            if (!route.isEnabled) continue
            if (!route.matchesSelector(sel)) continue
            cachedRoutes += CastRoute(
                route.id, route.name?.toString() ?: "Chromecast", route
            )
        }
        routesChanged?.invoke(cachedRoutes.toList())
    }

    private val routerCallback = object : MediaRouter.Callback() {
        override fun onRouteAdded(router: MediaRouter, route: MediaRouter.RouteInfo) {
            refreshRoutes()
        }

        override fun onRouteRemoved(router: MediaRouter, route: MediaRouter.RouteInfo) {
            refreshRoutes()
        }

        override fun onRouteChanged(router: MediaRouter, route: MediaRouter.RouteInfo) {
            refreshRoutes()
        }
    }

    private val sessionListener = object : SessionManagerListener<GmsCastSession> {
        override fun onSessionStarted(session: GmsCastSession, sessionId: String) {
            currentSession = session
            sessionDeferred?.complete(session)
            sessionDeferred = null
        }

        override fun onSessionResumed(session: GmsCastSession, wasSuspended: Boolean) {
            currentSession = session
            sessionDeferred?.complete(session)
            sessionDeferred = null
        }

        override fun onSessionStartFailed(session: GmsCastSession, error: Int) {
            sessionDeferred?.complete(null)
            sessionDeferred = null
        }

        override fun onSessionEnded(session: GmsCastSession, error: Int) {
            if (currentSession == session) currentSession = null
        }

        override fun onSessionEnding(session: GmsCastSession) {}

        override fun onSessionResumeFailed(session: GmsCastSession, error: Int) {
            sessionDeferred?.complete(null)
            sessionDeferred = null
        }

        override fun onSessionStarting(session: GmsCastSession) {}

        override fun onSessionResuming(session: GmsCastSession, sessionId: String) {}

        override fun onSessionSuspended(session: GmsCastSession, reason: Int) {}
    }

    /**
     * Conecta con una ruta: la selecciona y espera la sesión (hasta
     * 15 s). Debe llamarse desde el hilo principal.
     */
    suspend fun connect(context: Context, route: CastRoute): Boolean {
        if (!ensure(context)) return false
        currentSession?.let { return true }
        sessionDeferred = CompletableDeferred()
        try {
            withContext(Dispatchers.Main) {
                mediaRouter?.selectRoute(route.route)
            }
        } catch (e: Exception) {
            return false
        }
        val session = withTimeoutOrNull(15_000) { sessionDeferred?.await() }
        return session != null
    }

    fun disconnect() {
        try { sessionManager?.endCurrentSession(true) } catch (e: Exception) { }
        currentSession = null
    }

    private fun remoteClient(): RemoteMediaClient? = try {
        currentSession?.remoteMediaClient
    } catch (e: Exception) {
        null
    }

    /** Manda reproducir una URL directa en el receptor por defecto. */
    suspend fun load(url: String, title: String, fmt: String): Boolean =
        withContext(Dispatchers.IO) {
            val client = remoteClient() ?: return@withContext false
            val mediaInfo = MediaInfo.Builder(url)
                .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
                .setContentType(contentTypeFor(fmt, url))
                .setMetadata(
                    MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE).apply {
                        putString(MediaMetadata.KEY_TITLE, title)
                    }
                )
                .build()
            val request = MediaLoadRequestData.Builder()
                .setMediaInfo(mediaInfo)
                .setAutoplay(true)
                .build()
            try {
                val result = client.load(request).await(15, TimeUnit.SECONDS)
                result.status.isSuccess
            } catch (e: Exception) {
                false
            }
        }

    suspend fun pause(): Boolean = withContext(Dispatchers.IO) {
        try {
            remoteClient()?.pause()?.await(10, TimeUnit.SECONDS)
                ?.status?.isSuccess == true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun resume(): Boolean = withContext(Dispatchers.IO) {
        try {
            remoteClient()?.play()?.await(10, TimeUnit.SECONDS)
                ?.status?.isSuccess == true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun stop(): Boolean = withContext(Dispatchers.IO) {
        try {
            remoteClient()?.stop()?.await(10, TimeUnit.SECONDS)
                ?.status?.isSuccess == true
        } catch (e: Exception) {
            false
        }
    }

    /** Salta a una posición en segundos (seek exacto del SDK). */
    suspend fun seekTo(seconds: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val options = MediaSeekOptions.Builder()
                .setPosition(seconds.coerceAtLeast(0).toLong() * 1000)
                .build()
            remoteClient()?.seek(options)?.await(10, TimeUnit.SECONDS)
                ?.status?.isSuccess == true
        } catch (e: Exception) {
            false
        }
    }

    /** Velocidad 0.5x–2x (el receptor por defecto la soporta). */
    suspend fun setSpeed(rate: Float): Boolean = withContext(Dispatchers.IO) {
        try {
            remoteClient()?.setPlaybackRate(rate.toDouble())
                ?.await(10, TimeUnit.SECONDS)?.status?.isSuccess == true
        } catch (e: Exception) {
            false
        }
    }

    /** Posición y duración en segundos, o null sin sesión. */
    fun position(): Pair<Double, Double>? {
        val client = remoteClient() ?: return null
        return try {
            val pos = client.approximateStreamPosition
            val dur = client.streamDuration
            if (pos < 0 && dur <= 0) null
            else (pos.coerceAtLeast(0).toDouble() / 1000.0) to
                (dur.coerceAtLeast(0).toDouble() / 1000.0)
        } catch (e: Exception) {
            null
        }
    }

    fun isPlaying(): Boolean {
        return try {
            remoteClient()?.mediaStatus?.playerState == MediaStatus.PLAYER_STATE_PLAYING
        } catch (e: Exception) {
            false
        }
    }

    fun hasSession(): Boolean = currentSession != null

    private fun contentTypeFor(fmt: String, url: String): String {
        val lower = url.lowercase()
        return when {
            fmt == "hls" || lower.contains(".m3u8") -> "application/x-mpegURL"
            fmt == "dash" || lower.contains(".mpd") -> "application/dash+xml"
            fmt == "mp3" || lower.contains(".mp3") -> "audio/mpeg"
            else -> "video/mp4"
        }
    }
}
