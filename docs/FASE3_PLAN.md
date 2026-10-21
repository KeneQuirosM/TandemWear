# Fase 3 — Música del iPhone (AMS): plan

**Estado (21-10-2026):** F3.1–F3.6 hechos. AMS ya está conectado a la sesión ANCS y hay reproductor, tarjeta en Inicio, tarjeta en el Tile y el ajuste «Música». Todo lo que depende del reloj está **por verificar** (ver `docs/PRUEBAS_FASE3.md`).

## Fuente

Apple, *Apple Media Service Reference* (especificación + apéndice A, tablas A-1 a A-8), en developer.apple.com. **Volví a verificarla el 21-10-2026 al empezar la Fase 3:** los UUID, los identificadores, el formato de las notificaciones y el orden de suscripción coinciden con el código, y están fijados en `AmsProtocolTest`.

Apple no especifica:
- si una segunda escritura de Entity Update para la misma entidad reemplaza la suscripción anterior o se suma. Por eso se pide todo en **una** escritura por entidad;
- si avisa cuando cambia la app que reproduce. Por verificar.

| Elemento | UUID | Propiedades |
|---|---|---|
| Servicio AMS | `89D3502B-0F36-433A-8EF4-C502AD55F8DC` | — |
| Remote Command | `9B3C81D8-57B1-4A8A-B8DF-0E56F7CA51C2` | escribir, notificar (comandos admitidos) |
| Entity Update | `2F7CABCE-808D-411F-9A0C-BB92BA96C102` | escribir con respuesta, notificar |
| Entity Attribute | `C6B2F38C-23AB-46D8-A6AB-A3A870BBD5D7` | leer, escribir |

**Identificadores:**
- Comandos 0–13: Play, Pause, TogglePlayPause, Next, Previous, VolumeUp/Down, AdvanceRepeat/Shuffle, SkipForward/Backward, Like, Dislike, Bookmark.
- Entidades: Player 0, Queue 1, Track 2.
- Atributos de Player: Name 0, PlaybackInfo 1 (`estado,velocidad,transcurrido`), Volume 2.
- Atributos de Queue: Index, Count, Shuffle y Repeat.
- Atributos de Track: Artist, Album, Title, Duration.
- Errores: 0xA0 (estado inválido), 0xA1 (comando inválido), 0xA2 (atributo ausente).

## Qué hay hecho

- `ams/AmsProtocol.kt` contiene:
  - los UUID;
  - los constructores de bytes: Entity Update, Remote Command, Entity Attribute y las 3 suscripciones por defecto;
  - el parser de Entity Update (`[entidad][atributo][flags][valor UTF-8]`, con la bandera de truncado);
  - el parser de comandos admitidos;
  - `PlaybackInfo`, con la posición extrapolada según la especificación: `transcurrido + (ahora − recibido) × velocidad`.
- `ams/MediaState.kt` es un reductor inmutable con el estado de la música. Lleva la lista de valores truncados para pedirlos completos por Entity Attribute.
- 8 tests en `AmsProtocolTest`.

## Cómo quedó (F3.1–F3.6)

| Paso | Qué |
|---|---|
| F3.1 | `ble/AmsClient`: suscripción y comandos. Cada paso espera a que ANCS esté libre (la cola sin nada en curso ni pendiente, ninguna petición de atributos ANCS en curso y 500 ms sin actividad ANCS). Empieza 5 s después de `SESSION ACTIVE`. Tiempo límite de 1,5 s por operación. Si falla, AMS se apaga para esa sesión y ANCS sigue igual. Cada paso queda en el log (`ams: …`) con su duración y con lo que esperó a ANCS |
| F3.2 | `ams/MusicSession` (reductor puro) y `AncsService.sharedMusic` |
| F3.3 | Reproductor (`ui/music/`) y tarjeta de música en Inicio |
| F3.4 | Tarjeta de música en el Tile, con play/pausa sin abrir la app |
| F3.5 | Ajuste «Música», que se aplica en vivo |
| F3.6 | Esta documentación y `docs/PRUEBAS_FASE3.md` |

**Orden de la suscripción (`AmsPolicy.subscriptionPlan`):**
1. activar las notificaciones de Entity Update;
2. activar las notificaciones de Remote Command;
3. escribir en Entity Update la entidad Player: nombre, PlaybackInfo y volumen;
4. Queue: índice, total, aleatorio y repetición;
5. Track: artista, álbum, título y duración.

**Cuándo suscribirse:** la constante `AmsPolicy.START_MODE` lo decide.
- `ON_CONNECT` (actual): al conectar.
- `ON_DEMAND`: solo cuando el reproductor está abierto. Para pasar a este modo basta con cambiar la constante; el reproductor ya avisa cuando está en pantalla (`ACTION_MUSIC_DEMAND`). Si las 5 escrituras de cada reconexión hacen vibrar el reloj (gotcha 10), se cambia a este modo.
- Pendiente para `ON_DEMAND`: el Tile todavía no pide la suscripción, así que en ese modo solo mostraría música después de abrir el reproductor.

**Valores truncados:** si título, artista o álbum llegan truncados, se piden completos por Entity Attribute (escribir y luego leer), también esperando a que ANCS esté libre.

**Progreso:** se calcula con la fórmula de Apple, `transcurrido + (ahora − recibido) × velocidad`, usando `elapsedRealtime` como reloj. La pantalla lo redibuja cada segundo solo mientras suena y está encendida.

**Volumen:** AMS solo permite subir y bajar paso a paso. El nivel mostrado es el que informa el iPhone (de 0 a 1).

**MediaSession:** evaluada como viable, pero queda para después, como experimento aparte. Ver la evaluación del 21-10 en el chat y el pendiente #5 en `PENDIENTE_CAPA_BLE.md`.

## Riesgos y cosas por verificar en el reloj

- Si iOS 26 deja usar AMS en el mismo enlace sin pedir otro permiso. El log del 21-10 mostró `AMS=true`.
- Si suscribirse hace vibrar el reloj en cada reconexión. En ese caso, pasar a `ON_DEMAND`.
- Si el iPhone avisa cuando cambia la app que reproduce (de Spotify a Música, por ejemplo).
- Volumen: el tamaño de cada paso lo decide iOS.
