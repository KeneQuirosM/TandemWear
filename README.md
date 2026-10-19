<p align="center">
  <img src="master-docs/AppleToWearOS/icon-light.png" width="120" alt="TandemWear" />
</p>

# TandemWear

Usa un reloj con Wear OS (por ejemplo, Samsung Galaxy Watch 4) con un iPhone: notificaciones y llamadas por Bluetooth Low Energy, usando los servicios que iOS ya trae (ANCS y, más adelante, AMS para música).

- 100 % en el reloj: **no hay app en el iPhone**.
- Sin nube, sin cuentas, sin servicios de pago.
- Gratis y de código abierto (MIT).

> **Estado:** en desarrollo (Fase 1: fiabilidad). Ver `docs/`.

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

Tests unitarios: `gradlew.bat testDebugUnitTest`

## Licencia

MIT — ver [LICENSE](LICENSE).
