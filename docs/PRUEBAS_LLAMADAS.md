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

**Nota:** el receptor de las acciones (`NotificationActionReceiver`) no tiene intent-filter. Por eso las acciones que lo llaman solo con el paquete (Rechazar de la llamada, y los botones de las notificaciones normales) probablemente no le llegan. El nuevo Contestar lo llama de forma explícita. No toqué Rechazar, como pediste; si la prueba 4 lo confirma, lo corrijo en un commit aparte.
