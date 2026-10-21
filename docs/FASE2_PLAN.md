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
  - excepción: los mensajes de error que genera la capa BLE (`ConnectionState.Error`) siguen en inglés. La UI los muestra bajo un título traducido («No se pudo conectar»).
- **Aplazado (decisión del 21-10):** traducir esos errores requiere códigos de error en `BleConnectionManager`. **No se toca `BleConnectionManager`** (ni el emparejamiento ni la reconexión) hasta que termine la prueba de larga duración del usuario y él dé el OK.
- **Firma de depuración fija** (commit aparte): `wearos-app/keystore/debug.keystore`. Las compilaciones de CI y las locales se instalan encima con `adb install -r`. La primera instalación tras el cambio todavía exige desinstalar una vez.

- **2.2 a 2.8 escritos (noche del 21-10)**, con CI en verde paso a paso. Resumen en `docs/RESUMEN_NOCHE.md`, decisiones en `docs/DECISIONES.md` y pruebas manuales en `docs/PRUEBAS_FASE2.md`. Todo está **por verificar** en el reloj.
- **Pendiente: `targetSdk 36`.** Se sube después de la prueba de larga duración del usuario; hasta entonces se queda en 35.

## Reconciliación con el brief v2 (`prompt_fase2_v2.md`)

| Tema | Plan anterior | Brief v2 | Propuesta |
|---|---|---|---|
| Recientes | Historial Room, últimas 50 (decisión 1) | Lista con las últimas 20; detalle con acciones; descartar del reloj | **Guardar 50 y mostrar 20** en Inicio; el detalle muestra las acciones del iPhone solo si la notificación es de la sesión actual (UID válido) |
| Vibración | Canales del sistema + «Silenciar esta app» (decisión 2) | Patrones propios por categoría (ninguna/corta/doble/larga) con vista previa | **Choque.** Opción intermedia: un canal por (categoría, patrón), así la vibración sigue siendo la del sistema y no rompe los avisos emergentes. **Decides tú** |
| No molestar | Horario propio | Horario + excepción de llamadas; selector de hora deslizable (sin teclado numérico) | Brief: `TimePicker` de Wear Compose M3 (rueda), sin campos de texto |
| Privacidad | Ocultar contenido por app | Por app + no mostrar contenido con pantalla bloqueada | Brief (añade `setPublicVersion` / `VISIBILITY_PRIVATE`) |
| Estados de conexión | Los de `ConnectionState` | + «Bluetooth apagado», «reconectando» | Brief. «Bluetooth apagado» lo calcula el ViewModel con el estado del adaptador; **no se toca la capa BLE** |
| Identidad | — | Dos círculos entrelazados en el ícono y el indicador | Brief: ícono adaptativo vectorial nuevo + indicador de conexión |
| Modo ambiente | — | Atenuar y evitar quemado (Wear OS 6 mantiene la actividad visible) | Brief (`AmbientLifecycleObserver` de `androidx.wear:wear`; versión a confirmar) |
| Tile | Estado + 4 accesos (mockup) | Estado + última notificación + pendientes | Brief, con un toque que abre la app; respeta «ocultar contenido» |
| Complicación | Estado | Ícono + texto corto | Igual |
| Acerca de | Pantalla en la navegación | Versión, «basado en WearBridge», licencias | Brief |
| Idioma | Respaldo inglés (decisión 3) | «Español idioma principal» | Compatible: ambos idiomas completos y el inglés como respaldo para otros idiomas |
| Apps conocidas | En 2.4 | B5 | Igual (aún no está hecho) |
| Ajustes en vivo | DataStore | Sin reiniciar el servicio, con tests | El servicio observa los flujos de DataStore; la lógica va en `NotificationPolicy` pura con tests |
| Capturas | — | `@Preview` o tests de capturas, y cómo verlos | **Propuesta:** Roborazzi + Robolectric (gratis y local). Genera PNG de cada pantalla en CI como artefacto, para verlas sin el reloj |
| Ramas | — | `feature/...` | La sesión solo permite `claude/dazzling-heisenberg-ttxsvg`: commits pequeños, cada uno compilando |
| Dependencias | Subir M3 alpha → estable | No cambiar sin OK | Ver «Versiones» abajo; **espero tu OK** |

### Versiones (documentación oficial, consultada el 21-10-2026)

| Librería | Hoy | Última estable | Nota |
|---|---|---|---|
| Wear Compose (foundation/navigation) | 1.4.1 | **1.7.1** (07-10-2026) | 1.8.0-alpha exige Kotlin 2.2 |
| Wear Compose Material 3 | **1.0.0-alpha29** | La página de M3 indica 1.5.0 (27-08-2025) como primera estable; Wear Compose se versiona en conjunto, así que probablemente también 1.7.1 | **Por confirmar en CI.** La estable trae `AppScaffold`/`ScreenScaffold`, `EdgeButton`, `TimePicker` y `TransformingLazyColumn` (en foundation) |
| Tiles | — | 1.6.2 (29-07-2026) | Exige compileSdk ≥ 35. Los eventos de entrada y salida van por `onRecentInteractionEvents` (agrupados); los antiguos no funcionan con target 36 |
| ProtoLayout | — | 1.4.2 (29-07-2026) | `protolayout-material3`: coordenadas por confirmar en Maven |
| Complicaciones | — | watchface 1.3.0 (25-02-2026) | `watchface-complications-data-source-ktx` |

**Decisión del 21-10:** subir Wear Compose/M3 a la versión estable con `compileSdk 36` en un commit aparte. **`targetSdk` se queda en 35** hasta que termine la prueba de larga duración del usuario. Si la subida falla dos veces en CI: volver a la versión alpha y hacer un selector de hora propio, con desplazamiento y sin teclado.

**Riesgo:** subir a Wear Compose 1.7 arrastra Compose 1.9+ y probablemente exige `compileSdk 36`. Propongo hacerlo **en un commit aislado**, verificado en CI, antes del bloque A, junto con el cambio a `targetSdk 36` ya evaluado en `docs/ANDROID16_WEAROS6.md`.
