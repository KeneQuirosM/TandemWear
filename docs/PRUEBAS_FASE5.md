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
