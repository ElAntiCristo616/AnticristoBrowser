package com.elanticristo616.browser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * CuteNovna Browser v1.1.0 — tests del filtro de anuncios.
 * El selector de videos NUNCA debe mostrar anuncios.
 */
class AdFilterTest {

    @Test
    fun `urls de redes de anuncios quedan fuera`() {
        assertTrue(AdFilter.isAdUrl("https://googleads.g.doubleclick.net/pagead/ads?x=1"))
        assertTrue(AdFilter.isAdUrl("https://imasdk.googleapis.com/js/sdkloader/ima3.js"))
        assertTrue(AdFilter.isAdUrl("https://cdn.example.com/vast/preroll.mp4"))
        assertTrue(AdFilter.isAdUrl("https://ads.example.com/midroll/video.mp4"))
        assertTrue(AdFilter.isAdUrl("https://srv.exoclick.com/ad.mp4"))
        assertTrue(AdFilter.isAdUrl("https://cdn.taboola.com/libtrc/x.js"))
    }

    @Test
    fun `urls normales de video NO se marcan como anuncio`() {
        assertFalse(AdFilter.isAdUrl("https://cdn.example.com/videos/load-video-720p.mp4"))
        assertFalse(AdFilter.isAdUrl("https://cdn.example.com/download/file.mp4"))
        assertFalse(AdFilter.isAdUrl("https://example.com/media/video.m3u8"))
        assertFalse(AdFilter.isAdUrl("https://r1---sn.example.com/videoplayback?expire=1"))
        assertFalse(AdFilter.isAdUrl("https://example.com/hls/master.m3u8"))
    }

    @Test
    fun `titulos de anuncio quedan fuera`() {
        assertTrue(AdFilter.isAdTitle("Advertisement"))
        assertTrue(AdFilter.isAdTitle("Anuncio de prueba"))
        assertTrue(AdFilter.isAdTitle("Sponsored content"))
        assertTrue(AdFilter.isAdTitle("Video patrocinado por la marca"))
        assertTrue(AdFilter.isAdTitle("Publicidad de prueba"))
    }

    @Test
    fun `titulos normales NO se marcan como anuncio`() {
        assertFalse(AdFilter.isAdTitle("Video principal"))
        assertFalse(AdFilter.isAdTitle("Episodio 12 - La batalla"))
        assertFalse(AdFilter.isAdTitle("https://example.com/video.mp4"))
        assertFalse(AdFilter.isAdTitle("Goku vs Vegeta"))
    }

    @Test
    fun `el js de deteccion de anuncios trae los marcadores`() {
        assertTrue(AdFilter.AD_CHECK_JS.contains("esAnuncio"))
        assertTrue(AdFilter.AD_CHECK_JS.contains("doubleclick"))
        assertTrue(AdFilter.AD_CHECK_JS.contains("preroll"))
    }

    @Test
    fun `el js de deteccion trae las senales de anuncio de YouTube`() {
        // El pre-roll sale del mismo servidor que el video: solo el
        // reproductor lo delata (clases ad-showing / ytp-ad-*).
        assertTrue(AdFilter.AD_CHECK_JS.contains("ytp-ad"))
        assertTrue(AdFilter.AD_CHECK_JS.contains("ad-showing"))
        assertTrue(AdFilter.AD_CHECK_JS.contains("ad-interrupting"))
    }

    @Test
    fun `el js para esconder anuncios usa esAnuncio y protege el video real`() {
        assertTrue(AdFilter.HIDE_ADS_JS.contains("esAnuncio"))
        assertTrue(AdFilter.HIDE_ADS_JS.contains("hasBigVideo"))
        assertTrue(AdFilter.HIDE_ADS_JS.contains("display','none','important"))
        assertTrue(AdFilter.HIDE_ADS_JS.contains("VIDEO"))
    }
}
