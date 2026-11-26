# CuteNovna Browser

Navegador simple para Android con buscador DuckDuckGo y transmisión de
videos a la tele por el canal receptor «Web Video Caster - Receiver»
(gratis en la tienda Roku).

- **Navegador WebView** con barra de dirección que también es buscador:
  si lo escrito no es URL, busca en DuckDuckGo.
- **Sin cuentas, sin sincronización, sin configuraciones**: solo el
  buscador crudo y entrar a cualquier página. Sin ajustes salvo
  «Acerca de».
- **Dos pantallas**: la puerta pide solo la IP de la tele para entrar
  (la comprueba con GET a /query/device-info —timeout 10 s, hasta 3
  intentos, cualquier código HTTP vale como «tele alcanzable»— y la
  guarda; si la tele no responde, se entra IGUAL al portal con un
  aviso visible y se reintenta al transmitir, v1.0.2: la puerta ya no
  bloquea la entrada por un chequeo que falle; al abrir la app vuelve
  a entrar sola con la IP guardada, con el mismo aviso si no responde),
  y el portal trae el buscador DuckDuckGo y los accesos rápidos
  (YouTube, TikTok, Instagram, X, Facebook) con iconos incluidos en
  la app. «Cambiar» en el portal vuelve a la puerta para meter otra IP.
- **Detección de video** en la página (por red y por DOM) y campo para
  pegar un enlace (YouTube, TikTok, X, Instagram, Facebook…) con yt-dlp.
- **Selector de calidad**: antes de transmitir muestra las calidades
  disponibles (144p–1080p según lo que exista) y deja elegir. Por
  defecto va la **más alta disponible, nunca la más baja**. Con HLS se
  lee el manifiesto maestro y se elige la variante exacta.
- **Cast a Roku por el canal receptor** (id 259656): se lanza por ECP y
  se le manda el video con sus headers (User-Agent, Referer, Origin)
  para que no falle con error 0. Al transmitir, el video del teléfono
  se pausa para que la tele no se pause sola. Muestra el estado real
  que reporta el canal y errores específicos en español.
  El saludo al canal (deviceConnect, solo `welcomeMessage`, como la
  implementación de referencia ClearCast contra el canal v1.8.0) se
  reintenta hasta que el canal responde con su `deviceInfo`, para que
  no se quede en su pantalla de aviso de «aplicación falsa». Si el
  aviso sigue saliendo, la solución definitiva sería un canal receptor
  propio (fuera de esta versión).
- **Conectar a la tele**: descubrimiento por SSDP o IP manual
  (la IP se ve en la tele en Ajustes → Red → Acerca de); la IP se
  guarda y se usa primero la próxima vez.
## Novedades v1.1.0 — transmisión universal + lobby nuevo

- **Transmisión universal**: Roku (ECP/DIAL/WVC como siempre) +
  **Chromecast / Google Cast** (SDK oficial, receptor por defecto:
  URLs directas MP4/HLS/DASH, play/pausa/seek/velocidad) +
  **DLNA/UPnP** (descubrimiento SSDP + SOAP AVTransport: play/pausa/
  stop/seek; solo MP4 directo). La hoja «¿A qué tele lo mando?»
  lista las teles del Wi-Fi con su protocolo; la vinculada se
  recuerda por protocolo. Todo exige el mismo Wi-Fi.
- **Lobby nuevo** (look de la referencia Behance "Movie Mobile App",
  solo el look: no es una réplica, sin onboarding VR, sin «Sign up»,
  sin pósters ni datos falsos de pelis): saludo grande, campo para
  pegar el link + botón «Transmitir», **Favoritos** (sitios guardados
  con su icono oficial; tocar transmite, mantener quita) e
  **Historial** (links con fecha/hora; tocar reabre, ✕ borra,
  «Limpiar» borra todo). Cero accesos directos fijos. Navegación
  inferior: Inicio / Navegador / + central / Favoritos / Historial.
- **Sin temas ni personalización**: el sistema de 13 temas, el
  selector y las partículas se quitaron por completo. Un solo look
  fijo: el de la referencia.
- **Selector de video: anuncios bloqueados**: AdFilter filtra URLs
  de publicidad (redes de ads, VAST, preroll/midroll, patrocinados),
  contenedores de anuncios y títulos de anuncio. Una tarjeta por
  video, principal primero con ★.
- **Reproductor nuevo**: barra de progreso arrastrable con el dedo
  (seek libre), tiempo actual/total, play/pausa grande, −10 s/+10 s,
  velocidad 0.5×–2× (solo Chromecast; en Roku/DLNA el botón va
  apagado) y pantalla completa (diálogo grande con su propia barra).
  Funciona en los 3 protocolos; la posición se sondea cada 5 s.
- YouTube en Roku sigue por DIAL (app de YouTube en la tele). En
  Chromecast YouTube se manda como URL directa de yt-dlp al receptor
  por defecto (a propósito: por DIAL la app de YouTube no la puede
  controlar nuestra app y el reproductor quedaría inútil).
- Tema navideño (verde pino, rojo, guirnalda y nieve), todo en español.

## Lo que ya tenía (v1.0.4, sigue igual)

- **Detección de video agrupada**: cada video es UNA tarjeta en la
  hoja (sus variantes hls.m3u8, hls-360p, hls-480p, hls-720p y mp4
  se fusionan), con miniatura del póster, duración y sus calidades
  dentro. El video principal va primero con insignia dorada «Video
  principal». La tarjeta elegida queda marcada con borde dorado y ✓,
  y la calidad elegida con radio lleno y fondo resaltado.
- **Iconos de marca**: logos oficiales vectoriales (YouTube, TikTok,
  Instagram, X, Facebook) en favoritos y en la hoja de videos.
- **TikTok arreglado**: user-agent de Chrome móvil real (sin «wv»),
  cookies de terceros aceptadas, entrada por www.tiktok.com y manejo
  de enlaces tiktok:// o intent://.
- **Controles del reproductor (v1.0.4)**: la barrita traía Pausa /
  Seguir / Parar, saltos de −10 s y +10 s y barra de progreso. Los
  saltos usan el protocolo del canal receptor (positionSet, exacto)
  con respaldo por teclas ECP Fwd/Rew. En v1.1.0 el reproductor se
  rehízo (ver arriba).

## Para transmitir necesitas

Instalar gratis en la tele el canal **«Web Video Caster - Receiver»**
en la tienda de Roku (Streaming Channels).

## Código abierto (GPL-3.0)

Esta app es código abierto bajo la licencia GPL-3.0 (ver `LICENSE`)
porque usa yt-dlp a través de la librería youtubedl-android, que es
GPL-3.0. yt-dlp en sí es de dominio público (Unlicense).

## Compilar

El build de release se hace en GitHub Actions (`.github/workflows/android.yml`)
con el keystore guardado en secretos del repo. Requiere JDK 17.
