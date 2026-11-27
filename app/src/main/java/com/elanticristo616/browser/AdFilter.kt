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
     *
     * v1.1.4: señales explícitas del reproductor de YouTube. El
     * anuncio pre-roll sale del mismo servidor que el video
     * (googlevideo.com) y no se distingue por dominio, pero el
     * reproductor SÍ lo marca: el contenedor lleva las clases
     * `ad-showing` / `ad-interrupting` y el overlay `ytp-ad-*`.
     * Cuando el anuncio suena, el <video> queda dentro de ese
     * contenedor y esAnuncio lo marca como anuncio: el selector no
     * lo muestra como tarjeta y el video principal queda intacto.
     */
    const val AD_CHECK_JS = """
function clsStr(el){var c='';try{c=(el.className&&el.className.baseVal!==undefined)?el.className.baseVal:(el.className||'');}catch(e){}return (''+c+' '+(el.id||'')).toLowerCase();}
function esAnuncio(el){
var n=el,d=0;
while(n&&d<6){
var s=clsStr(n);
if(s.indexOf('ytp-ad')>=0||s.indexOf('ad-showing')>=0||s.indexOf('ad-interrupting')>=0||s.indexOf('video-ads')>=0)return true;
if(/(^|[\s\-_])(ad|ads|advert|advertisement|sponsor|preroll|midroll|postroll|banner|promo|teaser|popup|popunder|commercial|interstitial|outstream|instream|adslot|ad-break|ad-container|video-ad)([\s\-_]|$)/.test(s))return true;
try{
if(n.dataset&&(n.dataset.ad!==undefined||n.dataset.adSlot!==undefined||n.dataset.adClient!==undefined))return true;
var al=((n.getAttribute&&n.getAttribute('aria-label'))||'')+' '+((n.getAttribute&&n.getAttribute('title'))||'');
al=al.toLowerCase();
if(al.indexOf('anuncio')>=0||al.indexOf('advertisement')>=0||al.indexOf('publicidad')>=0||al.indexOf('sponsored')>=0)return true;
}catch(e){}
if(n.tagName==='IFRAME'){
var src=(n.src||'').toLowerCase();
if(src.indexOf('doubleclick')>=0||src.indexOf('googlesyndication')>=0||src.indexOf('ads')>=0||src.indexOf('vast')>=0||src.indexOf('exoclick')>=0||src.indexOf('popads')>=0||src.indexOf('taboola')>=0||src.indexOf('outbrain')>=0||src.indexOf('adnxs')>=0||src.indexOf('criteo')>=0||src.indexOf('mgid')>=0||src.indexOf('adsterra')>=0||src.indexOf('propellerads')>=0||src.indexOf('trafficjunky')>=0||src.indexOf('juicyads')>=0||src.indexOf('smartadserver')>=0||src.indexOf('rubiconproject')>=0||src.indexOf('pubmatic')>=0||src.indexOf('prebid')>=0||src.indexOf('adform')>=0||src.indexOf('flashtalking')>=0||src.indexOf('imasdk')>=0||src.indexOf('googleads')>=0||src.indexOf('adservice')>=0||src.indexOf('aliexpress')>=0||src.indexOf('skimlinks')>=0||src.indexOf('viglink')>=0||src.indexOf('impactradius')>=0||src.indexOf('shareasale')>=0||src.indexOf('awin1')>=0||src.indexOf('linksynergy')>=0||src.indexOf('anrdoezrs')>=0||src.indexOf('dpbolvw')>=0||src.indexOf('kqzyfj')>=0||src.indexOf('jdoqocy')>=0||src.indexOf('tkqlhce')>=0||src.indexOf('apmebf')>=0||src.indexOf('affiliate')>=0||src.indexOf('go2cloud')>=0||src.indexOf('clickbank')>=0)return true;
}
n=n.parentElement;d++;
}
return false;
}
"""

    /**
     * JS que ESCONDE los anuncios de la página (v1.1.4). Reutiliza
     * esAnuncio de AD_CHECK_JS: oculta los elementos con pinta de
     * anuncio (contenedores, iframes de redes, botones falsos de
     * play dentro de contenedores de anuncios) y los overlays fijos
     * a pantalla completa que tapan todo y no son el reproductor.
     *
     * NUNCA oculta el reproductor real: un elemento que contiene un
     * <video> visible y grande (>200x150) no se toca, y los <video>
     * mismos tampoco. Se corre al terminar de cargar la página y se
     * repite un par de veces con retraso, porque estas páginas
     * inyectan sus anuncios después de la carga.
     */
    const val HIDE_ADS_JS = AD_CHECK_JS + """
(function(){
try{
function hasBigVideo(el){
var vs=el.querySelectorAll?el.querySelectorAll('video'):[];
for(var i=0;i<vs.length;i++){
try{var r=vs[i].getBoundingClientRect();if(r.width>200&&r.height>150)return true;}catch(e){}
}
return false;
}
function hasAdIframe(el){
try{
var frs=el.querySelectorAll?el.querySelectorAll('iframe'):[];
for(var i=0;i<frs.length;i++){try{if(esAnuncio(frs[i]))return true;}catch(e){}}
if(el.tagName==='IFRAME'){try{if(esAnuncio(el))return true;}catch(e){}}
}catch(e){}
return false;
}
var els=document.querySelectorAll('div,section,aside,iframe,button,a,span');
for(var i=0;i<els.length;i++){
var el=els[i];
if(el.tagName==='VIDEO')continue;
try{if(esAnuncio(el)&&!hasBigVideo(el))el.style.setProperty('display','none','important');}catch(e){}
}
var ovs=document.querySelectorAll('div');
for(var j=0;j<ovs.length;j++){
var o=ovs[j];
try{
var cs=window.getComputedStyle(o);
if(cs.position==='fixed'||cs.position==='absolute'){
var zi=parseInt(cs.zIndex||'0',10);
var r=o.getBoundingClientRect();
if(zi>=9999&&r.width>=window.innerWidth*0.9&&r.height>=window.innerHeight*0.9&&!hasBigVideo(o)&&!o.querySelector('video')){
if(esAnuncio(o)||hasAdIframe(o))o.style.setProperty('display','none','important');
}else if(zi>=1000&&r.width>=window.innerWidth*0.5&&r.height>=window.innerHeight*0.4&&!hasBigVideo(o)&&hasAdIframe(o)){
o.style.setProperty('display','none','important');
}
}
}catch(e){}
}
}catch(e){}
})()
"""

    /**
     * JS que NEUTRALIZA LA TRAMPA DEL BOTÓN PLAY (v1.1.5).
     *
     * En las páginas de pelis/series/porno, el primer toque en el
     * reproductor no reproduce: abre un anuncio (pop-up o salto a
     * otra página). La trampa vive en un overlay transparente que
     * tapa el video y en manejadores de clic del contenedor.
     *
     *  (a) window.open queda anulado: los pop-ups no abren nada.
     *  (b) Un listener en fase de captura: si el clic cae en un
     *      overlay que tapa el video (no es el video, no es un
     *      control real), se frena y se reproduce/pausa el video
     *      real. Así el primer toque reproduce en vez de abrir el
     *      anuncio. Los controles reales (botones, barras, menús
     *      pequeños) no se tocan.
     *  (c) A los overlays que tapan el video casi por completo se
     *      les borra onclick y, si son enlaces, se les anula el
     *      destino.
     *
     * Se corre al terminar de cargar la página y se repite a los
     * 1.5 s y 4 s, como HIDE_ADS_JS. No toca el escaneo de videos
     * (scanPageForVideos) ni el botón de transmitir: todo eso es
     * UI nativa de Android, no DOM.
     */
    const val TRAP_BLOCK_JS = """
(function(){
try{
try{window.open=function(){return null;};}catch(e){}
function clsT(el){var c='';try{c=(el.className&&el.className.baseVal!==undefined)?el.className.baseVal:(el.className||'');}catch(e){}return (''+c+' '+(el.id||'')).toLowerCase();}
function esControlReal(el){
if(!el)return false;
var t=(el.tagName||'').toUpperCase();
if(t==='BUTTON'||t==='INPUT'||t==='SELECT'||t==='TEXTAREA')return true;
var s=clsT(el);
if(s.indexOf('control')>=0||s.indexOf('ytp-')>=0||s.indexOf('vjs-control')>=0)return true;
return false;
}
function contenedorConVideo(el){
var n=el;
for(var d=0;n&&d<8;d++){
try{if(n.querySelector&&n.querySelector('video'))return n;}catch(e){}
n=n.parentElement;
}
return null;
}
function videoMasGrande(root){
var vs=root?root.querySelectorAll('video'):[];
var best=null,area=0;
for(var i=0;i<vs.length;i++){
try{var r=vs[i].getBoundingClientRect();var a=r.width*r.height;if(a>area){area=a;best=vs[i];}}catch(e){}
}
return best;
}
function solapaVideo(el,v,frac){
try{
var tr=el.getBoundingClientRect(),vr=v.getBoundingClientRect();
if(vr.width<=0||vr.height<=0)return false;
var ox=Math.max(0,Math.min(tr.right,vr.right)-Math.max(tr.left,vr.left));
var oy=Math.max(0,Math.min(tr.bottom,vr.bottom)-Math.max(tr.top,vr.top));
return (ox*oy)>=(vr.width*vr.height*frac);
}catch(e){return false;}
}
function alternarVideo(v){
try{
if(v.paused){var pr=v.play();if(pr&&pr.catch)pr.catch(function(){});}
else{v.pause();}
}catch(e){}
}
document.addEventListener('click',function(e){
try{
var t=e.target;
if(!t||t.tagName==='VIDEO')return;
if(esControlReal(t))return;
var p=contenedorConVideo(t);
if(!p)return;
var v=videoMasGrande(p);
if(!v)return;
var n=t,overlay=null;
while(n&&n!==p){
if(solapaVideo(n,v,0.5)){overlay=n;break;}
n=n.parentElement;
}
if(!overlay)return;
e.preventDefault();e.stopPropagation();e.stopImmediatePropagation();
alternarVideo(v);
}catch(err){}
},true);
try{
var all=document.querySelectorAll('div,a,span,button');
for(var k=0;k<all.length;k++){
var el=all[k];
if(el.tagName==='VIDEO'||esControlReal(el))continue;
try{
var vs=document.querySelectorAll('video');
for(var m=0;m<vs.length;m++){
if(solapaVideo(el,vs[m],0.8)){
el.onclick=null;
try{el.removeAttribute('onclick');}catch(e){}
if(el.tagName==='A'){try{el.setAttribute('href','javascript:void(0)');}catch(e){}}
break;
}
}
}catch(e){}
}
}catch(e){}
}catch(e){}
})()
"""
}
