#!/usr/bin/env python3
"""Genera app/src/main/assets/duckduckgo-blocklist.txt para CuteNovna Browser.

Fuente: DuckDuckGo Tracker Blocklist (tds.json).
  Repo:   https://github.com/duckduckgo/tracker-blocklists
  Datos:  https://staticcdn.duckduckgo.com/trackerblocking/v3/tds.json
  Licencia de los datos: CC BY-NC-SA 4.0, Copyright 2022 Duck Duck Go, Inc.
  (hay que dejar la atribución en la cabecera del asset generado).

Cómo se extrae (a propósito, conservador):
  - Solo se usan los trackers con default == "block" de tds.json.
  - De cada regla se toma el dominio inicial (la regla es una regex;
    puede llevar una ruta, p. ej. "doubleclick.net/gampad/ads").
  - NUNCA se incluye un dominio de contenido, video, CDN, login o red
    local (lista NEVER_BLOCK): si lo hiciéramos, tds nos colaría
    youtube.com, tiktok.com, googlevideo.com, fbcdn.net, etc., porque
    en tds "domains" están TODOS los dominios observados, no solo los
    de anuncios. Por eso no se usa la clave "domains" de tds.
  - Se añade una lista SUPPLEMENT con redes de anuncios de streaming/
    porno que tds no cubre o marca "ignore" (ExoClick, PopAds, ...).
  - Se reduce a dominios base (si "doubleclick.net" está, sobran sus
    subdominios: el matcher del app hace matching por sufijo).

Uso:
  python3 tools/gen_blocklist.py [--tds RUTA_O_URL] [--out RUTA]
  Si no se pasa --tds, descarga el tds.json oficial del CDN de DuckDuckGo.

El asset generado se commitea en el repo: el APK lo lleva dentro y el
build de GitHub Actions NO descarga nada en tiempo de build.
"""
import argparse
import json
import re
import sys
import urllib.request
from datetime import date

TDS_URL = "https://staticcdn.duckduckgo.com/trackerblocking/v3/tds.json"

# Dominios que NUNCA se bloquean: contenido, video, CDNs de medios,
# login, búsqueda y red local. Si un candidato es uno de estos (o un
# subdominio suyo, o un padre suyo) queda fuera.
NEVER_BLOCK = {
    # Video / medios (el video principal no se toca jamás)
    "googlevideo.com", "ytimg.com", "ggpht.com", "gvt1.com",
    "youtube.com", "youtube-nocookie.com", "youtu.be",
    "tiktokcdn.com", "cdninstagram.com", "fbcdn.net", "twimg.com",
    "tiktok.com", "instagram.com", "facebook.com",
    # Búsqueda / login / cuentas
    "duckduckgo.com", "google.com", "accounts.google.com",
    "ssl.gstatic.com", "fonts.gstatic.com", "fonts.googleapis.com",
    "gstatic.com", "gmail.com", "microsoft.com", "apple.com",
    "connect.facebook.net",
    # Contenido y servicios (tds los marca block por rutas de tracking,
    # pero el dominio entero es un sitio que la gente usa)
    "yandex.ru", "reddit.com", "shop.app", "hubspot.com",
    "mailchimp.com", "addtoany.com",
    # Infraestructura genérica y red local
    "run.app", "localhost",
}

# Redes de anuncios (sobre todo de streaming/porno) que tds no cubre o
# marca "ignore", pero que son 100% publicidad. Solo dominios que son
# redes de anuncios puras: bloquearlos no rompe ningún contenido.
SUPPLEMENT = {
    # Porno / streaming (las que más sufren Remy y su amigo)
    "exoclick.com", "trafficjunky.com", "juicyads.com", "adcash.com",
    "popads.net", "popcash.net", "adsterra.com", "propellerads.com",
    "hilltopads.com", "clickadu.com", "admaven.com", "eroadvertising.com",
    "plugrush.com", "adspyglass.com", "clickaine.com", "richads.com",
    "adnium.com", "trafficstars.com",
    # Recomendación / intercambio de anuncios general
    "taboola.com", "mgid.com", "revcontent.com", "pubmatic.com",
    "openx.net", "triplelift.com", "indexexchange.com", "spotx.tv",
    "freewheel.tv", "springserve.com", "aniview.com", "vidazoo.com",
    # SDKs de anuncios móviles
    "vungle.com", "applovin.com", "ironsource.com", "chartboost.com",
    "tapjoy.com", "inmobi.com", "smaato.com", "mopub.com",
    # Servidores de anuncios
    "smartadserver.com", "adform.net", "flashtalking.com", "criteo.com",
    "moatads.com", "zedo.com", "adzerk.net", "yieldmo.com",
    "sharethrough.com", "lijit.com", "sovrn.com", "adroll.com",
    "adblade.com",
}

_HOST_RE = re.compile(r"([a-z0-9\-]+(\.[a-z0-9\-]+)+)")


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
    """Quita subdominios ya cubiertos por un dominio padre en la lista
    (comparación contra TODA la lista, no solo contra lo ya visto)."""
    s = set(domains)
    out = []
    for d in sorted(s):
        if any(d != k and d.endswith("." + k) for k in s):
            continue
        out.append(d)
    return out


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--tds", default=None,
                    help="Ruta local a tds.json o URL (por defecto, el CDN oficial)")
    ap.add_argument("--out", default="app/src/main/assets/duckduckgo-blocklist.txt")
    args = ap.parse_args()

    if args.tds:
        if args.tds.startswith("http"):
            print("Descargando", args.tds)
            with urllib.request.urlopen(args.tds, timeout=120) as r:
                data = json.loads(r.read().decode("utf-8"))
        else:
            with open(args.tds, encoding="utf-8") as f:
                data = json.load(f)
    else:
        print("Descargando", TDS_URL)
        with urllib.request.urlopen(TDS_URL, timeout=120) as r:
            data = json.loads(r.read().decode("utf-8"))

    trackers = data.get("trackers", {})
    tds_hash = (data.get("_builtWith") or {}).get("tracker-radar", "desconocido")

    cands = set()
    n_block = 0
    for key, v in trackers.items():
        if v.get("default") != "block":
            continue
        n_block += 1
        # SOLO los dominios que aparecen en las reglas: la clave del
        # tracker NO se añade, porque muchas veces es un sitio de
        # contenido (bing.com, quora.com, snapchat.com, chaturbate.com
        # son trackers "block" en tds y bloquearlos rompería el sitio).
        for r in v.get("rules", []):
            h = leading_host(r.get("rule", ""))
            if h:
                cands.add(h)
    print(f"trackers block en tds: {n_block}, dominios candidatos: {len(cands)}")

    kept = {h for h in cands if not is_never_block(h)}
    dropped = sorted(cands - kept)
    print(f"descartados por NEVER_BLOCK ({len(dropped)}): {dropped}")

    merged = kept | SUPPLEMENT
    final = reduce_to_base(merged)
    print(f"lista final: {len(final)} dominios base "
          f"({len(kept)} de tds + {len(SUPPLEMENT)} suplemento)")

    header = (
        "# CuteNovna Browser — lista de bloqueo de anuncios/rastreadores.\n"
        "# Fuente: DuckDuckGo Tracker Blocklist (tds.json)\n"
        "#   https://github.com/duckduckgo/tracker-blocklists\n"
        "#   https://staticcdn.duckduckgo.com/trackerblocking/v3/tds.json\n"
        "# Licencia de los datos: CC BY-NC-SA 4.0, Copyright 2022 Duck Duck Go, Inc.\n"
        f"# Generado: {date.today().isoformat()} | tds tracker-radar: {tds_hash}\n"
        f"# {len(final)} dominios base (matching por sufijo en la app).\n"
        "# NUNCA incluye dominios de video (googlevideo.com, youtube.com,\n"
        "# ytimg.com...), CDNs de medios, login ni red local.\n"
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
