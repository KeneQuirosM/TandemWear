# Audio de llamadas por el reloj (punto 4): conclusión

Estado: **cerrado como no viable** (decisión del 22-10-2026). No hay código y no lo habrá mientras Android no ofrezca una API pública para el audio del cliente HFP. Las llamadas se contestan o se rechazan desde el reloj, y la conversación sigue en el iPhone.

## Lo que muestra el reloj (volcado del 21-10)

- `HeadsetClientService` (perfil HFP, el reloj como manos libres) está `Connected` al iPhone (`…68:2B ⇒ …84:D5`). El sistema activó HEADSET_CLIENT con política 100 después del SDP de la vinculación (18:33:07).
- `mAudioState: 0`: no hay audio.
- `mAudioRouteAllowed: false`: el reloj no enruta audio HFP.
- `mHsClientAudioPolicy`: `mCallEstablishPolicy`, `mConnectingTimePolicy` e `mInBandRingtonePolicy` valen **2**.
- `HfpClientConnectionService: null`: Telecom no publica las llamadas HFP, así que no hay interfaz del sistema para ellas.
- **SCO llegó a abrirse dos veces:**
  - 18:33:35–18:33:58 (22 s, códec CVSD). Casi sin datos recibidos; la cerró el iPhone.
  - 18:42:01, durante 86 ms. **La cerró el propio reloj** (`CONN_CAUSE_LOCAL_HOST`).

  Encaja con `mAudioRouteAllowed=false`. ¿Hiciste algo a esas horas, como una llamada de prueba o cambiar la salida de audio en el iPhone?

## (1) Qué significa el valor 2

`BluetoothSinkAudioPolicy.POLICY_NOT_ALLOWED = 2`: «Audio is not preferred by HF device for the policy». Las otras constantes son `POLICY_UNCONFIGURED = 0` y `POLICY_ALLOWED = 1`. Los tres campos significan:

- **CallEstablish**: si el reloj quiere el audio al hacer o contestar una llamada;
- **ConnectingTime / ActiveDeviceAfterConnection**: si el reloj quiere el audio al conectarse, incluso con una llamada en curso;
- **InBandRingtone**: si quiere el tono de llamada del iPhone.

Con 2 en los tres, el reloj le dice al iPhone que **no quiere el audio en ningún caso**.

Fuente: `framework/java/android/bluetooth/BluetoothSinkAudioPolicy.java` del módulo Bluetooth de AOSP. Lo leí en la copia de LineageOS 22.2 (Android 15), porque desde aquí no llego a android.googlesource.com; la API es de Android 14. La clase es `@SystemApi` y `@hide`.

## (2) ¿Hay una API pública para enrutar el audio del cliente HFP?

**No.** Revisé `framework/api/current.txt` (la API pública) y `framework/api/system-current.txt` del mismo módulo:

- En la API pública no aparece `BluetoothHeadsetClient`, ni `BluetoothSinkAudioPolicy`, ni `requestAudioPolicyAsSink`, ni `BluetoothProfile.HEADSET_CLIENT`.
- `BluetoothHeadsetClient` es una clase `@SystemApi`. En la API de sistema solo expone estado y política de conexión (`getConnectedDevices`, `getConnectionState`, `setConnectionPolicy`, …), todo con `BLUETOOTH_CONNECT` + **`BLUETOOTH_PRIVILEGED`**.
- `connectAudio`, `acceptCall` y `setAudioRouteAllowed` del cliente HFP son `@hide`, ni siquiera `@SystemApi`. Además llevan `@UnsupportedAppUsage(maxTargetSdk = R)`: una app con targetSdk mayor que 30 no puede llamarlas por reflexión.
- `BluetoothDevice.requestAudioPolicyAsSink` y `isRequestAudioPolicyAsSinkSupported` son `@SystemApi` con `BLUETOOTH_PRIVILEGED`.
- Los `setAudioRouteAllowed` y `connectAudio` de `BluetoothHeadset` son del lado AG (el teléfono), no del manos libres, y también son privilegiados.

`BLUETOOTH_PRIVILEGED` es un permiso `signature|privileged`: solo lo tienen apps firmadas con la clave de la plataforma o preinstaladas como privilegiadas. Una app instalada desde Play o por adb no lo obtiene.

## (3) Conclusión

**TandemWear no puede llevar el audio de las llamadas del iPhone al reloj.** El enlace HFP existe y el iPhone incluso intentó abrir SCO, pero:

- el sistema del reloj (Samsung) lo rechaza (`mAudioRouteAllowed=false`, políticas `NOT_ALLOWED`);
- y la única forma de cambiarlo es con APIs privilegiadas que una app normal no tiene.

Hablar por el reloj solo sería posible si Samsung lo habilitara para iPhone, cosa que el Galaxy Watch 4 no soporta oficialmente.

Decisión: el punto 4 se cierra como **no viable**. Seguimos con lo que sí funciona: contestar o rechazar desde el reloj, y que el audio quede en el iPhone.

Una prueba opcional, sin código, para confirmarlo:

1. Durante una llamada, abre en el iPhone el selector de salida de audio.
2. Si aparece «Galaxy Watch4», elígelo.
3. Lo esperado es que el reloj cierre SCO de inmediato, como a las 18:42:01, y el audio vuelva al iPhone.

## Efecto a vigilar (prueba C2)

Como el reloj está conectado como manos libres, al **contestar desde el reloj** iOS podría intentar mandar el audio al reloj. Comprueba que, al contestar desde el reloj, la conversación se oye en el iPhone sin cortes. Si el audio se pierde unos segundos, es este efecto. La salida sería pedirte que desactives en el reloj la opción del iPhone para llamadas, en Ajustes › Conexiones › Bluetooth del reloj, si Samsung la ofrece.

(`perfiles.txt` no llegó adjunto, solo `bt.txt`, `bt_puesto.txt` y `bt_log.txt`, que está vacío. Si tiene algo distinto, pásamelo.)
