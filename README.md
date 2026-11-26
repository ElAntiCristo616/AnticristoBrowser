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
- **Controles del reproductor al transmitir (v1.0.4)**: la barrita de
  transmisión trae Pausa / Seguir / Parar, saltos de −10 s y +10 s y
  barra de progreso con el tiempo. Los saltos usan el protocolo del
  canal receptor (positionSet, exacto) con respaldo por teclas ECP
  Fwd/Rew; la posición llega por los eventos del canal y se sondea
  cada 5 s. Solo se puede probar en la tele real.
- **Detección de video agrupada (v1.0.4)**: cada video es UNA tarjeta
  en la hoja (sus variantes hls.m3u8, hls-360p, hls-480p, hls-720p y
  mp4 se fusionan), con miniatura del póster, duración y sus calidades
  dentro. El video principal (el reproductor más grande/visible) va
  primero con insignia dorada «Video principal». La tarjeta elegida
  queda marcada con borde dorado y ✓, y la calidad elegida con radio
  lleno y fondo resaltado. Filtro de anuncios reforzado (más
  marcadores de URL, contenedores de anuncios, videos ocultos y
  videos de menos de 10 s fuera).
- **Iconos de marca rehechos (v1.0.4)**: los 5 accesos rápidos con los
  logos oficiales vectoriales (YouTube: play blanco sobre rojo;
  TikTok: nota con cian/magenta sobre negro; Instagram: cámara sobre
  el degradado oficial; X: X blanca sobre negro; Facebook: f blanca
  sobre azul).
- **TikTok arreglado (v1.0.4)**: user-agent de Chrome móvil real (sin
  «wv»), cookies de terceros aceptadas, entrada por www.tiktok.com y
  manejo de enlaces tiktok:// o intent://.
- **Temas (v1.0.4)**: 13 temas (Navidad, Sakura, Invierno, Océano,
  Galaxia, Bosque, Fuego, Atardecer, Neón, Otoño, Dulce, Lluvia y
  Claro) con paleta completa y partículas propias por tema (nieve,
  pétalos rosa, hojas, burbujas, brasas, lluvia, estrellas,
  corazones, destellos, polvo dorado o ninguna), guardados y
  aplicables al portal, a la puerta y al navegador. Selector en el
  botón «Tema» del portal y en el menú ⋮ del navegador.
- Tema navideño (verde pino, rojo, guirnalda y nieve), todo en español.

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
