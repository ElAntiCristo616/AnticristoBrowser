package com.elanticristo616.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.BufferedReader
import java.io.File
import java.io.StringReader

/**
 * CuteNovna Browser v1.1.4 — tests del bloqueo de anuncios.
 * La lista real (assets/duckduckgo-blocklist.txt) se carga en los
 * tests y se comprueba contra ella: el video principal, los hosts
 * de video, el login y la red local no se bloquean jamás.
 */
class BlocklistTest {

    private lateinit var assetDomains: Set<String>

    @Before
    fun cargarListaReal() {
        val asset = File("src/main/assets/duckduckgo-blocklist.txt")
        assetDomains = if (asset.exists()) {
            Blocklist.loadFromReader(asset.bufferedReader())
        } else {
            // Si el test corre desde otro directorio de trabajo.
            Blocklist.loadFromReader(
                File("app/src/main/assets/duckduckgo-blocklist.txt")
                    .bufferedReader()
            )
        }
        Blocklist.setDomainsForTest(assetDomains)
    }

    @Test
    fun `la lista real trae los dominios de anuncios conocidos`() {
        assertTrue(assetDomains.contains("doubleclick.net"))
        assertTrue(assetDomains.contains("googlesyndication.com"))
        assertTrue(assetDomains.contains("exoclick.com"))
        assertTrue(assetDomains.contains("popads.net"))
        assertTrue(assetDomains.contains("adsterra.com"))
        assertTrue(assetDomains.contains("propellerads.com"))
        assertTrue(assetDomains.contains("trafficjunky.com"))
    }

    @Test
    fun `la lista real NO trae dominios de video ni de login`() {
        val prohibidos = listOf(
            "googlevideo.com", "youtube.com", "youtu.be", "ytimg.com",
            "ggpht.com", "gvt1.com", "duckduckgo.com",
            "accounts.google.com", "ssl.gstatic.com",
            "fonts.gstatic.com", "fonts.googleapis.com", "gstatic.com",
            "tiktok.com", "tiktokcdn.com", "fbcdn.net",
            "cdninstagram.com", "roku.com", "localhost"
        )
        for (d in prohibidos) {
            assertFalse("la lista no debe traer $d", assetDomains.contains(d))
        }
    }

    @Test
    fun `matching por sufijo bloquea subdominios pero no parecidos`() {
        Blocklist.setDomainsForTest(setOf("doubleclick.net", "exoclick.com"))
        assertTrue(Blocklist.isBlockedHost("doubleclick.net"))
        assertTrue(Blocklist.isBlockedHost("ads.doubleclick.net"))
        assertTrue(Blocklist.isBlockedHost("g.doubleclick.net"))
        assertTrue(Blocklist.isBlockedHost("securepubads.g.doubleclick.net"))
        assertTrue(Blocklist.isBlockedHost("srv.exoclick.com"))
        assertFalse(Blocklist.isBlockedHost("notdoubleclick.net"))
        assertFalse(Blocklist.isBlockedHost("doubleclick.net.evil.com"))
        assertFalse(Blocklist.isBlockedHost("example.com"))
        assertFalse(Blocklist.isBlockedHost("exoclick.com.evil.com"))
    }

    @Test
    fun `hosts de video y login nunca se bloquean aunque esten en la lista`() {
        // Aunque alguien metiera youtube.com en la lista, la red de
        // seguridad de nunca-bloquear lo protege.
        Blocklist.setDomainsForTest(
            setOf("youtube.com", "googlevideo.com", "duckduckgo.com")
        )
        assertFalse(Blocklist.isBlockedHost("youtube.com"))
        assertFalse(Blocklist.isBlockedHost("www.youtube.com"))
        assertFalse(Blocklist.isBlockedHost("r1---sn.googlevideo.com"))
        assertFalse(Blocklist.isBlockedHost("duckduckgo.com"))
        assertFalse(Blocklist.isBlockedHost("accounts.google.com"))
        assertFalse(Blocklist.isBlockedHost("ssl.gstatic.com"))
    }

    @Test
    fun `la red local nunca se bloquea`() {
        Blocklist.setDomainsForTest(setOf("192.168.1.78", "127.0.0.1"))
        assertFalse(Blocklist.isBlockedHost("localhost"))
        assertFalse(Blocklist.isBlockedHost("127.0.0.1"))
        assertFalse(Blocklist.isBlockedHost("192.168.1.78"))
        assertFalse(Blocklist.isBlockedHost("10.0.0.5"))
        assertFalse(Blocklist.isBlockedHost("172.16.0.9"))
    }

    @Test
    fun `peticiones a dominios de anuncios se bloquean, el marco principal no`() {
        assertTrue(
            Blocklist.shouldBlockRequest(
                "https://googleads.g.doubleclick.net/pagead/ads?x=1", false
            )
        )
        assertTrue(
            Blocklist.shouldBlockRequest(
                "https://cdn.example.com/ima3.js", false
            )
        )
        assertTrue(
            Blocklist.shouldBlockRequest(
                "https://srv.exoclick.com/ad.js", false
            )
        )
        // El marco principal lo veta shouldOverrideUrlLoading, no esto.
        assertFalse(
            Blocklist.shouldBlockRequest(
                "https://googleads.g.doubleclick.net/pagead/ads?x=1", true
            )
        )
        // Peticiones normales pasan.
        assertFalse(
            Blocklist.shouldBlockRequest("https://example.com/app.js", false)
        )
        assertFalse(
            Blocklist.shouldBlockRequest(
                "https://www.youtube.com/s/player/abc/player.js", false
            )
        )
    }

    @Test
    fun `las urls de video y los segmentos nunca se bloquean`() {
        val videos = listOf(
            "https://ads.doubleclick.net/video.mp4",
            "https://srv.exoclick.com/hls/master.m3u8",
            "https://r1---sn.googlevideo.com/videoplayback?expire=1",
            "https://cdn.example.com/seg-0001.ts",
            "https://cdn.example.com/seg-0001.m4s",
            "https://cdn.example.com/video.mpd?x=1"
        )
        for (u in videos) {
            assertFalse(
                "el video/segmento no se bloquea: $u",
                Blocklist.shouldBlockRequest(u, false)
            )
            assertFalse(
                "la navegación a video no se veta: $u",
                Blocklist.shouldVetoNavigation(u)
            )
        }
    }

    @Test
    fun `navegaciones a dominios de anuncios se vetan, las legitimas no`() {
        assertTrue(
            Blocklist.shouldVetoNavigation("https://ads.exoclick.com/promo/x")
        )
        assertTrue(
            Blocklist.shouldVetoNavigation("https://popads.net/pop?x=1")
        )
        assertFalse(
            Blocklist.shouldVetoNavigation("https://example.com/promo/oferta")
        )
        assertFalse(
            Blocklist.shouldVetoNavigation("https://www.youtube.com/watch?v=x")
        )
        assertFalse(
            Blocklist.shouldVetoNavigation("https://duckduckgo.com/?q=pelis")
        )
        assertFalse(
            Blocklist.shouldVetoNavigation("https://cuevana3.io/pelicula/x")
        )
    }

    @Test
    fun `el cargador ignora comentarios y lineas vacias`() {
        val reader: BufferedReader = BufferedReader(
            StringReader("# comentario\n\ndoubleclick.net\n\nEXOCLOCK.COM\n")
        )
        val loaded = Blocklist.loadFromReader(reader)
        assertEquals(setOf("doubleclick.net", "exoclock.com"), loaded)
    }
}
