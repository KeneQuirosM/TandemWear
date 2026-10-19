# Android 16 (API 36) y Wear OS 6: impacto en TandemWear

Reloj objetivo: Galaxy Watch 4, SDK 36 (Wear OS 6, One UI 8 Watch). `minSdk` se queda en 33. Hoy: `compileSdk 35`, `targetSdk 35`.

Fuentes oficiales: [cambios para todas las apps](https://developer.android.com/about/versions/16/behavior-changes-all), [cambios al apuntar a 36](https://developer.android.com/about/versions/16/behavior-changes-16), [novedades de Android 16](https://developer.android.com/about/versions/16/features), [Wear OS 6](https://developer.android.com/training/wearables/versions/6/changes), [tipos de FGS](https://developer.android.com/develop/background-work/services/fgs/service-types), [inicio de FGS en segundo plano](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start) y [Android 15 al apuntar a 35](https://developer.android.com/about/versions/15/behavior-changes-15).

## Lo que nos afecta ya (cualquier targetSdk, en un reloj con Android 16)

| Área | Cambio | Impacto |
|---|---|---|
| Bluetooth: bonding | **Pérdida de bond:** si un dispositivo emparejado no se autentica al reconectar, el sistema corta el enlace, **conserva el bond** y muestra un diálogo pidiendo re-emparejar. Antes borraba el bond. | Pasa cuando el usuario "olvida" el reloj en el iPhone. La app debe detectarlo y llevar al usuario al onboarding de re-emparejamiento, no reintentar en bucle. |
| Intents | Protección por defecto contra *Intent redirection*. | No reenviamos intents recibidos. Sin impacto. |
| Broadcasts | La prioridad de broadcasts ordenados solo vale dentro del mismo proceso. | No usamos prioridades. Sin impacto. |
| JobScheduler | Nuevas cuotas de ejecución, también con FGS activo. | No usamos jobs. Sin impacto. |
| Wear OS 6: always-on | La actividad superior sigue *resumed* en modo ambiente. | `IncomingCallActivity` podría quedarse visible en ambiente. Revisar en Fase 2. |

## Lo que cambia si subimos `targetSdk` a 36

| Área | Cambio | Impacto |
|---|---|---|
| Bluetooth | Nuevos broadcasts `ACTION_KEY_MISSING` (pérdida de bond) y `ACTION_ENCRYPTION_CHANGE` (enlace re-cifrado = bond restaurado). Su emisión **varía según el fabricante**. | **A favor:** señal directa para "el iPhone olvidó el reloj". En Samsung está **por verificar**. |
| Bluetooth | `CompanionDeviceManager.removeBond()` público. | Útil para "Olvidar iPhone" desde la app (requiere asociación CDM). |
| Back | La animación de *predictive back* queda activa por defecto y ya no se llama a `onBackPressed`. | No sobreescribimos `onBackPressed`. Bajo riesgo. |
| Edge-to-edge | Ya no se puede desactivar. | Compose para Wear ya lo maneja. Bajo riesgo. |
| Layouts adaptativos | Se ignoran restricciones de orientación en pantallas ≥ 600 dp. | Los relojes están muy por debajo (la doc no menciona Wear explícitamente). Sin impacto. |
| Salud | Permisos de salud granulares. | No usamos salud. Sin impacto. |
| Tiles (Wear OS 6) | `onTileEnterEvent`/`onTileLeaveEvent` llegan agrupados (*batched*). | Afecta al diseño del Tile de la Fase 2: no depender de esos eventos en tiempo real. |
| `scheduleAtFixedRate` | Solo se recupera una ejecución perdida. | No lo usamos. |

## Servicios en primer plano (ya aplican con target 35)

- Tipo `connectedDevice`: requiere `FOREGROUND_SERVICE_CONNECTED_DEVICE` **y** tener concedido en tiempo de ejecución al menos uno de `BLUETOOTH_CONNECT`, `BLUETOOTH_ADVERTISE` o `BLUETOOTH_SCAN`. Si no, el `startForeground` falla. Hoy `BootReceiver` arranca el servicio sin comprobarlo: es un bug ya listado (P1-11).
- Desde `BOOT_COMPLETED` no se pueden iniciar FGS de tipo `dataSync`, `camera`, `mediaPlayback`, `phoneCall`, `mediaProjection` ni `microphone`. **`connectedDevice` sí está permitido.** No hay que usar `phoneCall` para el servicio principal.
- Arrancar un FGS desde segundo plano solo está permitido con exenciones concretas. La pertinente aquí es que el usuario desactive la optimización de batería para la app, o la asociación CDM (`REQUEST_COMPANION_START_FOREGROUND_SERVICES_FROM_BACKGROUND`). Hoy `NotificationActionReceiver` y la UI usan `startService` sobre un servicio ya en primer plano, lo cual está permitido.

## Lanzar la pantalla de llamada desde segundo plano (Android 15+)

Android 15 endurece los *background activity launches*. Por ejemplo, los `PendingIntent` ya no conceden ese privilegio por defecto. Hoy `AncsService` abre `IncomingCallActivity` con `startActivity()` desde el servicio, y además usa un *full-screen intent*.

**Por verificar en el reloj:** que la pantalla de llamada siga apareciendo con la pantalla apagada y con la app en segundo plano. El plan B es depender solo de la notificación `CallStyle` con *full-screen intent*, que es el mecanismo soportado. El permiso `USE_FULL_SCREEN_INTENT` es especial desde Android 14 (ver *gotcha* 8).

## Sin cambios documentados (no pude verificar más)

- **Anuncio BLE y servidor GATT:** ninguna página oficial de Android 16 o Wear OS 6 los menciona. Su comportamiento real en Samsung lo dirá el experimento TW Probe.
- **Permiso de notificaciones (`POST_NOTIFICATIONS`):** sin cambios desde Android 13. El *notification cooldown* o los cambios de agrupado en Android 16 **no** aparecen en estas páginas: por verificar en el reloj.
- **Telecom autogestionado:** ninguna página de Android 16 lo menciona.
- **Optimización de batería / Doze en Wear OS 6:** nada nuevo en estas páginas. One UI Watch puede tener su propio gestor de batería: **por verificar**.

## ¿Subir `targetSdk` a 36? Pros y contras

**Pros**
- Es lo que ejecuta tu reloj: probamos el comportamiento real en vez del modo de compatibilidad.
- Da acceso a `ACTION_KEY_MISSING`/`ACTION_ENCRYPTION_CHANGE` y a `removeBond()` para manejar "el iPhone olvidó el reloj".
- Google Play exige apuntar a versiones recientes. Para Wear OS el calendario exacto está **por verificar**, y hoy distribuimos por `adb`, así que no es urgente.

**Contras / riesgos**
- Requiere `compileSdk 36`. AGP 8.13 lo soporta, pero quizá haya que actualizar dependencias de AndroidX/Compose y el SDK en Android Studio. No puedo compilarlo aquí porque `dl.google.com` sigue bloqueado.
- *Predictive back* y edge-to-edge obligatorios: probablemente sin efecto, pero hay que probar navegación y gestos en el reloj.
- Tiles con eventos agrupados (Fase 2).
- La emisión de `ACTION_KEY_MISSING` depende del fabricante, así que no podemos depender solo de ella.

**Recomendación:** subir `compileSdk` y `targetSdk` a 36 en un commit aislado, en cuanto el proyecto compile en tu Windows o yo tenga acceso a `dl.google.com`. Mantener `minSdk 33`. Antes de eso, conviene una pasada en el reloj de: notificación con pantalla apagada, llamada entrante y botón atrás.
