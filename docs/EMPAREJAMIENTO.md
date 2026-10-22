# Emparejamiento y reconexión (reloj anuncia, iPhone empareja)

Basado en el resultado de TW Probe en Galaxy Watch 4 (SM-R870, SDK 36) del 20-10-2026:

- El reloj anunció como «Galaxy Watch4 (4TKZ)».
- El iPhone se conectó desde una dirección privada (`61:59:3B:55:9B:2F`, `bond=NONE`).
- El reloj vio ANCS (`7905f431`) y AMS (`89d3502b`).

## Flujo

```
Reloj: anuncia (solicitud de servicio ANCS) ──► iPhone › Ajustes › Bluetooth › toca el reloj
iPhone abre el enlace LE ──► reloj: GATT server ve el enlace ──► reloj abre cliente GATT
  └► MTU ► descubrir servicios ► ¿bonded? no ► createBond()  ── el iPhone muestra «Solicitud de enlace»
        └► BONDED ► guardar dirección de identidad ► suscribir Data Source ► suscribir Notification Source
              └► sesión ANCS activa ► dejar de anunciar
```

Piezas: `WatchAdvertiser`, `InboundLinkMonitor`, `BleConnectionManager`, `PairingPolicy` (reglas puras, con tests), `PairedPhoneStore` y `AncsService` (orquestación).

## (1) ¿ANCS exige emparejamiento?

**Sí.** La especificación de Apple dice de Notification Source, Control Point y Data Source: *«All these characteristics require authorization for access»*. En el experimento el servicio era **visible** sin bond, pero TW Probe nunca intentó suscribirse. Ver el servicio no es poder usarlo.

Diseño:

1. Al encontrar ANCS, si el dispositivo no está *bonded*, el reloj llama a `createBond()` sobre el enlace LE del iPhone. Así el emparejamiento ocurre por LE, que es el transporte de ANCS.
2. El estado pasa a `Bonding` y la UI dice «Acepta la solicitud en el iPhone». Hay 90 s de margen.
3. Si el usuario rechaza o vence el tiempo, se muestra error y se desconecta; «Reintentar» abre de nuevo la ventana de emparejamiento.
4. Si iOS o la pila de Android inician el emparejamiento por su cuenta (por ejemplo, tras un error de autenticación al escribir el CCCD), el receptor de bond lo detecta igual.
5. Solo con `BONDED` se suscribe Data Source y luego Notification Source, en el orden que recomienda Apple.
6. Versiones antiguas de iOS solo publican ANCS a accesorios *bonded*. Si no aparece ANCS y no hay bond, primero se empareja y luego se redescubre.
7. Después iOS puede preguntar si se permiten las notificaciones. En (i) junto al reloj debe quedar activado **Compartir notificaciones del sistema**.

**Por verificar en el reloj:** qué diálogo muestra el Galaxy Watch 4 durante el emparejamiento (confirmación numérica o ninguno).

## (2) Direcciones privadas rotativas: qué se guarda y cómo se reconecta

**Resultado del 21-10-2026:** el log dijo `iPhone identity address 7F:68:82:DE:94:D2 (link address 7F:68:82:DE:94:D2)`.

**Corrección:** `7F:68:…` **no** es la dirección de identidad del iPhone. El byte `0x7F` empieza por los bits `01`, así que es una dirección privada resoluble (RPA). Ahora el log la llama «bond address».

**Por qué es lo correcto igualmente:**
- Cuando Android empareja con un dispositivo que usa RPA, guarda el bond bajo la dirección con la que se emparejó (en AOSP, la *pseudo address*). La identidad y la IRK quedan asociadas internamente.
- Cuando el iPhone vuelve con una RPA nueva, el controlador la resuelve con la IRK y Android la sigue reportando a las apps con esa misma dirección del bond.
- Por eso `7F:68:…` es el identificador estable que Android nos da para ese bond, aunque tenga forma de RPA.
- La dirección pública de identidad solo se obtiene con API de sistema (`getIdentityAddress`), que no es pública.

**Qué se guarda:** la dirección que aparece **nueva** en la lista de dispositivos *bonded* al terminar de emparejar. En tu log, `B8:45:EB…` ya estaba antes; la nueva fue `7F:68:…`.

**Cómo se reconoce después:**
- Al subir un enlace, el log muestra `inbound: <dirección> → ATTACH` si coincide con la guardada, o `→ PROBE` si es otro dispositivo *bonded*.
- Si tras reiniciar el reloj o pasados más de 15 min (rotación de la RPA del iPhone) sigue saliendo `7F:68:… → ATTACH`, queda confirmado.
- Si sale otra dirección con `PROBE`, el bond se reporta de otra forma y lo ajusto.

### Quién reconecta: el iPhone, el reloj o ambos

Tras el bond el reloj **dejó de anunciarse porque estaba conectado**. Es intencional: no tiene sentido anunciar con la sesión ANCS activa, y ahorra batería. Al desconectarse se activan **dos caminos a la vez**:

1. **El reloj se anuncia en modo lento** (≈1 s). Si iOS reconecta por su cuenta a su accesorio emparejado, el iPhone abre el enlace.
2. **El reloj deja una conexión en segundo plano** (`connectGatt(autoConnect=true)`) a la dirección del bond. Esta solo prospera si el iPhone emite paquetes conectables con su RPA.

**Por verificar:** no sé con certeza cuál de los dos hace el trabajo en iOS 26 + One UI 8. Las dos razones:
- Apple no documenta cuándo iOS reconecta a accesorios de sistema.
- Cuando ambos caminos están activos, el log no distingue quién inició el enlace: el GATT server ve todos los enlaces LE.

Para medirlo, **TW Probe ANCS** tiene ahora un botón **«Reconnect via:»** con tres modos:

| Modo | Qué queda activo |
|---|---|
| `BOTH` (por defecto) | Anuncio + conexión en segundo plano |
| `ADVERTISE_ONLY` | Solo anuncio: si reconecta, lo hizo el iPhone |
| `AUTOCONNECT_ONLY` | Solo `autoConnect`: si reconecta, lo hizo el reloj |

El log también indica `gatt: connected … via=background request` o `via=attach to inbound link`.

Prueba recomendada: repite «fuera de rango» y «apagar Bluetooth del iPhone» en `ADVERTISE_ONLY` y luego en `AUTOCONNECT_ONLY`. Con eso decidimos si conviene mantener los dos caminos (más batería) o solo uno.

Otras situaciones:
- **Bluetooth del reloj apagado y encendido:** se reabren el monitor y el anuncio y se relanza la reconexión.
- **Reinicio del reloj:** `BootReceiver` arranca el servicio si hay iPhone emparejado y el permiso de Bluetooth está concedido.

## (3) Los enlaces de `B8:45:EB:2A:C6:84`

- El prefijo `B8:45:EB` está registrado a **Apple** ([maclookup.app](https://maclookup.app/macaddress/b845eb)). **Corrección (22-10):** no es tu iPhone sino **otro iPhone** que el reloj conocía desde mayo. Tu iPhone es `…84:D5`. Ver «Reevaluación con el volcado del 21-10» más abajo.
- En la prueba del 21-10 aparece como `type=DUAL bond=BONDED` **antes** de nuestro emparejamiento. Es decir, el reloj ya tenía un bond con el iPhone por esa dirección, distinto del bond LE (`7F:68:…`) que creó TandemWear.
- Origen probable, **por verificar**: un emparejamiento por Bluetooth clásico. O bien iOS, al tocar el reloj (dispositivo dual) en Ajustes, emparejó también por clásico en la primera prueba, o bien hubo un emparejamiento anterior desde los ajustes de Bluetooth del reloj. El enlace que se cerró a las 19:46 en el primer TW Probe era ese clásico.
- **Impacto:** para ANCS, ninguno. TandemWear lo trata como `PROBE` (otro dispositivo) y no lo usa. Pero son dos bonds para el mismo iPhone, y conviene saber si iOS los crea siempre.
- **Para averiguarlo:** al abrir el emparejamiento, el log lista ahora los bonds previos (`pairing: bonded before pairing: …`). Sigue estos pasos:
  1. En el reloj: Ajustes › Conexiones › Bluetooth, desvincula todo lo que sea el iPhone.
  2. En el iPhone: Omitir dispositivo.
  3. Empareja de nuevo con TW Probe ANCS.
  4. Si después vuelve a aparecer `B8:45:EB… DUAL`, iOS crea el bond clásico como parte del emparejamiento; si no, era un resto antiguo.

## Reconexión: ANCS tarda en aparecer (log del 21-10-2026)

**Observado:** tras reconectar, el primer descubrimiento devolvió 5 servicios sin ANCS. La app reintentó 2 veces a ~2 s y se rindió con un error terminal. Solo se recuperó porque el iPhone cerró y reabrió el enlace 3 s después.

**Causa probable:** iOS solo publica ANCS cuando el enlace ya está cifrado con las claves del bond. El primer descubrimiento llega antes, o sale de la caché GATT de Android.

**Ahora** (`AncsDiscoveryPolicy` + `BleConnectionManager.waitForAncs`):
- **Reintentos más pacientes:** esperas de 1,5 s, 3 s, 5 s, 8 s y 12 s (≈30 s en total), y se re-descubre en cuanto llega una de estas señales:
  - **Service Changed** (`onServiceChanged`, API 31+), que es lo que Apple recomienda vigilar;
  - el aviso de **cifrado activado** de Android 16 (`ACTION_ENCRYPTION_CHANGE`; su entrega depende del target y del fabricante, por verificar);
  - o el vencimiento del tiempo.
- **Caché GATT:** desde el 3.er reintento se vacía con `BluetoothGatt.refresh()` antes de re-descubrir. No es API pública; el log dice si funcionó.
- **Nunca error terminal con el iPhone emparejado:** si se agotan los reintentos, se suelta el enlace y la reconexión normal vuelve a intentarlo. El error «sin ANCS» solo aparece durante el emparejamiento con un dispositivo nuevo.
- **Tiempos en el log:** `ancs: ANCS visible N ms after link up (discovery retries: k)` y `ancs: SESSION ACTIVE … N ms after link up`.
- Si ANCS **desaparece** con la sesión activa (iOS lo despublica), la sesión se da por terminada y se espera a que vuelva.

## Reconexión: los UID de ANCS se renumeran

**Observado:** en cada sesión el iPhone renumera los UID: 0..N para las existentes, y las nuevas siguen desde ahí. El id de Android salía del UID, así que una notificación nueva podía **reemplazar en silencio** a una vieja con el mismo número, y un `REMOVED` podía borrar la notificación equivocada.

**Ahora:**
- **Id propio por sesión** (`NotificationIdRegistry`): cada par (sesión, UID) recibe un id de Android nuevo, de un contador persistido que nunca reutiliza ids. Una notificación nueva siempre usa un id nuevo, así que **siempre vibra y se muestra**. Las modificaciones (`MODIFIED`) actualizan la misma en silencio (`setOnlyAlertOnce`).
- **Token de sesión:** el reloj etiqueta los eventos Notification Source con la sesión, incluso los que llegan antes que el estado «Connected».
- **Al terminar una sesión** se limpia todo lo ligado al UID:
  - mapeos UID → id;
  - peticiones de atributos pendientes;
  - ensamblador del Data Source;
  - llamada activa (se cierra su pantalla).
- `REMOVED` solo cancela notificaciones **de la sesión actual**.
- **Las notificaciones de sesiones anteriores se quedan en el reloj.** Sus botones solo las descartan en el reloj: nunca envían la acción al iPhone, porque su UID ahora nombra otra notificación.
- **Log:** una sola línea `ancs: N old notifications skipped (PreExisting)` en vez de una por evento.

**Por decidir contigo:** prefiero conservar las notificaciones viejas (no desaparecen al salir del rango un momento). La alternativa es borrarlas al reconectar, que deja el reloj idéntico al iPhone pero pierde las no leídas en cada corte.

### Pruebas manuales

| Prueba | Qué hacer | Qué debería pasar |
|---|---|---|
| ANCS lento | Reconecta varias veces (fuera de rango, Bluetooth del iPhone) | Si el primer descubrimiento no trae ANCS: `bonded but no ANCS yet … waiting up to …`, luego `re-discovering (trigger: service changed / encryption on / timer)` y `ANCS visible N ms after link up`. Nunca `Error`. |
| UID renumerados | Deja 2–3 notificaciones en el reloj, sal de rango, vuelve y recibe un mensaje nuevo | Una línea `N old notifications skipped`; el mensaje nuevo **vibra y aparece** sin reemplazar a ninguna vieja |
| Borrado sincronizado | Tras reconectar, borra en el iPhone una notificación **nueva** | Desaparece del reloj; las viejas siguen |
| Acción sobre una vieja | Pulsa «Descartar» en una notificación de antes de la reconexión | Se quita del reloj; el log dice `dismissed locally only`; en el iPhone no pasa nada |

## (4) El iPhone olvida el reloj (Android 16)

Si en el iPhone haces «Omitir dispositivo», el iPhone borra sus claves, pero el reloj aún tiene el bond.

- **Android 16:** al reconectar, el sistema detecta que la autenticación falla, **corta el enlace, conserva el bond** y muestra su propio diálogo para volver a emparejar.
  - Si el fabricante lo emite, se recibe `android.bluetooth.device.action.KEY_MISSING`.
  - Con nuestro `targetSdk 35` puede que no llegue: la documentación lo describe para apps que apuntan a 36.
- **Detección propia, para cualquier versión:** se cuentan las señales de pérdida de claves y, con 2 seguidas, se pasa al estado `RepairNeeded` (`KeyLossDetector`). Las señales son:
  - desconexiones con motivo de autenticación (`0x05`, `0x06`, `0x3D`) de un dispositivo *bonded*;
  - el rechazo de la suscripción ANCS con error de autenticación aun estando *bonded*.
- **En `RepairNeeded`:**
  - el reloj deja de anunciar y de reintentar, para no gastar batería en bucle;
  - la pantalla dice «Tu iPhone olvidó este reloj, empareja de nuevo»;
  - «Emparejar» abre otra ventana de emparejamiento y borra la dirección guardada.
- Si el reloj aún lista el iPhone en su Bluetooth, conviene desvincularlo ahí primero. Una app normal no puede borrar el bond: `removeBond()` no es API pública, y la de `CompanionDeviceManager` requiere API 36 y una asociación CDM.

**Por verificar:** si One UI 8 emite `KEY_MISSING` y qué hace su diálogo de re-emparejamiento.

## Prueba manual: TW Probe ANCS (APK debug)

La app «TW Probe ANCS» usa **el mismo código** que la app real (servicio, emparejamiento, ANCS) y muestra su log en pantalla.

1. En el iPhone: Ajustes › Bluetooth › (i) junto a «Galaxy Watch4…» › **Omitir dispositivo** (para empezar limpio).
2. En el reloj: abre **TW Probe ANCS**, concede los permisos y toca **Pair (advertise)**.
3. En el iPhone: Ajustes › Bluetooth › toca el reloj › acepta **Enlazar**. Si el reloj muestra un diálogo, acéptalo también.
4. Si iOS pregunta por las notificaciones, permite. Verifica en (i) que **Compartir notificaciones del sistema** está activo.
5. Espera `ancs: SESSION ACTIVE`.
6. Mándate un mensaje desde otro teléfono. Ya no hace falta esperar 60 s: esa ventana de gracia se eliminó y solo se ignora lo marcado como `PreExisting`.
7. Debe aparecer `FIRST NOTIFICATION RECEIVED: app=… title=… message=…`. En el APK debug se ve el texto; en release solo longitudes.
8. Log completo:
   ```bat
   adb logcat -s TandemWear:I > ancs-probe.txt
   ```

Qué esperar en el log, en orden:
1. `advertise: PAIRING`
2. `inbound: link up … type=LE bond=NONE` y `→ ATTACH`
3. `gatt: connected`
4. `gatt: MTU …`
5. `ANCS=true AMS=true`
6. `bond: requesting pairing`
7. `bond: BONDED`
8. `bond: saved bond address …`
9. `pairing: saved iPhone …` (la dirección del bond; ver punto 2)
10. `ancs: SESSION ACTIVE`
11. `FIRST NOTIFICATION RECEIVED`

### Pruebas de reconexión

| Prueba | Qué hacer | Qué debería pasar |
|---|---|---|
| Fuera de rango | Aléjate unos 30 m del iPhone y vuelve | `gatt: disconnected status=8` (timeout) → `advertise: RECONNECT` → al volver, `inbound: link up` con la **dirección de identidad** → `SESSION ACTIVE` |
| Bluetooth del iPhone | Apágalo 10 s y enciéndelo | Igual que arriba |
| Bluetooth del reloj | Apágalo y enciéndelo | `bluetooth: OFF` / `bluetooth: ON` → reconexión |
| Reinicio del reloj | Reinicia el reloj | El servicio arranca solo y reconecta |
| El iPhone olvida el reloj | En el iPhone: Omitir dispositivo; acércalos | `bond: KEY_MISSING` o 2 señales de clave → `bond: REPAIR NEEDED`, la app pide emparejar de nuevo y deja de anunciar |

## Log en el reloj (B1)

`DiagnosticsLog` guarda cada línea en un archivo que sobrevive al búfer de logcat:
- **Dónde:** `files/diag/tandemwear.log` dentro de los datos de la app. Cuando pasa de 512 KB rota a `.1`, `.2` y `.3`, así que ocupa unos 2 MB como máximo, lo suficiente para varias noches.
- **Formato:** cada línea lleva fecha, hora con milisegundos y huso (`2026-10-21 02:03:25.077 -0600 gatt: …`).
- **Al arrancar el servicio** se escribe `=== service start: TandemWear <versión>, <modelo>, SDK, zone … ===`.
- **Privacidad:** el texto de las notificaciones solo aparece en el APK debug; en release solo se guardan longitudes.

Cómo sacarlo (APK debug, sin preparar nada antes):

```bat
:: Archivo actual
adb exec-out run-as app.tandemwear.watch cat files/diag/tandemwear.log > noche.txt
:: Todo, del más viejo al más nuevo
adb exec-out run-as app.tandemwear.watch sh -c "cat files/diag/tandemwear.log.3 files/diag/tandemwear.log.2 files/diag/tandemwear.log.1 files/diag/tandemwear.log 2>/dev/null" > noche.txt
```

`run-as` funciona porque el APK debug es depurable. El APK release no lo es.

También se puede usar **TW Probe ANCS › «Exportar log»**: une los archivos, del más viejo al más nuevo, en `Download/tandemwear-log-<fecha>.txt` y escribe la ruta en el log de la pantalla. Después:

```bat
adb pull /sdcard/Download/tandemwear-log-<fecha>.txt
```

### Quién inicia cada enlace y si trae ANCS

Cada enlace registra `initiator=` al conectarse. Al cerrarse registra una línea `link: ended … | today …` con el total del día por quien lo inició y cuántos tuvieron ANCS.

Android no informa quién inició el enlace, así que se deduce:
- **iphone:** nuestro cliente se acopló a un enlace entrante («attach to inbound link»);
- **watch:** la petición en espera (autoConnect) se abrió **60 s o menos** antes de que subiera el enlace. Esa noche fueron entre 0,1 y 18 s;
- **unknown:** la petición en espera llevaba más tiempo pendiente, así que pudo tomarla cualquiera de los dos.

Con unos días de uso normal, este contador dice si la hipótesis A se sostiene: que los enlaces iniciados por el reloj no reciben ANCS.

## Noche del 20 al 21-10: ANCS ausente durante horas (análisis)

**Síntoma.** El enlace subía cifrado con el bond, pero mostraba 5 servicios sin ANCS. El iPhone lo cortaba a los ~30 s (status=19), unas 12 veces. Al reiniciar el iPhone, el enlace que **él** inició trajo ANCS en 2,3 s.

**Hipótesis:**
- **A (la más probable):** iOS solo publica ANCS en enlaces que inicia su propio sistema Bluetooth. Los 12 fallos fueron enlaces iniciados por el reloj (`gatt: open autoConnect=true` entre 0,1 y 18 s antes); el único éxito lo inició el iPhone.
- **B:** el Modo de bajo consumo. Es posible, pero Apple no lo documenta y no explica el éxito tras el reinicio.
- **C:** un estado de iOS que solo se limpió al reiniciar.

**Doze en el reloj.** Los temporizadores de la capa BLE usan `delay()` de corrutinas, que cuenta en *uptime*, y el *uptime* se detiene en sueño profundo. Por eso:
- el reintento de 3 a 30 s tardó entre 7 y 23 minutos;
- la espera de ANCS de 1,5 s llegó a durar 30 s.

### Pendiente: B3 y B4 (en espera de datos)

- **B3: reconexión que sobreviva a Doze.**
  - Volver a abrir la petición en espera en el mismo momento del corte, sin temporizador: la petición vive en el controlador y no gasta CPU.
  - Las pausas largas, con `AlarmManager.setAndAllowWhileIdle` sobre `ELAPSED_REALTIME`.
- **B4: cortar los enlaces iniciados por el reloj que no tienen ANCS.**
  - El reloj corta él mismo al reintento 3 (unos 6 s), en vez de esperar los 30 s del iPhone.
  - Después pausa la petición en espera, cada vez más tiempo (5, 15 y 30 min), y mientras tanto solo se anuncia para que inicie el iPhone.

**Por qué esperamos.** B4 parte de la hipótesis A. Si fuera falsa, quitaría al reloj la única forma de reconectar cuando el iPhone no se conecta por el anuncio, como pasó toda esa noche. Antes queremos varios días de datos de uso normal con el contador de B1: cuántos enlaces inicia cada lado y cuántos traen ANCS. B3 depende de cómo quede B4, así que van juntos.

Mientras tanto, B5 solo **muestra** el estado «Conectado al iPhone, sin notificaciones», sin cambiar la lógica de reconexión.

### Reevaluación con el volcado del 21-10 (dos iPhones y la app Merge)

Fuentes: `dumpsys bluetooth_manager` del 21-10 a las 19:17 (historial desde el 20-10 a las 10:35) y el log de TandemWear de la noche (`noche.txt`).

**Quién es quién** (confirmado por ti el 22-10):

| Dirección pública | Direcciones LE que vio el reloj | Qué es |
|---|---|---|
| `…84:D5` | `…94:D2`, `…BB:02`, `…72:EB`, `…71:D0`, `…68:2B` (cada reemparejamiento dejó una) | **Tu iPhone** |
| `B8:45:EB:2A:C6:84` | `…8A:56` | **Otro iPhone**; el reloj lo conocía desde el 18-05 |

**1. La noche del 20 al 21-10 (02:00–05:30) fue toda con tu iPhone.** En el volcado no hay ningún enlace con `…C6:84` en esa franja. Los 13 enlaces de TandemWear fueron con `…BB:02` (tu iPhone), y el iPhone cortó cada uno a los ~30 s (`reason=19`). El otro iPhone no explica esa noche.

**2. Las líneas `inbound: link down B8:45:EB:2A:C6:84` del log de TandemWear no son enlaces reales.** Coinciden al segundo (19 de 19) con los intentos fallidos de la app **`com.mergecorp.merge`**, que intenta conectarse por GATT al otro iPhone. Cada intento dura 30 s y acaba en `status=147`, tiempo agotado. `InboundLinkMonitor` registra también los intentos de conexión de otras apps.

**3. Los cortes «cada ~2 min» son el bucle de reintentos de Merge**, no cortes del enlace clásico de tu iPhone:
- 284 intentos al otro iPhone entre el 20-10 a las 10:37 y el 21-10 a las 07:19;
- mediana de 134 s entre intentos (p10 120 s, p90 764 s);
- de madrugada, solo 2–6 por hora (Doze);
- se acabaron cuando Google Play Services borró ese vínculo, el 21-10 a las 07:21:51.

**4. Correlación que impide decidir B4 con esa noche.** Cada intento de Merge empezó **a menos de ~1,5 s de que subiera nuestro enlace** con tu iPhone. Por ejemplo: 02:23:48 / 02:23:48; 02:31:29 / 02:31:29; 02:55:09 / 02:55:09; 03:38:35 / 03:38:35. Y terminó 1–3 s antes de que tu iPhone cortara el nuestro.
- Probablemente Merge reacciona a `ACTION_ACL_CONNECTED` de cualquier iPhone.
- No podemos saber si sus intentos (el controlador intentando conectar con el otro iPhone durante 30 s) influyeron en que tu iPhone no publicara ANCS y cortara a los 30 s, o si solo coinciden porque ambos duran 30 s.
- La hipótesis A («iOS solo da ANCS en enlaces que inicia él») sigue en pie, pero **sin confirmar**: esa noche hubo un factor de confusión.

**5. De 07:21 a 18:30 del 21-10 TandemWear estuvo emparejado con el OTRO iPhone.**
- A las 07:21 el reloj anunciaba y el otro iPhone se conectó; TandemWear lo adjuntó y se emparejaron por LE (`…8A:56`).
- El emparejamiento se confirmó **en el reloj** a las 07:22:18 (`setPairingConfirmation(true)` desde Ajustes del reloj), y también tuvo que aceptarse en ese iPhone.
- Los datos de ese tramo, incluidos los cortes `reason=8` de las 13:23–17:03 y los contadores de B1, son de ese iPhone: **no sirven para B3/B4**.

**6. Desde las 18:33, con tu iPhone (`…68:2B` ⇒ `…84:D5`), solo hay unos 45 min en el volcado:**
- enlace de 18:32:51 a 18:48:33, que tu iPhone cortó a la vez en LE y en clásico (`0x13`);
- el iPhone volvió a conectar a las 18:48:35 (iniciado por él) y cortó a los 14 s;
- reconexión a las 18:49:04, estable hasta el volcado (19:17), con el enlace clásico/HFP iniciado por el iPhone a las 18:49:08.

Es muy poco para decidir.

**Qué falta para decidir B3/B4** (solo con datos posteriores a las 18:33):
1. Olvidar el vínculo viejo (`…C6:84`) en el reloj.
2. **Desinstalar o desactivar Merge**: reintenta cada ~2 min, compite por la radio y quizá por ANCS (gotcha 15).
3. Varios días de uso normal.
4. Exportar `files/diag/tandemwear.log*` (contadores de B1: quién inicia cada enlace y si trae ANCS) y un `dumpsys bluetooth_manager` al final.
5. Mientras tanto, B4 («cortar enlaces sin ANCS») no debe tocar el enlace clásico/HFP: no es nuestro y no ayuda a ANCS.
