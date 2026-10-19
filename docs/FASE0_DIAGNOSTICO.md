# Fase 0 — Diagnóstico (TandemWear, basado en WearBridge)

Base: `k97/WearBridge` @ `c9ff912` (MIT, © 2026 Karthik Rajendran), importado con su historial completo.
Fecha: 2026-10-19. Sin hardware: todo lo marcado **(por verificar)** necesita reloj + iPhone.

## 1. Estado real de compilación y tests

| Qué | Resultado |
|---|---|
| `gradlew assembleDebug` | **No verificado aquí**: la red del entorno bloquea `dl.google.com` (Google Maven + Android SDK). Hay que compilarlo en Windows o habilitar ese dominio. |
| `gradle/wrapper/gradle-wrapper.jar` | Existe y es válido (Gradle 8.13). `gradlew.bat` presente. |
| Tests de parsers ANCS (ejecutados en un arnés JVM con stub de `android.util.Log`) | **20 tests, 19 OK, 1 FALLA**: `DataSourceAssemblerTest › fragment boundary in middle of length field` (esperado `Bob`, obtenido `""`). El repo original ya tenía este test en rojo. |
| Tests diagnósticos extra (no incluidos en el repo) | 2/2 confirman bugs: datos sueltos sin petición generan una notificación vacía; se acepta una respuesta con otro UID. |
| Toolchain | `gradle-daemon-jvm.properties` exige JetBrains JDK 21 (Android Studio actual lo trae; con otro JDK Gradle lo descarga). |

## 2. Bugs, deuda y riesgos (priorizados)

### P0 — Bloquean el objetivo "sin app en iPhone"
1. **El emparejamiento depende 100 % de la app de iOS.** `MainViewModel.scanAndConnect()` solo usa `BleScanner.scanForCompanion()`, que filtra por el UUID de la app companion (`A0E3D5B1-…`). Sin esa app nunca se encuentra el iPhone. El README dice lo contrario. Un iPhone **no** anuncia ANCS por sí solo.
   - Solución estándar (Apple ANCS, NXP/Nordic, ESP32, etc.): el reloj **anuncia** como periférico BLE con *Service Solicitation* de ANCS (y AMS); el usuario lo empareja desde **iPhone › Ajustes › Bluetooth**; cuando el iPhone se conecta, el reloj abre su cliente GATT sobre ese enlace. Es un cambio de arquitectura → requiere tu OK.
   - Riesgo **(por verificar)**: que el Galaxy Watch 4 permita anunciar como periférico a apps de terceros (`BluetoothAdapter.isMultipleAdvertisementSupported`).
2. **`DataSourceAssembler` corrompe o inventa notificaciones** (confirmado con tests):
   - Si el fragmento se corta entre el ID de atributo y su longitud, el atributo queda vacío.
   - Datos que llegan sin petición pendiente producen una notificación vacía, que luego se publica.
   - No valida que el UID de la respuesta sea el pedido.
3. **Las peticiones a Control Point se espacian con un `delay(500)` fijo** en vez de esperar a que termine la respuesta. Con ráfagas o enlace lento, la siguiente petición resetea el ensamblador a media respuesta → notificaciones perdidas o basura.
4. **No hay cola GATT general.** Solo se serializan las escrituras de descriptores. Las escrituras a Control Point, las lecturas (nombre GAP y estado companion, que se lanzan seguidas) y las suscripciones pueden solaparse; Android rechaza la segunda (`false`) y se pierde en silencio. Phase 3 (AMS) necesita esta cola sí o sí.

### P1 — Fiabilidad
5. **Periodo de gracia de 60 s tras cada (re)conexión descarta TODAS las notificaciones nuevas** (no solo el backlog, que ya se filtra con `PreExisting`). Además se reinicia cuando llega el nombre GAP. Salir/entrar de rango = mensajes perdidos.
6. **`getBondedIPhone()` identifica por nombre** (confirmado). `hasBondedIPhone()` tiene otra lista distinta. Hay que guardar la dirección y/o comprobar ANCS.
7. **Reconexión incompleta:** sin receptor de Bluetooth ON/OFF; tras "Service discovery failed" no reintenta; el camino de reintento llama `connect(autoReconnect = false)` y deja la reconexión automática desactivada para siempre; no maneja *Service Changed* (Apple avisa que ANCS puede aparecer/desaparecer); al desconectar no se borran las notificaciones ya publicadas, aunque sus UIDs dejan de valer (sus botones actuarían sobre otra notificación).
8. **Hilos:** los callbacks GATT (hilo binder) mutan el estado (`isWritingDescriptor`, colas, características) a la vez que las corrutinas en `Dispatchers.Main`. Hay condiciones de carrera y todo el trabajo corre en el hilo principal.
9. **Llamadas:** `AncsCallConnection.onAnswer()` hace `setActive()` + `destroy()` sin `setDisconnected()` (fuga de estado en Telecom; además, una llamada propia activa puede robar el foco de audio). Declara `CAPABILITY_HOLD` sin implementarlo. La heurística de WhatsApp/FaceTime solo reconoce textos en inglés. `activeCallUid` no se limpia si se contesta en el iPhone.
10. **Eventos MODIFIED** vuelven a pedir atributos y a hacer sonar/vibrar (falta `setOnlyAlertOnce`). El registro de IDs activos (para el límite de 25) vive en memoria y se pierde si el servicio se reinicia.
11. **Manifest:**
    - Sin `BLUETOOTH_ADVERTISE` (necesario para el punto 1).
    - Si bajamos a `minSdk 30`: `BLUETOOTH_SCAN/CONNECT` no existen en API 30. Hacen falta `BLUETOOTH`/`BLUETOOTH_ADMIN` (con `maxSdkVersion=30`) y `ACCESS_FINE_LOCATION` para escanear; además hay que proteger el código con chequeos de versión.
    - `BootReceiver` arranca el servicio en primer plano aunque no haya iPhone emparejado ni permisos → posible crash al encender.
    - No pide exclusión de optimización de batería. Falta la meta-data `com.google.android.wearable.standalone`. Sobra `DISABLE_KEYGUARD`. `allowBackup=true`.
12. **`minSdk = 33`.** El Galaxy Watch 4 salió con Wear OS 3 (API 30). Samsung lo actualizó después; el nivel real de tu reloj está **(por verificar)** con `adb shell getprop ro.build.version.sdk`.

### P2 — Deuda / producto
13. Código y UI de la app iOS: UUID companion, característica de estado de llamada, "Quick Reply" (pantalla completa en `IncomingCallActivity` que promete algo imposible sin app iOS).
14. `knownAppNames`/iconos con apps de Australia/India; duplicados entre `AncsService` y `AppIconMapper`.
15. Código muerto: `connectToBestDevice`, `BleScanner.scan`, `connectByAddress`, `buildNotificationText`, `AppIconMapper.getIcon`, rama `else if` inalcanzable en `postNotification`. Dependencias sin uso: `play-services-wearable`, Room (sin entidades).
16. README promete vibración propia por categoría: no existe ninguna llamada a `Vibrator.vibrate`.
17. Casi todos los textos están en inglés y codificados en el código; `strings.xml` apenas se usa. Wear Compose Material3 está en `1.0.0-alpha29` (pasar a estable).
18. Logs de cada paquete BLE en hexadecimal también en release (privacidad + batería).

## 3. Investigación pedida

- **Nombre "TandemWear":** no encontré ninguna app con ese nombre exacto. Nombres parecidos: *Tandem Diabetes Care* (t:connect / Tandem Mobi, bomba de insulina *wearable* con app móvil), *Tandem* (intercambio de idiomas) y *TandemGPS*. No pude consultar directamente Google Play ni App Store (bloqueados en este entorno). Riesgo de confusión: bajo-moderado, sobre todo con la marca médica "Tandem".
- **Paso en el iPhone:** Ajustes › Bluetooth › (i) junto al dispositivo › **"Compartir notificaciones del sistema"** ("Share System Notifications"), disponible desde iOS 13. Fuentes: guías de Garmin y How-To Geek (no es documentación de Apple). `master-docs/ancs_research_notes.md` no lo menciona.
- **AMS:** UUIDs confirmados en la especificación oficial de Apple: servicio `89D3502B-0F36-433A-8EF4-C502AD55F8DC`, Remote Command `9B3C81D8-57B1-4A8A-B8DF-0E56F7CA51C2`, Entity Update `2F7CABCE-808D-411F-9A0C-BB92BA96C102`, Entity Attribute `C6B2F38C-23AB-46D8-A6AB-A3A870BBD5D7`. Tu memoria era correcta. Los IDs numéricos (apéndice) se verifican en la Fase 3.
- **Audio de llamadas por HFP (conclusión preliminar): no es viable para una app de terceros.** El rol *Hands-Free* (`BluetoothHeadsetClient`) es API oculta/de sistema en Android y no está expuesta en Wear OS. Además, el reloj tendría que emparejarse con el iPhone por Bluetooth clásico como si fuera un auricular, y eso lo controla el firmware de Samsung. Solo sería posible con root o una app de sistema. No encontré ninguna fuente pública que lo contradiga.
