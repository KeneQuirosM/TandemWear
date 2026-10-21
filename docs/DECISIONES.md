# Decisiones tomadas sin consulta (noche del 21-10-2026)

Regla: ante una duda de diseño o arquitectura que el brief no cubre, se elige la opción más conservadora y se anota aquí junto con la alternativa descartada.

| # | Paso | Decisión | Alternativa descartada | Motivo |
|---|---|---|---|---|
| 1 | deps | Wear Compose / Material 3 **1.6.2** (estable) | 1.7.1 + AGP 9.1 + compileSdk 37 | 1.7.x exige compileSdk 37 y AGP 9.1 (aar-metadata, revisado en CI, intento 1 fallido). Subir AGP a la versión 9 es un salto mayor |
| 2 | 3.0 | Parser AMS en código puro (`ams/`), **sin conectar** a `BleConnectionManager` | Suscribirse a AMS en la sesión actual | Regla de la noche: no tocar la capa BLE. La integración queda en `docs/FASE3_PLAN.md` |
