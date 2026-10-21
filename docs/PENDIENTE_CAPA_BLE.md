# Pendiente en la capa BLE

Cambios que la Fase 2 o la Fase 3 necesitan en `BleConnectionManager`, el emparejamiento o la reconexión. **No se hacen** hasta que termine la prueba de larga duración del usuario y él dé el OK.

| # | Qué | Para qué | Estado |
|---|---|---|---|
| 1 | Códigos de error en `ConnectionState.Error` (en vez de texto en inglés) | Traducir los errores en la UI (es/en) | Aplazado (decisión del 21-10) |
| 2 | Suscribirse a AMS (Entity Update antes de escribir comandos) en la misma sesión que ANCS, a través de `GattOperationQueue` | Controles de música (Fase 3) | Ver `docs/FASE3_PLAN.md` |
| 3 | Leer el Current Time Service del iPhone (0x1805, Local Time Information 0x2A0F) al iniciar la sesión | B2: saber la zona horaria real del iPhone en lugar de suponer que coincide con la del reloj. Quitaría la regla de «zona manual = todo nuevo» | Idea. No sé si iOS 26 lo publica (entre los 5 servicios del enlace sin ANCS no lo vimos) |
| 4 | B3 y B4: reconexión que sobreviva a Doze, y cortar los enlaces sin ANCS iniciados por el reloj | Ver `EMPAREJAMIENTO.md`, «Pendiente: B3 y B4» | Esperando datos de B1 |
