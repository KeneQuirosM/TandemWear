# Experimento: ¿puede el Galaxy Watch 4 anunciarse y aparecer en el iPhone?

Objetivo: confirmar, antes de reescribir el emparejamiento, que:

1. El reloj permite que una app anuncie como periférico BLE.
2. El iPhone lo lista en **Ajustes › Bluetooth** gracias a la *solicitud de servicio* ANCS.
3. Tras emparejar, el reloj abre un cliente GATT sobre esa conexión y **ve ANCS**.

La prueba es una pantalla de depuración, **TW Probe**, que solo existe en el APK *debug* (`app/src/debug`). No cambia la app normal.

## Pasos

1. Compilar e instalar:
   ```bat
   cd wearos-app
   gradlew.bat assembleDebug
   adb install -r app\build\outputs\apk\debug\app-debug.apk
   ```
2. En el iPhone: **Ajustes › Bluetooth** activado. Si hay un emparejamiento previo con el reloj, toca (i) › *Omitir dispositivo*.
3. **No abras** la app TandemWear normal durante la prueba: su servicio intentaría conectarse al iPhone por su cuenta y mezclaría resultados. Si ya la abriste, reinicia el reloj antes de empezar.
4. En el reloj: abre **TW Probe** (icono propio en el menú de apps) y concede los permisos de Bluetooth.
5. Mira las primeras líneas:
   - `multiple advertisement supported: true`: el reloj permite anunciar. Si sale `false`, para aquí y mándame la captura.
6. Toca **Start**. Debe salir `ADVERTISING OK … look for "<nombre del reloj>"`.
7. En el iPhone, en **Ajustes › Bluetooth**, busca el nombre del reloj en *Otros dispositivos*. Puede tardar 10–20 s.
8. Toca el reloj en el iPhone y acepta el emparejamiento en **ambos** dispositivos. El reloj puede mostrar un código o un diálogo del sistema.
9. Si iOS pregunta si quieres permitir que el dispositivo reciba tus notificaciones, acepta.
10. En TW Probe espera estas líneas:
   - `INBOUND connection from …`
   - `bond …: BONDED`
   - `ANCS visible: true`
   - `RESULT: SUCCESS — …`

## Qué mandarme

- Captura de pantalla del reloj (o el texto completo de TW Probe).
- Si es posible, el log:
  ```bat
  adb logcat -s TWProbe:I > probe.txt
  ```
- Si el reloj **no aparece** en el iPhone, dímelo. Y si aparece, pero con otro nombre, dime cuál.

## Cómo interpretar

| Resultado | Significado | Siguiente paso |
|---|---|---|
| `multiple advertisement supported: false` o `advertising FAILED` | El reloj no deja anunciar a apps | Plan B: escanear el iPhone ya emparejado (necesita otro mecanismo de descubrimiento) |
| Anuncia, pero el iPhone no lo lista | iOS no muestra la solicitud ANCS en este caso | Probar a anunciar también AMS o HID; investigar |
| Empareja, pero `ANCS visible: false` | Falta el bond o el permiso de notificaciones en iOS | Revisar *Compartir notificaciones del sistema* |
| `RESULT: SUCCESS` | Arquitectura confirmada | Reescribo el emparejamiento sobre este flujo |

Nada de esto se ha podido probar sin hardware: todo el experimento está **por verificar**.
