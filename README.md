<p align="center">
  <img src="master-docs/AppleToWearOS/icon-light.png" width="120" alt="TandemWear" />
</p>

# TandemWear

Usa un reloj con Wear OS (por ejemplo, Samsung Galaxy Watch 4) con un iPhone: notificaciones y llamadas por Bluetooth Low Energy, usando los servicios que iOS ya trae (ANCS y, más adelante, AMS para música).

- 100 % en el reloj: **no hay app en el iPhone**.
- Sin nube, sin cuentas, sin servicios de pago.
- Gratis y de código abierto (MIT).

> **Estado:** en desarrollo. Fase 1 (fiabilidad) lista; Fase 2 (interfaz y ajustes) escrita y pendiente de probar en el reloj (`docs/PRUEBAS_FASE2.md`). Ver `docs/`.

> **Basado en [WearBridge](https://github.com/k97/WearBridge)** de Karthik Rajendran (MIT). Se conserva su aviso de copyright en [LICENSE](LICENSE).

## Cómo funciona

```
iPhone (iOS)                          Reloj Wear OS
┌──────────────────────┐              ┌──────────────────────────┐
│ ANCS (notificaciones)│◄── BLE ────► │ TandemWear               │
│ integrado en iOS     │   (bond)     │ cliente GATT ANCS        │
└──────────────────────┘              │ servicio en primer plano │
                                      └──────────────────────────┘
```

1. El reloj se anuncia por BLE pidiendo ANCS.
2. En el iPhone: **Ajustes › Bluetooth**, toca el reloj y acepta el emparejamiento.
3. En el iPhone: **Ajustes › Bluetooth › (i)** junto al reloj › activa **Compartir notificaciones del sistema**.
4. El reloj recibe las notificaciones y puede contestar/rechazar llamadas (el audio sigue en el iPhone).

## Qué hace

- **Inicio:** estado de la conexión (conectado, reconectando, desconectado, «el iPhone olvidó el reloj», Bluetooth apagado) con su acción, y las últimas 20 notificaciones.
- **Detalle:** el texto completo, las acciones del iPhone (si la notificación es de la conexión actual) y «Descartar del reloj».
- **Ajustes:**
  - apps una por una: mostrar, silenciar, ocultar el texto;
  - sonido y vibración por categoría, con los ajustes del sistema;
  - horario de no molestar con excepción para llamadas;
  - privacidad en la pantalla bloqueada;
  - historial (se guardan 50 notificaciones, solo en el reloj).
- **Tile y complicación:** estado, última notificación y pendientes.
- Español e inglés, TalkBack, fuente grande, pantalla redonda y modo ambiente.

## Límites conocidos

- No se pueden responder mensajes (ANCS no ofrece campo de texto): solo leer, descartar y las acciones que ofrezca el iPhone.
- El audio de las llamadas se queda en el iPhone.
- Wear OS 3 no se empareja oficialmente con iPhone: el reloj se activa una vez con un teléfono Android.

## Compilar (Windows)

```bat
cd wearos-app
gradlew.bat assembleDebug
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

Tests unitarios: `gradlew.bat testDebugUnitTest`. También generan capturas de las pantallas en `app/build/outputs/roborazzi/`.

**Sin compilar:** cada push a la rama genera, en GitHub › Actions › *Build APK*, dos artefactos:
- `tandemwear-debug-apk`: el APK, firmado siempre con la misma llave de depuración, así que se instala encima con `adb install -r`;
- `screenshots`: PNG de las pantallas principales.

## Licencia

MIT — ver [LICENSE](LICENSE).
