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
