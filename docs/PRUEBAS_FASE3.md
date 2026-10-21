# Fase 3: pruebas manuales con Spotify (Galaxy Watch 4)

Todo está **por verificar en el reloj**. CI solo comprueba que compila y que pasan los tests de la JVM.

**Antes de empezar:**
- iPhone con Spotify y una lista de canciones.
- Reloj con el APK debug y el iPhone conectado.
- El log a mano: `adb exec-out run-as app.tandemwear.watch cat files/diag/tandemwear.log`, o TW Probe ANCS › «Exportar log».

## 1. Suscripción, sin estorbar a ANCS

1. Reconecta: apaga y enciende el Bluetooth del iPhone. En el log, después de `ancs: SESSION ACTIVE`, deben aparecer:
   - unos 5 s después, `ams: starting (session active), 5 steps`;
   - una línea por paso: `ams: notify Entity Update ok in N ms`, `ams: notify Remote Command ok …`, `ams: subscribe player/queue/track ok …`;
   - al final, `ams: subscribed N ms after session active`.
2. **Vibraciones:** con la muñeca quieta, ¿vibra el reloj al reconectar, durante esas 5 escrituras? Anótalo. Si vibra, la solución es pasar `AmsPolicy.START_MODE` a `ON_DEMAND`.
3. **Notificaciones durante la suscripción:** mándate 3 WhatsApp seguidos justo al reconectar. Deben llegar sin retraso visible. Si AMS esperó a ANCS, en el log aparece `(waited N ms for ANCS)`.
4. Si algún paso de AMS falla (`ams: … FAILED — AMS off for this session, ANCS unaffected`), las notificaciones deben seguir llegando igual.

## 2. Reproductor (Inicio › tarjeta de música)

1. Sin nada sonando: Inicio no muestra la tarjeta.
2. Pon Spotify en el iPhone. En Inicio aparece la tarjeta «Sonando · Spotify», con título y artista.
3. Abre la tarjeta. El reproductor debe mostrar:
   - app, título y artista;
   - la barra de progreso avanzando cada segundo, con los tiempos que coinciden con el iPhone (±2 s);
   - el porcentaje de volumen.
4. Botones:
   - **Pausa** pausa Spotify; la barra se detiene y el botón cambia a **Reproducir**;
   - **Siguiente** y **Anterior** cambian de canción, y título, artista y barra se actualizan solos;
   - **Volumen − y +** cambian el volumen del iPhone y el porcentaje se actualiza.
5. Una canción con título largo (más de 40 caracteres): debe verse completo, en 2 líneas con «…». En el log, `ams: request full track/2 ok`.
6. Cambia la canción desde el iPhone con el reproductor abierto: el reloj debe actualizarse solo.
7. Cierra Spotify en el iPhone (deslizándolo fuera en el selector de apps): el reproductor pasa a «Nada suena» y la tarjeta desaparece de Inicio. **Por verificar:** si iOS avisa este cambio.
8. Cambia a la app Música del iPhone: debe mostrar «Música» y su canción.
9. Prueba con TalkBack y con la fuente al máximo: cada botón se anuncia y nada se corta.

## 3. Tile

1. Con Spotify sonando, el Tile muestra la tarjeta (título, artista, «Sonando») y el botón **Pausar**.
2. **Pausar** pausa el iPhone sin abrir la app; el botón cambia a **Reproducir** al instante.
3. Tocar la tarjeta abre el reproductor.
4. Sin música, el Tile vuelve a mostrar la última notificación y el botón **Abrir**.
5. Batería: el Tile no debe actualizarse por cada segundo de progreso, solo al cambiar de canción, al pausar o al reanudar. En el log no debe haber ráfagas de actualizaciones.

## 4. Ajuste «Música»

1. Ajustes › desactiva **Música**:
   - en el log aparecen `ams: music disabled` y `ams: stop notifications …`;
   - la tarjeta desaparece de Inicio y del Tile;
   - el reproductor dice «La música está desactivada».
2. Actívala con el iPhone conectado: en unos segundos aparece `ams: starting (music enabled)` y vuelve la tarjeta.
3. Con «Música» desactivada, reconecta: no debe aparecer ninguna línea `ams: starting`.

## 5. Casos límite

- Llega una llamada mientras suena Spotify: la pantalla de llamada funciona igual que antes.
- Spotify suena por AirPods o por un parlante Bluetooth: los controles siguen funcionando, porque AMS controla al iPhone y no a la salida de audio.
- Un podcast: si el iPhone no informa la duración, la barra queda vacía, pero se ve el tiempo transcurrido.
