# Resumen de la noche (21-10-2026)

Rama `claude/dazzling-heisenberg-ttxsvg`, sin merge. **Los tags `fase2-paso-2.x` no se pudieron subir**: el proxy de la sesión corta el push de tags («remote end hung up»). Se usa el hash de cada commit.

No se tocó `BleConnectionManager`, ni el emparejamiento, ni la reconexión. Lo que la capa BLE necesitará está en `docs/PENDIENTE_CAPA_BLE.md`.

## Pasos

| Paso | Commit | CI | Qué hay |
|---|---|---|---|
| deps | `1dd9dda` | ✅ (2.º intento) | Wear Compose / M3 **1.6.2** estable, compileSdk 36, **targetSdk 35** |
| 3.0 | `aef53fd` + `fb0e7e0` | ✅ | Parser AMS puro con 8 tests, `docs/FASE3_PLAN.md` |
| 2.2 | `c54e56d` | ✅ | Navegación (deslizar y botón atrás), Inicio con 6 estados, Acerca de y Licencias, emparejar con el estilo nuevo |
| 2.3 | `67844e2` | ✅ | Historial en Room (guarda 50, muestra 20), detalle con acciones del iPhone, «Descartar del reloj» |
| 2.4 | `659fe61` | ✅ | Ajustes en DataStore aplicados en vivo, `NotificationPolicy` pura, ajustes por app, tabla de apps de Latinoamérica |
| 2.5 | `99d928a` | ✅ | No molestar con el `TimePicker` de rueda y excepción de llamadas, privacidad en la pantalla bloqueada |
| 2.6 + 2.7 | `b13419e` | ✅ | Tile (estado, última notificación, pendientes) y complicación (anillos + número) |
| 2.8 | `461ef1a` | ✅ | Modo ambiente, ícono adaptativo de dos anillos, pasada de accesibilidad, README y documentación |
| 2.8 capturas | `90ac5f9`, `a0fb2c1` → `924dbe2` | ❌❌ → ✅ | Roborazzi falló dos veces: primero por los metadatos de Kotlin 2.3 y luego por un `IllegalAccessException` de Robolectric. **Se pasó a `@Preview`**, según la regla acordada (`ui/preview/Previews.kt`) |

Commits de apoyo para CI:
- `a3a3870`: resumen de tests por suite.
- `fb0e7e0`, `303e530`, `dd8db5b` y `6aa05ae`: firmas de la API sacadas de los JAR de fuentes, porque Google Maven no es accesible desde la sesión.

## Tests (CI, JVM)

94 tests en 17 suites, todos en verde en la última corrida (`924dbe2`, CI #26). Cada suite:
- **Protocolo:** `AmsProtocol` 8, `DataSourceAssembler` 12, `ControlPointWriter` 7, `NotificationSourceParser` 8, `AncsEventFilter` 3.
- **Capa BLE y diagnóstico:** `NotificationIdRegistry` 6, `GattOperationQueue` 7, `PairingPolicy` 8, `AncsDiscoveryPolicy` 4, `DiagnosticsLog` 2.
- **Apps y política:** `KnownApps` 3, `NotificationPolicy` 8.
- **Datos:** `HistoryRepository` 4 (Room con Robolectric), `SettingsRepository` 4 (DataStore).
- **UI y superficies:** `TileModel` 3, `HomeStatus` 4, `StringResources` 3.

**Cómo ver las pantallas:** abre `ui/preview/Previews.kt` en Android Studio (vista *Split*). El APK está en GitHub › Actions › *Build APK* › artefacto `tandemwear-debug-apk`.

## Decisiones

En `docs/DECISIONES.md` hay 21 decisiones, cada una con la alternativa descartada. Las más importantes:
- Wear Compose 1.6.2 en vez de 1.7.1. La 1.7.x exige AGP 9.1 y compileSdk 37.
- Lo que el iPhone quita también sale de Recientes, pero solo la entrada de esa sesión.
- El historial **no** se borra con `RepairNeeded`, porque puede ser un falso positivo.
- `setSilent(true)` solo para una app silenciada y para el horario de no molestar.
- «Ocultar con pantalla bloqueada» viene apagado por defecto.
- No se inventaron IDs de bancos de Costa Rica, DiDi ni Mercado Libre. El ID de cada app se ve en Ajustes › Apps; pásamelos y los agrego.
- Si la app Teléfono está desactivada, las llamadas siguen sonando. Solo las silencia el horario sin la excepción.

## Qué probar primero

Lista completa en `docs/PRUEBAS_FASE2.md`. Todo está **por verificar** en el reloj.

1. **Instalar:** `adb uninstall app.tandemwear.watch` (una sola vez, por la firma fija) y luego `adb install -r app-debug.apk`. Es posible que haya que emparejar de nuevo.
2. **Que no se rompió nada:** emparejar, recibir WhatsApp y una llamada. La capa BLE no cambió, pero `AncsService` ahora pasa por la política, el historial y el Tile.
3. **Inicio y Recientes:** estados con el Bluetooth del iPhone y del reloj apagados; leer un mensaje en el iPhone y ver que desaparece de Recientes.
4. **Ajustes de una app:** silenciar y ocultar el texto en WhatsApp, y comprobar que aplica sin reconectar.
5. **No molestar:** un rango que incluya la hora actual. El mensaje llega en silencio y la llamada suena solo con «Permitir llamadas».
6. **Tile y complicación:** agregarlos y comprobar que se actualizan unos 2 s después de cada notificación.
7. **Modo ambiente y fuente al máximo.**

## Pendiente

- `targetSdk 36`: después de tu prueba de larga duración (anotado en `FASE2_PLAN.md`).
- Errores BLE traducidos, que requieren códigos en `BleConnectionManager` (`PENDIENTE_CAPA_BLE.md` #1).
- Integración de AMS (`FASE3_PLAN.md`, `PENDIENTE_CAPA_BLE.md` #2).
- Capturas automáticas en CI: reintentar Roborazzi cuando el proyecto suba a Kotlin ≥ 2.3 (decisiones #22 y #23).
- Opcional: patrones de vibración con vista previa. No se hizo; tu decisión fue usar los canales del sistema.
- IDs de apps de Costa Rica: leerlos en Ajustes › Apps.
