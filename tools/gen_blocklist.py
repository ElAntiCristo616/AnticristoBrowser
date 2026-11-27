#!/usr/bin/env python3
"""Genera app/src/main/assets/ad-blocklist.txt para CuteNovna Browser.

Fuentes (todas publicas y gratuitas):
  1. DuckDuckGo Tracker Blocklist (tds.json).
     Repo:   https://github.com/duckduckgo/tracker-blocklists
     Datos:  https://staticcdn.duckduckgo.com/trackerblocking/v3/tds.json
     Licencia de los datos: CC BY-NC-SA 4.0, Copyright 2022 Duck Duck Go, Inc.
  2. EasyList (lista generica de bloqueo de anuncios).
     Espejo canonico: https://easylist-downloads.adblockplus.org/easylist.txt
     (el de easylist.to corta la descarga desde aqui).
     Licencia: dual CC BY-SA 3.0 / GPLv3
     (https://easylist.to/pages/licence.html).
  3. AdGuard Base filter (opcional, --adguard).
     https://filters.adtidy.org/extension/chromium/filters/2.txt
     Licencia: GPLv3
     (https://github.com/AdguardTeam/AdguardFilters/blob/master/LICENSE).
     NOTA: el CDN de AdGuard corta la conexion a menudo desde aqui;
     por eso es opcional y si falla se avisa y se sigue sin el.
     EasyList ya trae miles de dominios; AdGuard es un extra.

Como se extrae (a proposito, conservador):
  - De tds: solo trackers con default == "block", solo el dominio
    inicial de cada regla (igual que v1.1.4).
  - De EasyList/AdGuard (sintaxis Adblock Plus): solo reglas de
    dominio puro "||dominio^" (con o sin opciones $...). NUNCA se
    toman reglas con ruta (||dominio/ruta) porque bloquearian el
    dominio entero por un solo anuncio. Las excepciones "@@||dominio^"
    se restan de la lista final.
  - NUNCA se incluye un dominio de contenido, video, CDN, login o
    red local (lista NEVER_BLOCK). Sin esto, las listas nos colarian
    dominios que la gente usa (o CDNs de medios).
  - Se anade SUPPLEMENT con redes de anuncios de streaming/porno y
    servidores de anuncios de afiliados que las listas no cubren.
  - Se reduce a dominios base (si "doubleclick.net" esta, sobran sus
    subdominios: el matcher de la app hace matching por sufijo).

Uso:
  python3 tools/gen_blocklist.py [--tds RUTA_O_URL] [--easylist RUTA_O_URL]
                                 [--adguard RUTA_O_URL] [--out RUTA]
  --no-tds / --no-easylist para saltar una fuente.
  Sin --adguard no se intenta AdGuard (CDN poco fiable).

El asset generado se commitea en el repo: el APK lo lleva dentro y
el build de GitHub Actions NO descarga nada en tiempo de build.
"""
import argparse
import json
import re
import sys
import urllib.request
from datetime import date

TDS_URL = "https://staticcdn.duckduckgo.com/trackerblocking/v3/tds.json"
# Espejo canonico de EasyList (Adblock Plus). El de easylist.to corta
# la descarga a mitad desde aqui; este llega completo.
EASYLIST_URL = "https://easylist-downloads.adblockplus.org/easylist.txt"
ADGUARD_URL = "https://filters.adtidy.org/extension/chromium/filters/2.txt"

# Dominios que NUNCA se bloquean: contenido, video, CDNs de medios,
# login, busqueda y red local. Si un candidato es uno de estos (o un
# subdominio suyo, o un padre suyo) queda fuera.
NEVER_BLOCK = {
    # Video / medios (el video principal no se toca jamas)
    "googlevideo.com", "ytimg.com", "ggpht.com", "gvt1.com",
    "youtube.com", "youtube-nocookie.com", "youtu.be",
    "tiktokcdn.com", "cdninstagram.com", "fbcdn.net", "twimg.com",
    "tiktok.com", "instagram.com", "facebook.com",
    "bkcdn.net", "bunnycdn.com", "jwplayer.com", "vimeo.com",
    "dailymotion.com", "twitch.tv",
    # Busqueda / login / cuentas
    "duckduckgo.com", "google.com", "accounts.google.com",
    "ssl.gstatic.com", "fonts.gstatic.com", "fonts.googleapis.com",
    "gstatic.com", "gmail.com", "microsoft.com", "apple.com",
    "connect.facebook.net", "bing.com", "yahoo.com",
    # Contenido y servicios (las listas los marcan por rutas de
    # tracking, pero el dominio entero es un sitio que la gente usa)
    "yandex.ru", "reddit.com", "shop.app", "hubspot.com",
    "mailchimp.com", "addtoany.com", "amazon.com",
    "mercadolibre.com", "wikipedia.org", "github.com",
    "linkedin.com", "pinterest.com", "netflix.com", "spotify.com",
    "whatsapp.com", "telegram.org", "mozilla.org", "opera.com",
    "w3.org",
    # Infraestructura generica y red local
    "run.app", "localhost", "cloudfront.net", "akamaihd.net",
    "akamaized.net", "cloudflare.com", "fastly.net",
    "amazonaws.com",
}

# Redes de anuncios (sobre todo de streaming/porno) que las listas
# no cubren o marcan "ignore", pero que son 100% publicidad. Solo
# dominios que son redes de anuncios puras: bloquearlos no rompe
# ningun contenido. Incluye los servidores de anuncios de afiliados
# (AliExpress): ad.aliexpress.com y s.click.aliexpress.com son solo
# publicidad, NO la tienda (aliexpress.com) ni su CDN de imagenes
# (alicdn.com), que NO se tocan.
SUPPLEMENT = {
    # Porno / streaming (las que mas sufren Remy y su amigo)
    "exoclick.com", "trafficjunky.com", "juicyads.com", "adcash.com",
    "popads.net", "popcash.net", "adsterra.com", "propellerads.com",
    "hilltopads.com", "clickadu.com", "admaven.com", "eroadvertising.com",
    "plugrush.com", "adspyglass.com", "clickaine.com", "richads.com",
    "adnium.com", "trafficstars.com",
    # Servidores de anuncios de afiliados (widgets de tienda en
    # paginas de pelis/series/porno)
    "ad.aliexpress.com", "s.click.aliexpress.com",
    # Recomendacion / intercambio de anuncios general
    "taboola.com", "mgid.com", "revcontent.com", "pubmatic.com",
    "openx.net", "triplelift.com", "indexexchange.com", "spotx.tv",
    "freewheel.tv", "springserve.com", "aniview.com", "vidazoo.com",
    # SDKs de anuncios moviles
    "vungle.com", "applovin.com", "ironsource.com", "chartboost.com",
    "tapjoy.com", "inmobi.com", "smaato.com", "mopub.com",
    # Servidores de anuncios
    "smartadserver.com", "adform.net", "flashtalking.com", "criteo.com",
    "moatads.com", "zedo.com", "adzerk.net", "yieldmo.com",
    "sharethrough.com", "lijit.com", "sovrn.com", "adroll.com",
    "adblade.com",
}

_HOST_RE = re.compile(r"([a-z0-9\-]+(\.[a-z0-9\-]+)+)")
_ABP_DOMAIN_RE = re.compile(r"^\|\|([a-z0-9\-\.]+)(\^|\$|$)")


def leading_host(rule: str):
    """Dominio inicial de una regla regex de tds (o None)."""
    s = rule.replace("\\\\", "\x00").replace("\\.", ".").replace("\\/", "/")
    s = s.replace("\x00", "\\").lstrip("^")
    m = _HOST_RE.search(s)
    return m.group(1).lower() if m else None


def is_never_block(host: str) -> bool:
    for n in NEVER_BLOCK:
        if host == n or host.endswith("." + n) or n.endswith("." + host):
            return True
    return False


def reduce_to_base(domains):
    """Quita subdominios ya cubiertos por un dominio padre en la lista.
    Para cada dominio se prueban solo sus sufijos propios (2-5 por
    dominio): O(n) en la practica, no O(n^2)."""
    s = set(domains)
    out = []
    for d in sorted(s):
        covered = False
        rest = d
        while True:
            dot = rest.find(".")
            if dot < 0:
                break
            rest = rest[dot + 1:]
            if rest in s:
                covered = True
                break
        if not covered:
            out.append(d)
    return out


def parse_abp_list(text: str):
    """Extrae dominios de una lista Adblock Plus (EasyList/AdGuard).

    Solo reglas de dominio puro "||dominio^" (con o sin opciones
    $...). Devuelve (bloqueados, excepciones): las excepciones son
    las reglas "@@||dominio^" y se restan al final.
    """
    blocked = set()
    exceptions = set()
    for raw in text.splitlines():
        line = raw.strip()
        if not line or line.startswith("!") or line.startswith("["):
            continue
        is_exc = line.startswith("@@")
        body = line[2:] if is_exc else line
        if not body.startswith("||"):
            continue
        m = _ABP_DOMAIN_RE.match(body)
        if not m:
            continue
        d = m.group(1).lower()
        if "*" in d or "." not in d:
            continue
        if is_exc:
            exceptions.add(d)
        else:
            blocked.add(d)
    return blocked, exceptions


def fetch_text(source: str, timeout: int = 120) -> str:
    """Lee texto desde una URL o una ruta local. Las descargas desde
    estos CDNs se cortan a veces a mitad (IncompleteRead): se
    reintenta 3 veces antes de fallar."""
    if source.startswith("http"):
        req = urllib.request.Request(
            source, headers={"User-Agent": "Mozilla/5.0"})
        last_err = None
        for attempt in range(3):
            try:
                with urllib.request.urlopen(req, timeout=timeout) as r:
                    return r.read().decode("utf-8", errors="ignore")
            except Exception as e:
                last_err = e
                print(f"  intento {attempt + 1} fallo ({e}); reintentando...")
                import time
                time.sleep(2)
        raise last_err
    with open(source, encoding="utf-8", errors="ignore") as f:
        return f.read()


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--tds", default=TDS_URL,
                    help="Ruta local a tds.json o URL")
    ap.add_argument("--no-tds", action="store_true")
    ap.add_argument("--easylist", default=EASYLIST_URL,
                    help="Ruta local a easylist.txt o URL")
    ap.add_argument("--no-easylist", action="store_true")
    ap.add_argument("--adguard", default=None,
                    help="Ruta local al filtro base de AdGuard o URL "
                         "(opcional: su CDN corta la conexion a menudo)")
    ap.add_argument("--out",
                    default="app/src/main/assets/ad-blocklist.txt")
    args = ap.parse_args()

    merged = set()
    stats = []

    if not args.no_tds:
        print("Descargando tds:", args.tds)
        data = json.loads(fetch_text(args.tds))
        trackers = data.get("trackers", {})
        tds_hash = (data.get("_builtWith") or {}).get(
            "tracker-radar", "desconocido")
        cands = set()
        n_block = 0
        for key, v in trackers.items():
            if v.get("default") != "block":
                continue
            n_block += 1
            for r in v.get("rules", []):
                h = leading_host(r.get("rule", ""))
                if h:
                    cands.add(h)
        kept = {h for h in cands if not is_never_block(h)}
        print(f"tds: {n_block} trackers block, {len(cands)} candidatos, "
              f"{len(kept)} tras NEVER_BLOCK")
        merged |= kept
        stats.append(f"tds: {len(kept)}")
    else:
        tds_hash = "no usado"

    if not args.no_easylist:
        print("Descargando EasyList:", args.easylist)
        text = fetch_text(args.easylist)
        blocked, exc = parse_abp_list(text)
        kept = {h for h in blocked - exc if not is_never_block(h)}
        print(f"EasyList: {len(blocked)} dominios, {len(exc)} excepciones, "
              f"{len(kept)} tras NEVER_BLOCK")
        merged |= kept
        stats.append(f"EasyList: {len(kept)}")

    if args.adguard:
        try:
            print("Descargando AdGuard:", args.adguard)
            text = fetch_text(args.adguard)
            blocked, exc = parse_abp_list(text)
            kept = {h for h in blocked - exc if not is_never_block(h)}
            print(f"AdGuard: {len(blocked)} dominios, {len(exc)} excepciones, "
                  f"{len(kept)} tras NEVER_BLOCK")
            merged |= kept
            stats.append(f"AdGuard: {len(kept)}")
        except Exception as e:
            print(f"AVISO: AdGuard fallo ({e}); se sigue sin el")

    merged |= SUPPLEMENT
    final = reduce_to_base(merged)
    print(f"lista final: {len(final)} dominios base "
          f"({len(SUPPLEMENT)} suplemento)")

    header = (
        "# CuteNovna Browser — lista de bloqueo de anuncios/rastreadores.\n"
        "# Fuentes (todas publicas y gratuitas):\n"
        "#   DuckDuckGo Tracker Blocklist (tds.json)\n"
        "#     https://github.com/duckduckgo/tracker-blocklists\n"
        "#     Licencia: CC BY-NC-SA 4.0, Copyright 2022 Duck Duck Go, Inc.\n"
        "#   EasyList\n"
        "#     https://easylist.to/easylist/easylist.txt\n"
        "#     Licencia: dual CC BY-SA 3.0 / GPLv3\n"
        "#     (https://easylist.to/pages/licence.html)\n"
        "#   AdGuard Base filter (solo si se paso --adguard)\n"
        "#     https://github.com/AdguardTeam/AdguardFilters\n"
        "#     Licencia: GPLv3\n"
        f"# Generado: {date.today().isoformat()} | tds tracker-radar: {tds_hash}\n"
        f"# Aportes: {', '.join(stats)} | suplemento: {len(SUPPLEMENT)}\n"
        f"# {len(final)} dominios base (matching por sufijo en la app).\n"
        "# NUNCA incluye dominios de video (googlevideo.com, youtube.com,\n"
        "# ytimg.com, bkcdn.net...), CDNs de medios, login ni red local.\n"
        "# Regenerar con: python3 tools/gen_blocklist.py\n"
    )
    with open(args.out, "w", encoding="utf-8") as f:
        f.write(header)
        for d in final:
            f.write(d + "\n")
    print("escrito:", args.out)
    return 0


if __name__ == "__main__":
    sys.exit(main())
