# Pendiente en la capa BLE

Cambios que la Fase 2 o la Fase 3 necesitan en `BleConnectionManager`, el emparejamiento o la reconexión. **No se hacen** hasta que termine la prueba de larga duración del usuario y él dé el OK.

| # | Qué | Para qué | Estado |
|---|---|---|---|
| 1 | Códigos de error en `ConnectionState.Error` (en vez de texto en inglés) | Traducir los errores en la UI (es/en) | Aplazado (decisión del 21-10) |
| 2 | Suscribirse a AMS (Entity Update antes de escribir comandos) en la misma sesión que ANCS, a través de `GattOperationQueue` | Controles de música (Fase 3) | Ver `docs/FASE3_PLAN.md` |
