# Anticristo Browser

Navegador simple para Android con buscador DuckDuckGo y transmisión de
videos a la tele por el canal receptor «Web Video Caster - Receiver»
(gratis en la tienda Roku).

- **Navegador WebView** con barra de dirección que también es buscador:
  si lo escrito no es URL, busca en DuckDuckGo.
- **Sin cuentas, sin sincronización, sin configuraciones**: solo el
  buscador crudo y entrar a cualquier página. Sin ajustes salvo
  «Acerca de».
- **Página principal con las 1,731 páginas de yt-dlp** (lista oficial
  completa, tal cual, incluida la sección de adultos), con buscador y
  favicon por sitio. Al tocar un sitio, abre su página en el navegador.
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
