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

## 2.6 Tile

1. Mantén presionada la esfera › Tiles › agrega «Estado de TandemWear». La vista previa son los dos anillos.
2. Con el iPhone conectado y sin notificaciones, debe decir «Conectado» (turquesa) y «Aún no hay notificaciones».
3. Al recibir un mensaje, en unos 2 s el Tile muestra el remitente, el texto y «N pendientes».
4. Con «Ocultar el texto» en esa app, solo aparece el nombre de la app. Con «Ocultar con pantalla bloqueada», el Tile no muestra el texto.
5. Al apagar el Bluetooth del iPhone, el Tile pasa a «Reconectando…» (ámbar) o «Desconectado».
6. «Abrir» y la tarjeta abren la app.
7. Batería: con el Tile agregado, el consumo en una hora no debe cambiar respecto a hoy (**por verificar**).

## 2.7 Complicación

1. En una esfera que acepte texto corto, elige «Notificaciones del iPhone». Deben verse los anillos y el número de pendientes.
2. Desconectado, el segundo anillo se ve atenuado.
3. Con TalkBack, lee el estado y los pendientes.
4. Tocarla abre la app.

## 2.8 Accesibilidad, ambiente e ícono

1. Ícono de la app: dos anillos turquesa sobre negro en el lanzador.
2. Modo ambiente (activa «Pantalla siempre encendida» y baja la muñeca con la app abierta): fondo negro, anillos y texto en gris. Cada minuto se desplaza unos píxeles.
3. Fuente al máximo (Ajustes › Pantalla › Tamaño de fuente): nada se corta en Inicio, Detalle ni Ajustes. Todo se desplaza con la corona.
4. TalkBack: cada botón dice su función. Los títulos se anuncian como encabezados. Cada tarjeta de Recientes se lee como «app, remitente: texto, hora».
5. Idioma inglés en el reloj: todos los textos en inglés.

## B1, B2 y B5 (después del análisis de la noche del 20 al 21-10)

**B1, el log en archivo:**
1. Con el APK debug instalado y el servicio corriendo:
   ```bat
   adb exec-out run-as app.tandemwear.watch cat files/diag/tandemwear.log
   ```
   Debe empezar con `=== service start: TandemWear …` y cada línea debe tener fecha completa.
2. Abre TW Probe ANCS y toca «Exportar log». En la pantalla debe aparecer `export: N bytes → /sdcard/Download/tandemwear-log-….txt`. Después:
   ```bat
   adb pull /sdcard/Download/tandemwear-log-….txt
   ```
3. Al cortar y recuperar la conexión deben aparecer `initiator=` en la línea `gatt: connected` y `link: ended … | today …` al cerrarse.

**B2, notificaciones restauradas:**
1. Deja un recordatorio vencido en el iPhone y reinícialo. Al reconectar, el recordatorio aparece en Recientes **sin vibrar ni aviso emergente**. En el log debe verse `restored (dated N min before the session)`.
2. Durante esos primeros minutos, mándate un WhatsApp: debe sonar y avisar como siempre (`new (recent)`).
3. Pon la zona horaria del reloj en manual y repite el paso 1: el recordatorio debe avisar (`watch time zone is manual`).

**B5, «Conectado sin notificaciones»:** si se repite lo de esa noche, Inicio, el Tile y la notificación del servicio deben mostrar «Conectado sin notificaciones» (en ámbar) en vez de «Reconectando». La reconexión se comporta igual que antes.
