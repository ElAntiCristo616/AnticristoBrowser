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
 * CuteNovna Browser v1.1.5 — tests del bloqueo de anuncios.
 * La lista real (assets/ad-blocklist.txt) se carga en los
 * tests y se comprueba contra ella: el video principal, los hosts
 * de video, el login y la red local no se bloquean jamás.
 */
class BlocklistTest {

    private lateinit var assetDomains: Set<String>

    @Before
    fun cargarListaReal() {
        val asset = File("src/main/assets/ad-blocklist.txt")
        assetDomains = if (asset.exists()) {
            Blocklist.loadFromReader(asset.bufferedReader())
        } else {
            // Si el test corre desde otro directorio de trabajo.
            Blocklist.loadFromReader(
                File("app/src/main/assets/ad-blocklist.txt")
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
            "cdninstagram.com", "roku.com", "localhost",
            // v1.1.5: el CDN de video de las paginas (ScatGold) y la
            // tienda no se bloquean jamas.
            "bkcdn.net", "bunnycdn.com", "aliexpress.com", "alicdn.com"
        )
        for (d in prohibidos) {
            assertFalse("la lista no debe traer $d", assetDomains.contains(d))
        }
    }

    @Test
    fun `la lista real es grande y trae EasyList mas el suplemento`() {
        // Miles de dominios: EasyList + tds + suplemento.
        assertTrue(
            "la lista debe traer miles de dominios, trae ${assetDomains.size}",
            assetDomains.size > 10000
        )
        assertTrue(assetDomains.contains("ad.aliexpress.com"))
        // s.click.aliexpress.com se reduce a su padre
        // click.aliexpress.com, que si esta en la lista.
        assertTrue(assetDomains.contains("click.aliexpress.com"))
        assertTrue(
            Blocklist.isBlockedHost("s.click.aliexpress.com")
        )
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

    @Test
    fun `el veto fuerte veta el salto a otro host con pinta de anuncio`() {
        // v1.1.5: navegacion del marco principal a OTRO host con
        // marcadores de anuncio -> veto, aunque el dominio no este
        // en la lista (el salto de la trampa del play).
        assertTrue(
            Blocklist.shouldVetoNavigation(
                "https://randon-ads.example/promo/oferta?x=1",
                "scat.gold"
            )
        )
        assertTrue(
            Blocklist.shouldVetoNavigation(
                "https://otra-pagina.example/vast/preroll",
                "scat.gold"
            )
        )
        // Mismo host con marcadores: NO se veta (la pagina puede
        // llevar "promo" o "banner" en su propia URL).
        assertFalse(
            Blocklist.shouldVetoNavigation(
                "https://scat.gold/promo/oferta",
                "scat.gold"
            )
        )
        assertFalse(
            Blocklist.shouldVetoNavigation(
                "https://cuevana3.io/pelicula/banner-x",
                "cuevana3.io"
            )
        )
        // Sin host actual conocido: solo aplica el veto por lista.
        assertFalse(
            Blocklist.shouldVetoNavigation(
                "https://randon-ads.example/promo/oferta?x=1",
                null
            )
        )
        // Video directo a otro host: nunca se veta.
        assertFalse(
            Blocklist.shouldVetoNavigation(
                "https://cdn.example.com/video.mp4",
                "scat.gold"
            )
        )
        // Host de nunca-bloquear con marcadores: tampoco se veta.
        assertFalse(
            Blocklist.shouldVetoNavigation(
                "https://www.youtube.com/watch?v=x&promo=1",
                "scat.gold"
            )
        )
    }

    @Test
    fun `los videos del cdn de la pagina no se bloquean ni se vetan`() {
        // v1.1.5: bkcdn.net es el CDN de video de la pagina: el
        // video principal puede vivir ahi. El arreglo del salto es
        // la trampa del play, no bloquear el CDN.
        val videos = listOf(
            "https://z6v2p9a8.bkcdn.net/videos/5f9e7f312dbceb9a98dee33f12ff46c1c1189512.mp4",
            "https://z6v2p9a8.bkcdn.net/videos/f95bced1177bb5b8b9b784cebe2dceeb45c0f442.mp4"
        )
        for (u in videos) {
            assertFalse(
                "el video del CDN no se bloquea: $u",
                Blocklist.shouldBlockRequest(u, false)
            )
            assertFalse(
                "la navegacion al video del CDN no se veta: $u",
                Blocklist.shouldVetoNavigation(u, "scat.gold")
            )
        }
        assertFalse(Blocklist.isBlockedHost("z6v2p9a8.bkcdn.net"))
        assertFalse(Blocklist.isBlockedHost("bkcdn.net"))
    }

    @Test
    fun `las urls de video estilo KVS se detectan como video directo`() {
        // v1.1.6: KVS (ScatGold) emite <source> con
        // .../get_file/.../video_720p.mp4/ (barra final) y el 302
        // puede ir a remote_control.php?file=...mp4: antes ninguna
        // de las dos pasaba isDirectVideoUrl/isMediaUrl y el
        // selector quedaba vacio aunque el video sonara.
        val kvs = listOf(
            "https://z6v2p9a8.bkcdn.net/get_file/123/abc123def456/0/456/456_720p.mp4/",
            "https://scat.gold/get_file/123/abc123def456/0/456/456.mp4/",
            "https://z6v2p9a8.bkcdn.net/remote_control.php?file=videos/456_720p.mp4&token=abc",
            "https://z6v2p9a8.bkcdn.net/videos/456_720p.mp4",
            "https://z6v2p9a8.bkcdn.net/videos/hls/456/hls.m3u8?token=abc",
            "https://z6v2p9a8.bkcdn.net/get_file/123/abc/0/456/hls.m3u8/"
        )
        for (u in kvs) {
            assertTrue("es video directo: $u", Blocklist.isVideoFileUrl(u))
            assertTrue("es medio: $u", Blocklist.isMediaUrl(u))
            assertFalse(
                "el video KVS no se bloquea: $u",
                Blocklist.shouldBlockRequest(u, false)
            )
            assertFalse(
                "la navegacion al video KVS no se veta: $u",
                Blocklist.shouldVetoNavigation(u, "scat.gold")
            )
        }
        // Una miniatura con .mp4 en el nombre NO es video.
        assertFalse(
            Blocklist.isVideoFileUrl(
                "https://cdn.example.com/thumbs/video.mp4.jpg"
            )
        )
        assertFalse(
            Blocklist.isMediaUrl(
                "https://cdn.example.com/thumbs/video.mp4.jpg"
            )
        )
        // Una busqueda con .mp4 en el texto tampoco.
        assertFalse(
            Blocklist.isVideoFileUrl("https://example.com/buscar?q=video.mp4")
        )
        // Ni una ruta que solo menciona .mp4.
        assertFalse(
            Blocklist.isVideoFileUrl("https://example.com/ver/video.mp4/info")
        )
        // (Que sea video por forma no quita el filtro de anuncios:
        // el intercept y el escaneo siguen descartando la URL si
        // huele a anuncio con AdFilter.isAdUrl.)
    }
}
