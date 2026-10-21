# Fase 2 — Interfaz y ajustes: plan

Referencia visual: `docs/TandemWear-mockups.pdf` (inicio, emparejar, notificación, llamada, Tile, ajustes por app, fuera de alcance).

**Fuera de alcance:** el flujo de emparejamiento y enlace queda tal cual. Las pantallas solo leen `ConnectionState` y envían las mismas acciones al servicio (`START_PAIRING`, `START`, `DISCONNECT`).

## Objetivos

1. UI nueva en Compose para Wear OS + Material 3. Debe funcionar en pantalla redonda de 1,4", con modo oscuro y respetando el tamaño de fuente del sistema.
2. Ajustes por app:
   - activar o desactivar;
   - vibración;
   - ocultar contenido sensible;
   - horario de no molestar propio.
3. Tile con estado y accesos rápidos, y una complicación con el estado.
4. Accesibilidad: contraste ≥ 4,5:1, objetivos táctiles ≥ 48 dp, etiquetas para TalkBack, y textos en `strings.xml` en **español e inglés**.

## Arquitectura propuesta

```
ui/                      Compose (sin lógica de negocio)
  nav/TandemNavHost      SwipeDismissableNavHost: Inicio, Emparejar, Recientes, Detalle,
                         Ajustes, Ajustes de app, Horario, Acerca de
  home/ pairing/ recent/ settings/ theme/
viewmodel/               StateFlow<UiState> por pantalla
data/
  settings/              DataStore (Preferences): reglas por app, horario, opciones globales
  history/               Room: últimas N notificaciones (para «Recientes»)
notify/
  NotificationPolicy     PURA y con tests: (regla de app, horario, hora, categoría) → decisión
                         {mostrar, ocultar contenido, silenciar, descartar}
tile/ complication/      TileService + ComplicationDataSourceService
```

- **`NotificationPolicy` como función pura.** `AncsService.postNotification` le pregunta antes de publicar, así toda la lógica nueva queda cubierta por tests unitarios, como los parsers.
- **Silencio en horario de no molestar:** usaré `setSilent(true)` solo dentro del horario. El *gotcha* 7 prohíbe `setNotificationSilent()` porque mata el aviso emergente, y dentro del horario eso es justo lo que queremos. Por verificar en One UI.
- **Contenido sensible:** se publica con título genérico («Nuevo mensaje de WhatsApp») y `setPublicVersion` para la pantalla bloqueada.
- **Lista de apps en ajustes:** las apps vistas (persistidas) + la tabla de apps conocidas, que pasa a ser un recurso fácil de ampliar con apps de Latinoamérica (bancos de Costa Rica, Waze, Mercado Libre, DiDi, Uber, Netflix…). Este punto quedó de la Fase 1 y lo agrego aquí.

## Dependencias

| Qué | Para qué | Nota |
|---|---|---|
| `androidx.wear.compose:compose-material3` (última estable) | Hoy está en `1.0.0-alpha29` | Versión exacta a confirmar en CI |
| `androidx.wear.compose:compose-navigation` | Navegación con deslizar para cerrar | Ya está en el proyecto |
| `androidx.datastore:datastore-preferences` | Ajustes | Nueva |
| Room (ya en el proyecto, sin uso) | Historial de recientes | Activar KSP de Room |
| `androidx.wear.tiles` + `androidx.wear.protolayout` | Tile | Nueva |
| `androidx.wear.watchface:watchface-complications-data-source-ktx` | Complicación | Nueva |

Se compila en CI (GitHub Actions) en cada push. Wear OS 6 agrupa los eventos de entrada y salida del Tile, así que el Tile no dependerá de ellos para actualizarse; el servicio pedirá la actualización al cambiar el estado.

## Orden de trabajo (un commit pequeño por paso, CI en verde en cada uno)

| # | Tarea | Tests |
|---|---|---|
| 2.1 | Tema oscuro con los colores del mockup; tipografía con `sp`; `strings.xml` en/es con todos los textos actuales | Comprobación automática de que no queden textos fijos en la UI |
| 2.2 | Navegación + Inicio rediseñado (estado, recientes, acceso a Ajustes) + pantalla de emparejar con el nuevo estilo (mismo flujo) | Mapeo de estado de los ViewModels (JVM) |
| 2.3 | Historial (Room): guardar al publicar, borrar al descartar en el iPhone; lista + detalle | Repositorio (Room en memoria) |
| 2.4 | `NotificationPolicy` + ajustes en DataStore + pantalla de apps y de app | Política (tabla de casos) + repositorio |
| 2.5 | Horario de no molestar y contenido sensible | Casos de horario que cruzan la medianoche |
| 2.6 | Tile (estado + 4 accesos) | Lógica del contenido del Tile |
| 2.7 | Complicación (texto corto + icono) | — |
| 2.8 | Pasada de accesibilidad: TalkBack, contraste, fuente 1,3× | Lista manual |

Al terminar entrego el resumen, los comandos y las pruebas manuales en el Galaxy Watch 4.

## Decisiones (21-10-2026)

1. **Historial:** se guarda en el reloj (Room), con las últimas 50; se borra al desemparejar y se puede desactivar en Ajustes. Las notificaciones viejas se **conservan** en el reloj al reconectar.
2. **Vibración:** se usan los ajustes del sistema por categoría (canales), más un «Silenciar esta app».
3. **Idioma de respaldo:** inglés.

## Estado

- **2.1 hecho:**
  - tema oscuro con la paleta del mockup (`TandemColors`) y tamaños en `sp` (`TandemType`);
  - todos los textos de la UI, de la notificación del servicio, de los canales y de Telecom están en `values/strings.xml` (inglés) y `values-es/strings.xml`;
  - `StringResourcesTest` verifica que ambos idiomas tengan las mismas claves y los mismos marcadores, y que la UI no tenga textos fijos;
  - excepción: los mensajes de error que genera la capa BLE (`ConnectionState.Error`) siguen en inglés. La UI los muestra bajo un título traducido («No se pudo conectar»). Traducirlos requiere códigos de error en `BleConnectionManager`; queda para cuando se pueda tocar esa capa.
