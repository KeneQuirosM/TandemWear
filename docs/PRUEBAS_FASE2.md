# Fase 2: pruebas manuales en el Galaxy Watch 4

Todo lo que se lista aquí está **por verificar** en el reloj. CI solo comprueba que compila y que pasan los tests de la JVM.

Instalación:

```bash
adb install -r tandemwear-debug.apk
```

La primera vez con la firma fija hay que desinstalar antes (`adb uninstall app.tandemwear.watch`).

## 2.2 Navegación e Inicio

1. Abre la app con el iPhone conectado. Inicio debe mostrar los dos anillos en turquesa y «Conectado a <iPhone>».
2. Apaga el Bluetooth del iPhone. En pocos segundos debe pasar a «Reconectando…» (ámbar) o «Desconectado» (gris), con el botón «Reconectar».
3. Apaga el Bluetooth del reloj (Ajustes rápidos). Debe aparecer «Bluetooth apagado» con el botón «Ajustes de Bluetooth», que abre los ajustes del sistema.
4. Desliza hacia la derecha en Acerca de y en Licencias, y prueba también el botón atrás: debe volver a la pantalla anterior.
5. En Acerca de deben verse la versión, «Basado en WearBridge…» y la pantalla de licencias.
6. Con TalkBack activado, al cambiar el estado se anuncia el título nuevo (región en vivo).
7. Emparejar: el flujo es el mismo de siempre. Al quedar conectado, la pantalla se cierra sola.

## 2.3 Recientes

1. Envía 3 mensajes de WhatsApp. Deben aparecer en Inicio › Recientes, el más nuevo arriba, con ícono, app, «hace X min», remitente y vista previa.
2. Toca uno para abrir el detalle. Deben estar los botones del iPhone (p. ej. «Borrar») y «Descartar del reloj».
3. «Descartar del reloj» lo quita de la lista y del panel de notificaciones, pero **no** del iPhone.
4. Lee el mensaje en el iPhone: debe desaparecer de Recientes.
5. Apaga y enciende el Bluetooth del iPhone, y abre un detalle antiguo. Ya no deben estar los botones del iPhone, sino el aviso «ya no están disponibles».
6. Con 55 notificaciones, Recientes muestra 20 y la base de datos guarda 50.
7. Empareja de nuevo: el historial se borra.

## 2.4 Ajustes y apps

1. Ajustes › Apps lista las apps que ya enviaron algo, con su ID debajo del nombre.
2. WhatsApp › desactiva «Mostrar notificaciones» y envía un mensaje: no debe aparecer nada en el reloj, y WhatsApp sigue en la lista.
3. «Silenciar esta app»: la notificación llega sin vibrar ni aviso emergente y se ve en el panel.
4. «Ocultar el texto»: solo dice «Nueva notificación de WhatsApp», tanto en el reloj como en Recientes.
5. Cambia un ajuste con el iPhone conectado: aplica a la siguiente notificación sin reconectar.
6. «Sonido y vibración» abre los ajustes de notificaciones de la app en el sistema (canales por categoría).
7. «Guardar historial» desactivado: Recientes queda vacío y no se guarda nada.

## 2.5 No molestar y privacidad

1. Programa No molestar de la hora actual a +1 h. Un mensaje llega sin vibrar ni aviso emergente, pero aparece en Recientes.
2. Con «Permitir llamadas» activado, una llamada suena. Con la opción desactivada, la llamada llega solo como notificación silenciosa.
3. Elige las horas con la corona o deslizando, sin teclado. Prueba un rango que cruce la medianoche (22:00 → 07:00).
4. Privacidad › «Ocultar con pantalla bloqueada»: con el reloj bloqueado solo se ve el nombre de la app (**depende de One UI Watch**).
