package com.elanticristo616.browser

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * CuteNovna Browser v1.1.0 — FAVORITOS E HISTORIAL.
 *
 * Guardados en local (SharedPreferences "cutenovna_library"), en el
 * teléfono, sin cuenta ni servidor: cada quien tiene los suyos.
 * Favoritos: sitios guardados con nombre, URL e icono de marca
 * (YouTube, TikTok, Instagram, X, Facebook o globo genérico).
 * Historial: links transmitidos/abiertos, con fecha y hora, máximo
 * 50, el más reciente primero.
 */
object LibraryStore {

    private const val PREFS = "cutenovna_library"
    private const val KEY_FAVS = "favorites_json"
    private const val KEY_HIST = "history_json"
    private const val MAX_HISTORY = 50

    data class Favorite(val name: String, val url: String, val brand: String)
    data class HistoryItem(val url: String, val title: String, val timeMs: Long)

    /** Marca por host: youtube, tiktok, instagram, x, facebook o web. */
    fun brandFor(url: String): String {
        val host = try {
            android.net.Uri.parse(url).host?.lowercase() ?: ""
        } catch (e: Exception) {
            ""
        }
        return when {
            "youtube.com" in host || "youtu.be" in host -> "youtube"
            "tiktok.com" in host -> "tiktok"
            "instagram.com" in host -> "instagram"
            host == "x.com" || host.endsWith(".x.com") || "twitter.com" in host -> "x"
            "facebook.com" in host || "fb.com" in host -> "facebook"
            else -> "web"
        }
    }

    /** Drawable del icono de marca (o globo genérico). */
    fun brandIconRes(brand: String): Int = when (brand) {
        "youtube" -> R.drawable.ic_brand_youtube
        "tiktok" -> R.drawable.ic_brand_tiktok
        "instagram" -> R.drawable.ic_brand_instagram
        "x" -> R.drawable.ic_brand_x
        "facebook" -> R.drawable.ic_brand_facebook
        else -> R.drawable.ic_globe
    }

    /** Nombre legible de un sitio por su host. */
    fun displayNameFor(url: String): String {
        val host = try {
            android.net.Uri.parse(url).host?.lowercase() ?: ""
        } catch (e: Exception) {
            ""
        }
        return when {
            host.isBlank() -> url
            "youtube.com" in host || "youtu.be" in host -> "YouTube"
            "tiktok.com" in host -> "TikTok"
            "instagram.com" in host -> "Instagram"
            host == "x.com" || host.endsWith(".x.com") -> "X"
            "facebook.com" in host -> "Facebook"
            else -> host.removePrefix("www.")
        }
    }

    fun favorites(context: Context): List<Favorite> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_FAVS, null) ?: return emptyList()
        val out = mutableListOf<Favorite>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val url = o.optString("url", "")
                if (url.isBlank()) continue
                out += Favorite(
                    o.optString("name", displayNameFor(url)),
                    url,
                    o.optString("brand", brandFor(url))
                )
            }
        } catch (e: Exception) {
            // JSON roto: se empieza de cero
        }
        return out
    }

    /** Agrega un favorito. False si esa URL ya estaba guardada. */
    fun addFavorite(context: Context, name: String, url: String): Boolean {
        val clean = url.trim()
        if (clean.isBlank()) return false
        val current = favorites(context).toMutableList()
        if (current.any { it.url.equals(clean, ignoreCase = true) }) return false
        current += Favorite(
            name.ifBlank { displayNameFor(clean) },
            clean,
            brandFor(clean)
        )
        saveFavorites(context, current)
        return true
    }

    fun removeFavorite(context: Context, url: String) {
        val current = favorites(context).filterNot {
            it.url.equals(url, ignoreCase = true)
        }
        saveFavorites(context, current)
    }

    private fun saveFavorites(context: Context, list: List<Favorite>) {
        val arr = JSONArray()
        for (f in list) {
            arr.put(JSONObject().apply {
                put("name", f.name)
                put("url", f.url)
                put("brand", f.brand)
            })
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_FAVS, arr.toString()).apply()
    }

    fun history(context: Context): List<HistoryItem> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_HIST, null) ?: return emptyList()
        val out = mutableListOf<HistoryItem>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val url = o.optString("url", "")
                if (url.isBlank()) continue
                out += HistoryItem(url, o.optString("title", ""), o.optLong("time", 0L))
            }
        } catch (e: Exception) {
            // JSON roto: se empieza de cero
        }
        return out.sortedByDescending { it.timeMs }
    }

    /** Agrega un link al historial (el más reciente primero, sin
     *  duplicados: si ya estaba, se mueve arriba con la hora nueva). */
    fun addHistory(context: Context, url: String, title: String = "") {
        val clean = url.trim()
        if (clean.isBlank()) return
        val current = history(context).filterNot {
            it.url.equals(clean, ignoreCase = true)
        }.toMutableList()
        current.add(0, HistoryItem(clean, title, System.currentTimeMillis()))
        val trimmed = current.take(MAX_HISTORY)
        val arr = JSONArray()
        for (h in trimmed) {
            arr.put(JSONObject().apply {
                put("url", h.url)
                put("title", h.title)
                put("time", h.timeMs)
            })
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_HIST, arr.toString()).apply()
    }

    fun removeHistory(context: Context, url: String) {
        val current = history(context).filterNot {
            it.url.equals(url, ignoreCase = true)
        }
        val arr = JSONArray()
        for (h in current) {
            arr.put(JSONObject().apply {
                put("url", h.url)
                put("title", h.title)
                put("time", h.timeMs)
            })
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_HIST, arr.toString()).apply()
    }

    fun clearHistory(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_HIST, "[]").apply()
    }

    /** "Hoy 14:32", "Ayer 21:15" o "12/11 09:05". */
    fun formatTime(timeMs: Long): String {
        if (timeMs <= 0) return ""
        val now = System.currentTimeMillis()
        val cal = java.util.Calendar.getInstance()
        val todayStart = cal.apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        val day = 24L * 60 * 60 * 1000
        val hm = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timeMs))
        return when {
            timeMs >= todayStart -> "Hoy $hm"
            timeMs >= todayStart - day -> "Ayer $hm"
            else -> SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(timeMs))
        }
    }
}
