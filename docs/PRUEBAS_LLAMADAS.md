# Pruebas manuales — llamadas

Todo lo que sigue está **por verificar** en el Galaxy Watch 4 con el iPhone. Antes de cada bloque, deja el log abierto:

```bash
adb logcat -s TandemWear
```

## C2. «Contestar» que contesta de verdad (ligado a la sesión ANCS)

Qué cambió: el botón **Contestar** de la notificación de llamada (CallStyle) ya no solo abre la pantalla. Ahora envía al iPhone la acción positiva de ANCS para la sesión en curso. El **Contestar** de la pantalla de llamada también va ligado a la sesión. **Rechazar no cambió.**

1. Llamada normal desde otro teléfono. En la pantalla de llamada del reloj, toca **Contestar**:
   - el iPhone contesta;
   - en el log aparece `call: answer uid=… session=bound`;
   - el reloj pasa a «Activa en el iPhone» o se cierra.
2. Llamada con el reloj bloqueado o con la pantalla apagada. Si solo aparece la notificación de llamada (sin la pantalla completa), toca su **Contestar**:
   - el iPhone contesta;
   - la notificación desaparece y el reloj deja de vibrar;
   - en el log aparece `call: answer … session=bound`.
3. Llamada de WhatsApp: repite los pasos 1 y 2. Si el iPhone no muestra un botón para contestar la llamada de WhatsApp desde fuera de la app, anota qué pasa; puede que ANCS no ofrezca esa acción.
4. **Rechazar**, en la pantalla y en la notificación: debe comportarse exactamente igual que antes. Anota si el Rechazar de la *notificación* hace algo; sospecho que nunca llegó a funcionar (ver nota).
5. Sesión caducada:
   1. empieza una llamada;
   2. apaga el Bluetooth del iPhone mientras suena y vuelve a encenderlo;
   3. si la pantalla de llamada sigue visible, toca **Contestar**.

   Debe aparecer `ancs: action on a notification from an ended session — dismissed locally only` y **no** debe contestarse otra notificación del iPhone.
6. Durante una llamada con Spotify sonando, la música no se ve afectada.
7. Después de contestar desde el reloj, la conversación se oye en el **iPhone** sin cortes. El reloj está conectado al iPhone como manos libres (HFP), así que iOS podría intentar mandar el audio al reloj; ver `docs/AUDIO_LLAMADAS.md`.

**Nota (resuelta en C5 y C6):** el receptor de las acciones (`NotificationActionReceiver`) no tiene intent-filter. Por eso las acciones que lo llaman solo con el paquete (Rechazar de la llamada, y los botones de las notificaciones normales) probablemente no le llegan. El nuevo Contestar lo llama de forma explícita. No toqué Rechazar, como pediste; si la prueba 4 lo confirma, lo corrijo en un commit aparte.

## C3. Pantalla completa como camino principal; apertura directa como respaldo; medición de Telecom

Qué cambió:

- La pantalla de llamada la abre la notificación CallStyle con «pantalla completa».
- Si en 1,5 s no se ve, el reloj la abre directamente (respaldo). Si Android no da el permiso de pantalla completa, la abre directamente desde el principio.
- Telecom (el sistema telefónico del reloj) sigue activo, pero ahora:
  - cuelga bien al contestar;
  - ya no ofrece «retener»;
  - registra cada paso en el log.
- En TW Probe ANCS hay un botón **Calls via Telecom: ON/OFF** para medir sin Telecom.

1. **Permiso.** Comprueba si lo tienes:

   ```bash
   adb shell appops get app.tandemwear.watch USE_FULL_SCREEN_INTENT
   ```

   - Si sale `deny`, en Inicio aparece «Llamadas: permite pantalla completa». Tócalo: se abre la pantalla del sistema (o los ajustes de la app). Permítelo y vuelve: el aviso desaparece.
   - Si en el reloj no hay interruptor, usa `adb shell appops set app.tandemwear.watch USE_FULL_SCREEN_INTENT allow`.
2. **Con permiso, pantalla apagada.** Llamada entrante: se abre la pantalla de llamada. En el log aparece `call: screen shown by full-screen intent after N ms`. Anota N.
3. **Con permiso, usando el reloj** (por ejemplo, en la esfera o en otra app). Llamada entrante: busca en el log una de estas dos líneas:
   - `screen shown by full-screen intent`: la abrió la notificación;
   - `no screen 1500 ms after full-screen intent — direct start (fallback)`: la abrió el respaldo.

   Anota cuál aparece.
4. **Sin permiso** (`appops set … deny`). Llamada entrante: aparece `call: full-screen intent not allowed — direct start` y la pantalla se abre igual.
5. **Contestar o rechazar antes de 1,5 s** desde la notificación: después no debe abrirse ninguna pantalla.
6. **Telecom ON** (por defecto). En una llamada, anota:
   - `telecom: connection created N ms after report`;
   - si aparece `telecom: onShowIncomingCallUi`;
   - si ves `telecom: incoming connection refused`.

   Contesta desde el reloj: después de `call: answer …`, el reloj no se queda «en llamada» (no hay icono de llamada en curso ni pantalla de llamada activa del sistema).
7. **Telecom OFF.** Abre TW Probe ANCS, pon **Calls via Telecom: OFF** y repite los pasos 2 y 3, con la pantalla apagada y también mientras carga el reloj. Aparece `call: Telecom off (debug)`. Compara:
   - ¿se abre la pantalla igual de rápido?
   - ¿se abre sobre la pantalla de carga?
   - ¿vibra?

   Con estos datos decidimos si quitamos Telecom.
8. **Rechazar** sigue igual que antes, con Telecom ON y OFF.

## C4. Tabla de frases de llamada (`CallPhrases`)

Qué cambió:

- Las llamadas que ANCS no marca como «llamada» (WhatsApp llega como Social) se reconocen por frases en español e inglés, sin importar mayúsculas ni acentos.
- Las llamadas **perdidas o finalizadas** ya no hacen sonar el reloj: antes, «Missed voice call» podía abrir la pantalla de llamada.
- Si una notificación de una app de llamadas (WhatsApp, Teléfono, FaceTime) menciona una llamada pero no se reconoce, el log guarda una línea `call: not a ringing call (…): "…"` con el texto normalizado (60 caracteres como máximo). Los mensajes normales de chat no se registran.

Con el iPhone en español:

1. Llamada de voz por WhatsApp: se abre la pantalla de llamada. Si no se abre, copia la línea `call: not a ringing call` del log y la añadimos a la tabla.
2. Videollamada por WhatsApp: igual que el paso 1.
3. Llamada perdida de WhatsApp («Llamada de voz perdida»): llega como notificación normal, **sin** pantalla de llamada.
4. Un chat de WhatsApp que diga «¿hacemos una videollamada?»: notificación normal, sin pantalla de llamada.
5. Llamada normal y FaceTime: igual que antes (llegan con la categoría de llamada).
6. Repite los pasos 1 y 3 con el iPhone en inglés.
7. Llamada grupal de WhatsApp: anota qué pasa. Si no suena, revisa la línea `call: not a ringing call` del log.

Por verificar: las frases exactas de WhatsApp y FaceTime en iOS 26 en español. La tabla está en `ancs/CallPhrases.kt`.

## C5. Rechazar de la notificación de llamada (destino explícito)

Qué cambió: el **Rechazar** de la notificación de llamada ahora llega a su receptor. Antes no llegaba, porque la acción no tenía destino explícito y el receptor no tiene filtro. Envía lo mismo que el Rechazar de la pantalla de llamada: la acción negativa con el UID, sin sesión.

1. Llamada entrante. Si se abre la pantalla de llamada, baja a la notificación (o espera a que la pantalla se cierre) y toca **Rechazar** en la notificación. El iPhone rechaza la llamada y en el log aparece `call: decline uid=… session=none`.
2. Rechazar desde la **pantalla de llamada**: igual que antes.
3. Rechazar con el reloj bloqueado y la pantalla apagada (solo la notificación): el iPhone rechaza la llamada.

## C6. Botones de las notificaciones normales (destino explícito)

Qué cambió: los botones que trae una notificación del iPhone en el reloj (por ejemplo «Marcar como leído» o «Eliminar», según la app) usaban el mismo tipo de acción sin destino que el Rechazar de la llamada. Por eso probablemente no hacían nada. Los botones del **detalle de la notificación dentro de TandemWear** van directo al servicio y ya funcionaban; no cambian.

1. Notificación con acciones (por ejemplo, un correo de Mail o un mensaje de WhatsApp). Toca su botón positivo en la notificación del reloj: la acción ocurre en el iPhone y la notificación desaparece del reloj.
2. Igual con el botón negativo (por ejemplo, «Eliminar» o «Borrar»): ocurre en el iPhone.
3. Una notificación de una sesión anterior (después de reconectar): al tocar un botón, solo se cierra en el reloj y aparece `ancs: action on a notification from an ended session — dismissed locally only`. No debe afectar a otra notificación del iPhone.
4. Desde Inicio › Recientes › detalle: los botones funcionan igual que antes.

## C7. Frases en inglés con la misma regla conservadora

Qué cambió: en inglés solo hacen sonar el reloj las frases con «incoming» o «calling», igual que en español:

- suenan: `incoming call`, `incoming voice/video/audio call`, `is calling`, `calling you`;
- **ya no suenan** «Video call», «Voice call» o «Audio call» a secas, ni «Ringing» ni el emoji ☎ solo, porque pueden aparecer en un chat.

Las notificaciones de WhatsApp, Teléfono o FaceTime que mencionan una llamada pero no se reconocen quedan en el log como `call: not a ringing call (…): "…"`. Con esas líneas ampliamos la tabla.

Con el iPhone en **inglés**:

1. Llamada de voz por WhatsApp: se abre la pantalla de llamada. Si no se abre, copia la línea `call: not a ringing call` del log y pásamela.
2. Videollamada por WhatsApp: igual que el paso 1.
3. Un chat de WhatsApp que diga «video call later?»: notificación normal, sin pantalla de llamada.
4. Llamada perdida («Missed voice call»): notificación normal.
5. Llamada normal y FaceTime: igual que antes, porque llegan con la categoría de llamada.
