# Pruebas manuales — Fase 5

Todo lo que sigue está **por verificar** en el Galaxy Watch 4 con tu iPhone. Antes de cada bloque, deja el log abierto:

```bash
adb logcat -s TandemWear
```

Al terminar, exporta el log desde TW Probe ANCS (botón «Exportar log»):

```bash
adb exec-out run-as app.tandemwear.watch cat files/diag/tandemwear.log
```

## 5.1 Batería del iPhone: solo registro

Qué hace: al descubrir los servicios del iPhone, el log anota la lista completa de UUID. Unos 8 s después de `SESSION ACTIVE`, si el iPhone publica el servicio de batería (`180f`), el reloj lee el nivel (`2a19`) **una vez** y solo lo anota en el log. Aún no se muestra en pantalla.

1. Conecta el iPhone (o espera a que se reconecte). En el log aparece:
   - `gatt: service UUIDs [1800(GAP), 1801, 180a, 180f(Battery), …(ANCS), …(AMS), …]` — **pásame esta línea completa**;
   - unos 8 s después de `SESSION ACTIVE`, una de estas tres líneas:
     - `battery: 62% (2a19 readable)` → la función completa (5.2) es viable;
     - `battery: not exposed on this link (…)` → el iPhone no publica el servicio en nuestro enlace;
     - `battery: read 2a19 failed …` → existe, pero no se deja leer.
2. Compara el porcentaje con el del iPhone (Ajustes › Batería). Pueden diferir en 1 %.
3. Llega una notificación justo después de conectar: se muestra normal, sin retraso. La lectura de batería espera a que ANCS esté libre; si ANCS sigue ocupado 30 s, aparece `battery: read skipped`.
4. Si el reloj vibra justo en el momento de la lectura (unos 8 s después de conectar), anótalo (gotcha 10).
5. Reconecta 2 o 3 veces: cada vez aparece una sola línea `battery: …` por conexión.

## 5.3 VIP y silenciar conversación

Qué hace:
- Una **conversación** es la app más el título que manda el iPhone (el remitente o el nombre del grupo). No distingue mayúsculas ni acentos.
- En **Recientes**, abre una notificación. Debajo de sus acciones están estos botones:
  - **Marcar como VIP** / **Quitar de VIP**;
  - **Silenciar esta conversación**: **15 minutos**, **1 hora** o **Hasta mañana 7:00** (07:00 del día siguiente);
  - si ya está silenciada: **Quitar silencio** («Silenciada hasta 21:45»).
- Las notificaciones con el texto oculto (ajuste por app) no muestran estos botones, porque no tienen título real.
- Reglas:
  - Una conversación silenciada llega sin sonido ni vibración y se guarda en Recientes.
  - Un VIP suena aunque su app esté silenciada.
  - En **nuestras** horas de silencio, un VIP solo suena si activas **Ajustes › Conversaciones › Los VIP suenan en horas de silencio**.
  - Una app desactivada no muestra nada, ni siquiera de un VIP.
  - Silenciar gana sobre VIP. Marcar como VIP quita el silencio de esa conversación.
- **Ajustes › Conversaciones**: el interruptor anterior y las listas «VIP» y «Silenciadas». Toca una entrada para quitarla.
- Los cambios se aplican al momento, sin reiniciar nada.

Pruebas:
1. Pide a alguien que te escriba por WhatsApp. En Recientes › detalle, toca **Silenciar › 15 minutos**. El siguiente mensaje de esa persona llega sin vibrar ni sonar, pero aparece en Recientes. Un mensaje de otra persona suena normal.
2. Espera a que pasen los 15 minutos (o toca **Quitar silencio**): vuelve a sonar.
3. **Hasta mañana 7:00**: en Ajustes › Conversaciones › Silenciadas aparece «… · Silenciada hasta mañana 7:00» (o «7:00 a. m.» si el reloj usa formato de 12 h).
4. Silencia WhatsApp entero (Ajustes › Apps › WhatsApp › Silenciar) y marca a una persona como **VIP**: sus mensajes suenan y los de los demás no.
5. Activa las horas de silencio para que cubran la hora actual:
   - con «Los VIP suenan en horas de silencio» **desactivado**, el VIP llega en silencio;
   - **activado**, suena.
6. Desactiva WhatsApp (Ajustes › Apps): no llega nada, ni siquiera del VIP.
7. Quita un VIP y un silencio desde Ajustes › Conversaciones: se aplica al siguiente mensaje.
8. Con TalkBack: los botones se anuncian («Marcar como VIP», «15 minutos»…).

### Opción avanzada (no necesaria): saltarse el No molestar del reloj

Por defecto, un VIP **solo** se salta las horas de silencio de TandemWear; el No molestar del sistema lo sigue silenciando. Así lo indica la pantalla de Ajustes.

Saltarse el No molestar del sistema requiere dos cosas:
- un canal de notificación propio para VIP con `setBypassDnd(true)`, que **todavía no existe**;
- que el usuario conceda el acceso a No molestar. En Wear OS quizá solo se pueda con `adb shell cmd notification allow_dnd app.tandemwear.watch`.

Nada del producto depende de esto. Si lo quieres, se añade el canal VIP en un commit aparte; **por verificar** que el Galaxy Watch 4 lo respete.

## 5.4a Base de datos versión 2 (llamadas) sin perder el historial

Qué hace: la base de datos pasa a la versión 2 con la tabla `calls`. La migración 1→2 conserva tus notificaciones de Recientes y la lista de apps. Un test la comprueba con una base real de la versión 1. Antes, cualquier cambio de versión vaciaba el historial.

1. **Antes de instalar el APK nuevo**, anota qué hay en Recientes.
2. Instala por encima (`adb install -r …`), sin desinstalar.
3. Recientes muestra lo mismo que antes, y Ajustes › Apps conserva su lista.

## 5.4b Historial de llamadas

Qué hace: **Inicio › Llamadas** lista las llamadas que vio el reloj: nombre o número, icono, tipo, app y hora relativa.
- Tipos:
  - **Perdida** (en rojo): sonó y nadie contestó, o llegó del iPhone la notificación de llamada perdida;
  - **Contestada en el iPhone**;
  - **Contestada desde el reloj**;
  - **Rechazada desde el reloj**.
- Una llamada que sonó en el reloj y su aviso de «llamada perdida» del iPhone cuentan como **una sola** si son del mismo nombre con menos de 3 min de diferencia.
- Se guardan las últimas 100. Se borran al desemparejar o cambiar de iPhone, al desactivar el historial en Ajustes y con **Borrar llamadas**.
- No hay botón para devolver la llamada: el reloj no puede llamar a través del iPhone.

1. Llamada que no contestas: aparece una sola entrada **Perdida** con el nombre y «hace 1 min», no dos.
2. Contesta en el iPhone: aparece **Contestada en el iPhone**.
3. Contesta desde el reloj: aparece **Contestada desde el reloj**.
4. Rechaza desde el reloj (pantalla o notificación): aparece **Rechazada desde el reloj**.
5. Llamada de WhatsApp: la app aparece como «WhatsApp».
6. Con horas de silencio que no dejan pasar llamadas, la llamada no suena, pero el aviso de llamada perdida del iPhone la añade como **Perdida**.
7. Desactiva el historial en Ajustes: la lista se vacía y dice que el historial está desactivado. Las llamadas siguientes no se guardan.
8. **Borrar llamadas** vacía la lista.
9. En el log aparece `calls: ended uid=… as MISSED|ANSWERED|…` por cada llamada.

## 5.2a Batería del iPhone: seguimiento (sin interfaz todavía)

Qué hace:
- Unos 8 s después de `SESSION ACTIVE` lee el nivel y luego se **suscribe** a sus cambios: una sola escritura por conexión.
- Cada paso espera a que ANCS esté libre y, si falla, solo lo anota en el log. Si la suscripción falla, pasa a leer cada 15 min.
- **Ajustes › Batería del iPhone** (activado por defecto) lo enciende o lo apaga al momento. Al apagarlo deja de suscribirse y oculta el nivel.
- **TW Probe ANCS › «iPhone battery: SUBSCRIBE/POLL»** cambia al modo de lectura cada 15 min, sin suscripción. Úsalo si la suscripción hace vibrar el reloj (gotcha 10). Se aplica en la siguiente conexión.

1. Conecta. En el log aparecen, en orden:
   - `battery: starting (session active), mode=SUBSCRIBE`;
   - `battery: read level ok …`;
   - `battery: iPhone 34%`;
   - `battery: subscribe level ok …`.
2. **¿Vibra el reloj** justo al escribir la suscripción (unos 8–9 s después de conectar)? Si vibra, pon POLL en TW Probe, reconecta y comprueba que ya no vibra (`mode=POLL`, sin `subscribe`).
3. Con SUBSCRIBE, desconecta el iPhone del cargador o conéctalo: cuando el porcentaje cambie, aparece una nueva línea `battery: iPhone N%` sin que el reloj haga nada. **Por verificar** cada cuánto avisa iOS.
4. Con POLL, el nivel se actualiza cada 15 min (más en reposo profundo).
5. Ajustes › Batería del iPhone desactivado: aparece `battery: disabled` y `battery: unsubscribe level ok`. Al activarlo de nuevo, vuelve a leer en unos 8 s.
6. Llegan notificaciones al conectar: se muestran sin retraso.

## 5.2b Batería del iPhone en Inicio, Tile y complicación

Qué hace:
- **Inicio**: bajo el estado, «iPhone 34%» con un icono de batería, solo mientras hay conexión y se conoce el nivel.
- **Tile**: el título pasa a «Conectado · 34%».
- **Complicación «Batería del iPhone»**:
  - texto corto: «34%» con icono de teléfono;
  - **anillo** de 0 a 100 %;
  - o solo el icono.
  - Sin dato (desconectado o ajuste apagado), muestra «–» en texto corto y nada en el anillo.
  - Tocarla abre TandemWear.
- Todo se actualiza **solo cuando cambia el nivel**, nunca por temporizador.

1. Con el iPhone conectado: Inicio muestra «iPhone N%», igual que el iPhone (±1 %).
2. Pon la complicación en la esfera, en un hueco corto y, si la esfera lo permite, en uno de anillo. Muestran el mismo valor.
3. Carga o descarga el iPhone hasta que cambie el %. Cambian Inicio, el Tile y la complicación.
4. Desconecta el iPhone (Bluetooth apagado):
   - Inicio deja de mostrar la línea;
   - el Tile dice «Reconectando» sin porcentaje;
   - la complicación muestra «–».
5. Ajustes › Batería del iPhone apagado: desaparece de los tres sitios.
6. TalkBack lee «iPhone 34%» en Inicio y «Batería del iPhone 34%» en la complicación.

## B2 (evaluación) paso 1: hora del iPhone, solo registro

Qué hace:
- Unos 3 s después de `SESSION ACTIVE`, lee **una vez** la hora actual (`2a2b`) y la información de hora local (`2a0f`) del servicio 1805 del iPhone, y solo lo anota en el log.
- Va antes de AMS (5 s) y de la batería (8 s). Pasa por la misma cola y espera a que ANCS esté libre. Solo lee, nunca escribe, y si falla solo lo anota.
- **Ninguna regla cambia**: B2 sigue igual. Las notificaciones que llegan antes o después de la lectura usan la regla actual.

1. Conecta el iPhone. En el log aparecen:
   - `time: 1805 has 2a2b=true 2a0f=…` — **¿2a0f true o false?**
   - `time: 2a2b raw …` y `time: 2a0f raw …`, los bytes tal como llegan;
   - `time: iPhone 2026-10-22T21:30:15.5 | zone -06:00 dst +0 min | skew +0.4 s` — **pásame estas líneas**.
2. Comprueba que la hora coincide con la del iPhone y que la zona es la de tu iPhone (Costa Rica: `-06:00`, sin horario de verano).
3. **¿Vibra el reloj** unos 3 s después de conectar? Solo hay lecturas, así que no debería.
4. Con la zona del reloj en manual distinta de la real (prueba opcional), el `skew` sigue siendo pequeño (segundos): la hora absoluta del iPhone no depende de la zona del reloj.
5. Llegan notificaciones al conectar: se muestran sin retraso.
6. Opcional: cambia la zona del iPhone (Ajustes › General › Fecha y hora) y reconecta. La línea refleja la zona nueva y quizá `last adjust: zone change`.
