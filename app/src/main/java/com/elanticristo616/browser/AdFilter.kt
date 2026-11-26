package com.elanticristo616.browser

/**
 * CuteNovna Browser v1.1.0 — FILTRO DE ANUNCIOS.
 *
 * El selector de videos NUNCA muestra anuncios: se filtran las URLs
 * de video que huelen a publicidad (redes de anuncios, VAST,
 * preroll/midroll), los <video> dentro de contenedores de anuncios,
 * los videos ocultos o muy cortos (< 10 s) y los títulos que dicen
 * "Anuncio"/"Publicidad". Nunca se usa "ad" solo como marcador:
 * rompería URLs normales ("load", "download"...).
 *
 * Extraído a un objeto propio para poder probarlo con tests.
 */
object AdFilter {

    /** Marcadores de publicidad en URLs de video. */
    val URL_MARKERS = listOf(
        "doubleclick", "googlesyndication", "googleads", "imasdk", "ima3",
        "vast", "vpaid", "preroll", "midroll", "postroll", "adserver",
        "adservice", "adsense", "adtech", "advert", "sponsor", "criteo",
        "taboola", "outbrain", "adnxs", "moatads", "adbreak", "ads.",
        ".ads.", "/ads/", "pubmatic", "rubiconproject", "smartadserver",
        "adform", "flashtalking", "exoclick", "adcash", "popads",
        "trafficjunky", "juicyads", "adsterra", "propellerads",
        "hilltopads", "clickadu", "popunder", "popup", "banner",
        "promo", "teaser", "prebid", "dfp", "adroll", "adsystem",
        "googletag", "googletagservices", "advertising",
        "mgid", "adblade", "revcontent", "amazon-adsystem",
        "adsafeprotected", "scorecardresearch", "zedo", "adzerk",
        "yieldmo", "sharethrough", "lijit", "sovrn", "openx",
        "appnexus", "triplelift", "indexexchange", "spotx",
        "freewheel", "springserve", "aniview", "vidazoo",
        "vungle", "unityads", "applovin", "ironsource",
        "chartboost", "tapjoy", "inmobi", "smaato", "mopub",
        "interstitial", "outstream", "instream", "videoad",
        "ad-break", "ad_break", "ad-slot", "adslot"
    )

    /** ¿La URL huele a anuncio? */
    fun isAdUrl(u: String): Boolean {
        val lower = u.lowercase()
        return URL_MARKERS.any { lower.contains(it) }
    }

    /** ¿El título dice que es un anuncio? */
    fun isAdTitle(title: String): Boolean {
        val lower = title.lowercase()
        return lower.contains("anuncio") || lower.contains("advertisement") ||
            lower.contains("publicidad") || lower.contains("sponsored") ||
            lower.contains("patrocinado") || lower.contains("ad break")
    }

    /** ¿El póster viene de un servidor de anuncios? */
    fun isAdPoster(u: String): Boolean = isAdUrl(u)

    /**
     * Función JS esAnuncio(el) para el escaneo de la página: sube
     * hasta 6 padres buscando clase/id de anuncio, atributos
     * data-ad, aria-label/título que digan anuncio, e iframes de
     * redes de publicidad. Se inyecta tal cual en el JS de
     * BrowserActivity.scanPageForVideos.
     */
    const val AD_CHECK_JS = """
function clsStr(el){var c='';try{c=(el.className&&el.className.baseVal!==undefined)?el.className.baseVal:(el.className||'');}catch(e){}return (''+c+' '+(el.id||'')).toLowerCase();}
function esAnuncio(el){
var n=el,d=0;
while(n&&d<6){
var s=clsStr(n);
if(/(^|[\s\-_])(ad|ads|advert|advertisement|sponsor|preroll|midroll|postroll|banner|promo|teaser|popup|popunder|commercial|interstitial|outstream|instream|adslot|ad-break|ad-container|video-ad)([\s\-_]|$)/.test(s))return true;
try{
if(n.dataset&&(n.dataset.ad!==undefined||n.dataset.adSlot!==undefined||n.dataset.adClient!==undefined))return true;
var al=((n.getAttribute&&n.getAttribute('aria-label'))||'')+' '+((n.getAttribute&&n.getAttribute('title'))||'');
al=al.toLowerCase();
if(al.indexOf('anuncio')>=0||al.indexOf('advertisement')>=0||al.indexOf('publicidad')>=0||al.indexOf('sponsored')>=0)return true;
}catch(e){}
if(n.tagName==='IFRAME'){
var src=(n.src||'').toLowerCase();
if(src.indexOf('doubleclick')>=0||src.indexOf('googlesyndication')>=0||src.indexOf('ads')>=0||src.indexOf('vast')>=0||src.indexOf('exoclick')>=0||src.indexOf('popads')>=0||src.indexOf('taboola')>=0||src.indexOf('outbrain')>=0||src.indexOf('adnxs')>=0||src.indexOf('criteo')>=0||src.indexOf('mgid')>=0||src.indexOf('adsterra')>=0||src.indexOf('propellerads')>=0||src.indexOf('trafficjunky')>=0||src.indexOf('juicyads')>=0||src.indexOf('smartadserver')>=0||src.indexOf('rubiconproject')>=0||src.indexOf('pubmatic')>=0||src.indexOf('prebid')>=0||src.indexOf('adform')>=0||src.indexOf('flashtalking')>=0||src.indexOf('imasdk')>=0||src.indexOf('googleads')>=0||src.indexOf('adservice')>=0)return true;
}
n=n.parentElement;d++;
}
return false;
}
"""
}
