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

## (2) Direcciones privadas rotativas: cómo se identifica y reconecta

- Antes del bond solo vemos la dirección privada resoluble (RPA), que cambia cada pocos minutos. **No se guarda.**
- Al emparejar, el iPhone entrega su **dirección de identidad** y su **IRK**. Android guarda el bond bajo la dirección de identidad y el controlador resuelve con la IRK cada RPA nueva a esa identidad.
- Qué guardamos: la dirección que aparece **nueva** en la lista de dispositivos *bonded* justo después de emparejar (`PairingPolicy.newlyBondedAddress`, diferencia antes/después). Es la identidad, no la RPA.
  - Solo se confía en ella mientras el bond exista: `PairedPhoneStore.bondedPhone()` devuelve null si el bond desapareció, y entonces la app vuelve a pedir emparejar.
  - No hay coincidencia por nombre («iPhone»): eso se eliminó.

Reconexión, con dos caminos en paralelo:

1. **El iPhone vuelve al reloj.** Con un iPhone emparejado y desconectado, el reloj anuncia en modo lento (≈1 s de intervalo) y el iPhone reconecta solo a su accesorio emparejado. Al entrar ese enlace (dirección de identidad = la guardada), el reloj abre el cliente GATT.
2. **Conexión en segundo plano del reloj.** Un `connectGatt(autoConnect=true)` a la dirección de identidad deja una petición en la lista del controlador, que la resuelve contra las RPA del iPhone.

Mientras está conectado, el reloj deja de anunciar (ahorra batería).

Otras situaciones:
- **Bluetooth del reloj apagado y encendido:** se reabren el monitor y el anuncio y se relanza la reconexión.
- **Reinicio del reloj:** `BootReceiver` arranca el servicio solo si hay iPhone emparejado y el permiso de Bluetooth está concedido.

**Por verificar en el reloj:** que el iPhone reconecte solo al volver al rango y tras apagar y encender su Bluetooth, y cuánto tarda.

## (3) La conexión cerrada por `B8:45:EB:2A:C6:84` a las 19:46:21

- El prefijo `B8:45:EB` está registrado a **Apple** ([maclookup.app](https://maclookup.app/macaddress/b845eb)). Es casi seguro la **dirección de identidad (pública) de tu iPhone**: la misma que, tras emparejar, debería quedar guardada.
- No es la RPA `61:59:3B…` que usó el enlace LE: no hubo un «GATT client disconnected» a la vez. Fue un **enlace distinto**, del que el servidor GATT solo notificó el cierre, sin evento de conexión. Aparece dos veces porque Android la notificó dos veces.
- Hipótesis más probable: al ver «Galaxy Watch4» en Ajustes, que es un dispositivo de modo dual, iOS abrió también un enlace **Bluetooth clásico (BR/EDR)** con su dirección pública. Al no encontrar nada, lo cerró unos 22 s después (estado 0 = cierre normal). Ocurrió en las dos rondas, unos 22 s tras cada interacción.
- Impacto: ninguno para ANCS. Lo que hay que evitar es emparejar por clásico (un bond clásico no da claves LE). Por eso el reloj inicia el bond sobre el enlace LE.
- **Por verificar:** ahora TW Probe y los logs registran `type=LE/CLASSIC/DUAL` y el estado de bond de cada enlace. Si en el próximo log `B8:45:EB…` sale como `CLASSIC`, queda confirmado.

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
6. Espera **60 s**: la app ignora lo que llega en el primer minuto tras conectar (bug conocido, P1-5, se corrige en la Fase 1). Luego mándate un mensaje desde otro teléfono.
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
8. `bond: iPhone identity address …`
9. `pairing: saved iPhone …`
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
