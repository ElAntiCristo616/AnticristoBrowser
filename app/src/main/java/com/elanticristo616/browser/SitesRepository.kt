package com.elanticristo616.browser

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.ImageView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

/**
 * La lista completa de sitios que yt-dlp conoce (1,731 entradas),
 * tal cual viene en la lista oficial, guardada en res/raw/sites.json.
 * Cada sitio lleva su imagen: el favicon por dominio desde
 * icons.duckduckgo.com (sin librerías externas).
 */
object SitesRepository {

    data class Site(val name: String, val domain: String) {
        /** Nombre bonito para mostrar (sin la variante :xxx). */
        val displayName: String get() = name.substringBefore(':')
        val faviconUrl: String
            get() = "https://icons.duckduckgo.com/ip3/$domain.ico"
        val siteUrl: String get() = "https://$domain"
    }

    @Volatile
    private var cached: List<Site>? = null

    /** Carga la lista (1,731 entradas) desde res/raw/sites.json. */
    fun load(context: Context): List<Site> {
        cached?.let { return it }
        val list = mutableListOf<Site>()
        try {
            val text = context.resources.openRawResource(R.raw.sites)
                .bufferedReader(Charsets.UTF_8).readText()
            val arr = JSONArray(text)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val name = obj.optString("n", "")
                val domain = obj.optString("d", "")
                if (name.isNotBlank()) list += Site(name, domain)
            }
        } catch (e: Exception) {
            // Sin lista: la app sigue funcionando como navegador
        }
        cached = list
        return list
    }

    // ---------- Favicons ----------

    private val iconCache = object : LinkedHashMap<String, Bitmap>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>): Boolean {
            return size > 120
        }
    }

    /** Baja el favicon del dominio y lo pone en el ImageView (con caché). */
    fun loadFavicon(scope: CoroutineScope, domain: String, view: ImageView) {
        if (domain.isBlank()) return
        synchronized(iconCache) {
            iconCache[domain]?.let { view.setImageBitmap(it); return }
        }
        view.setImageResource(android.R.drawable.ic_menu_gallery)
        scope.launch(Dispatchers.IO) {
            val bmp = fetchIcon("https://icons.duckduckgo.com/ip3/$domain.ico")
            if (bmp != null) {
                synchronized(iconCache) { iconCache[domain] = bmp }
                withContext(Dispatchers.Main) { view.setImageBitmap(bmp) }
            }
        }
    }

    private fun fetchIcon(url: String): Bitmap? {
        return try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 6000
            conn.readTimeout = 6000
            conn.setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36"
            )
            try {
                if (conn.responseCode != 200) return null
                BitmapFactory.decodeStream(conn.inputStream)
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            null
        }
    }
}
