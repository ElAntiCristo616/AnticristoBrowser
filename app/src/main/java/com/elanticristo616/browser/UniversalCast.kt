package com.elanticristo616.browser

import android.content.Context

/**
 * CuteNovna Browser v1.1.0 — orquestador de la transmisión.
 *
 * El resto de la app solo habla con UniversalCast: manda el video a
 * la tele vinculada (sea Roku, Chromecast o DLNA), pausa, sigue,
 * para, salta a una posición, adelanta/atrasa y cambia la velocidad.
 * La tele activa vive en CastSession.activeTarget y se recuerda en
 * TargetStore.
 */
object UniversalCast {

    fun activeTarget(): CastTarget? = CastSession.activeTarget

    /** "Roku • Sharp Roku TV" o "Sin tele". */
    fun activeLabel(): String {
        val target = CastSession.activeTarget ?: return "Sin tele"
        return "${target.protocol} • ${target.name}"
    }

    fun hasTarget(): Boolean = CastSession.activeTarget != null

    fun supportsSpeed(): Boolean = CastSession.activeTarget?.supportsSpeed() == true

    /** Cierra la sesión Cast de la tele activa (si es Chromecast)
     *  sin esperar al stop: para onDestroy y cambios de tele, donde
     *  una corrutina no llegaría a tiempo antes de que la actividad
     *  muera. v1.1.1: antes nadie llamaba a disconnect(), la sesión
     *  quedaba viva y el receptor se quedaba en su pantalla idle
     *  aunque se cerrara la app. */
    fun disconnectCastNow() {
        if (CastSession.activeTarget !is GoogleCastTarget) return
        GoogleCastManager.disconnect()
    }

    /** Suelta la sesión Cast anterior si se vincula una tele
     *  distinta (otro protocolo u otra ruta). Se llama ANTES de
     *  reasignar CastSession.activeTarget. Si es la misma tele, no
     *  se toca nada. */
    fun releasePreviousCast(newTarget: CastTarget) {
        val old = CastSession.activeTarget ?: return
        if (old is GoogleCastTarget && old.id != newTarget.id) {
            GoogleCastManager.disconnect()
        }
    }

    /** Desconecta por completo de la tele activa: para la
     *  reproducción, cierra la sesión Cast si la hay y olvida la
     *  tele vinculada (CastSession + TargetStore). Lo usa el botón
     *  «Desconectar» del reproductor. */
    suspend fun disconnectAll(context: Context): CastHelper.CastResult {
        val target = CastSession.activeTarget
        if (target != null) {
            try { target.command("stop") } catch (e: Exception) { }
        }
        if (target is GoogleCastTarget) GoogleCastManager.disconnect()
        CastSession.activeTarget = null
        TargetStore.clear(context)
        return CastHelper.CastResult(true, "Desconectado de la tele.")
    }

    suspend fun castVideo(
        context: Context,
        url: String,
        title: String,
        fmt: String,
        headers: Map<String, String>? = null
    ): CastHelper.CastResult {
        val target = CastSession.activeTarget
            ?: return CastHelper.CastResult(false, "Primero conecta una tele.")
        return try {
            target.castVideo(context, url, title, fmt, headers)
        } catch (e: Exception) {
            CastHelper.CastResult(false, "No se pudo mandar el video: ${e.message}")
        }
    }

    suspend fun command(cmd: String): CastHelper.CastResult {
        val target = CastSession.activeTarget
            ?: return CastHelper.CastResult(false, "Nada transmitiendo ahora.")
        return try {
            target.command(cmd)
        } catch (e: Exception) {
            CastHelper.CastResult(false, "No se pudo mandar el comando: ${e.message}")
        }
    }

    suspend fun seekTo(seconds: Int): CastHelper.CastResult {
        val target = CastSession.activeTarget
            ?: return CastHelper.CastResult(false, "Nada transmitiendo ahora.")
        return try {
            target.seekTo(seconds)
        } catch (e: Exception) {
            CastHelper.CastResult(false, "No se pudo saltar a esa posición.")
        }
    }

    suspend fun seekBy(deltaSeconds: Int): CastHelper.CastResult {
        val target = CastSession.activeTarget
            ?: return CastHelper.CastResult(false, "Nada transmitiendo ahora.")
        return try {
            target.seekBy(deltaSeconds)
        } catch (e: Exception) {
            CastHelper.CastResult(false, "No se pudo adelantar ni atrasar.")
        }
    }

    /** Velocidad 0.5x–2x; solo Chromecast la soporta de verdad. */
    suspend fun setSpeed(rate: Float): CastHelper.CastResult {
        val target = CastSession.activeTarget
            ?: return CastHelper.CastResult(false, "Nada transmitiendo ahora.")
        if (!target.supportsSpeed()) {
            return CastHelper.CastResult(
                false,
                "Esta tele no permite cambiar la velocidad."
            )
        }
        return try {
            target.setSpeed(rate)
        } catch (e: Exception) {
            CastHelper.CastResult(false, "No se pudo cambiar la velocidad.")
        }
    }

    suspend fun playbackState(): PlaybackState? {
        return try {
            CastSession.activeTarget?.playbackState()
        } catch (e: Exception) {
            null
        }
    }
}
