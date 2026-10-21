# Fase 3 — Música del iPhone (AMS): plan

**Estado:** investigación y parser. El código de `ams/` es puro, tiene tests y **no está conectado** a la capa BLE.

## Fuente

Apple, *Apple Media Service Reference* (especificación + apéndice A, tablas A-1 a A-8), consultada el 21-10-2026. Los UUID están verificados contra el apéndice y fijados en `AmsProtocolTest`.

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

## Integración (pendiente, requiere tocar la capa BLE → `PENDIENTE_CAPA_BLE.md` #2)

1. Tras activar la sesión ANCS (DS → NS), buscar el servicio AMS en el mismo `BluetoothGatt` (aparece en el descubrimiento, gotcha 17).
2. Suscribirse en este orden, todo por `GattOperationQueue`:
   - el CCCD de Entity Update;
   - el CCCD de Remote Command;
   - las 3 escrituras de `defaultSubscriptions()`.
   La especificación exige suscribirse a Entity Update **antes** de escribir comandos.
3. Las notificaciones pasan por `parseEntityUpdate` y luego por `MediaState.apply`, que alimenta un `StateFlow<MediaState>`.
4. Cuando un valor llega truncado: escribir en Entity Attribute y luego leerlo. Una operación a la vez, sin bloquear ANCS.
5. Los botones de la UI escriben en Remote Command. Solo se muestran los comandos que el iPhone anuncia en `supportedCommands`.
6. Al terminar la sesión se descarta el estado, igual que los UID de ANCS.

## Riesgos y cosas por verificar en el reloj

- Si iOS 26 deja usar AMS con el mismo vínculo sin pedir otro permiso. **Por verificar.**
- Escrituras GATT frecuentes podrían provocar vibraciones del sistema (gotcha 10). Hay que limitar las escrituras a las acciones del usuario.
- Volumen: AMS solo da subir y bajar, no un valor absoluto.
