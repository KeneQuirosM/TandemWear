# Decisiones tomadas sin consulta (noche del 21-10-2026)

Regla: ante una duda de diseño o arquitectura que el brief no cubre, se elige la opción más conservadora y se anota aquí junto con la alternativa descartada.

| # | Paso | Decisión | Alternativa descartada | Motivo |
|---|---|---|---|---|
| 1 | deps | Wear Compose / Material 3 **1.6.2** (estable) | 1.7.1 + AGP 9.1 + compileSdk 37 | 1.7.x exige compileSdk 37 y AGP 9.1 (aar-metadata, revisado en CI, intento 1 fallido). Subir AGP a la versión 9 es un salto mayor |
| 2 | 3.0 | Parser AMS en código puro (`ams/`), **sin conectar** a `BleConnectionManager` | Suscribirse a AMS en la sesión actual | Regla de la noche: no tocar la capa BLE. La integración queda en `docs/FASE3_PLAN.md` |
| 3 | 2.2 | «Bluetooth apagado» se calcula en `MainViewModel` con el broadcast del adaptador | Agregar un estado a `ConnectionState` | No tocar la capa BLE |
| 4 | 2.2 | «Limpiar notificaciones» y «Desconectar» pasan de Inicio a Ajustes | Dejarlos en Inicio | El brief pide una sola acción principal por estado; Desconectar en Inicio es fácil de tocar sin querer |
| 5 | 2.3 | Si el iPhone quita una notificación (leída o borrada allá), también sale de Recientes. Solo se borra la entrada de **esa** sesión | Conservarla en Recientes | El plan de 2.3 dice «borrar al descartar en el iPhone»; así Recientes refleja lo pendiente |
| 6 | 2.3 | El historial se borra al emparejar un iPhone distinto (otra dirección de bond) o cuando el bond desaparece del reloj. **No** se borra con `RepairNeeded` | Borrarlo también con `RepairNeeded` | `RepairNeeded` puede ser un falso positivo de `KeyLossDetector`; perder el historial por eso es peor |
| 7 | 2.3 | Las acciones del iPhone en el detalle solo aparecen si la notificación es de la sesión ANCS actual | Mostrarlas siempre | Los UID se renumeran al reconectar (gotcha 20) |
| 8 | 2.3 | Descartar en el detalle no se desliza: es un botón | Deslizar para revelar (SwipeToReveal) | El brief acepta «deslizar o tocar»; un botón es más fácil con TalkBack |
| 9 | 2.4 | Las apps desactivadas no se publican ni se guardan, pero sí quedan en Ajustes › Apps | Ocultarlas también de la lista | Si no, no habría forma de volver a activarlas |
| 10 | 2.4 | «Silenciar esta app» y el horario de no molestar usan `setSilent(true)` **solo** en esos casos | Bajar la importancia del canal | Es exactamente «sin sonido, vibración ni aviso emergente»; las demás notificaciones siguen sin `setSilent` (gotcha 7) |
| 11 | 2.4 | «Ocultar con pantalla bloqueada» viene **desactivado** por defecto | Activarlo por defecto | Mantiene el comportamiento actual durante la prueba de larga duración |
| 12 | 2.4 | Los ID de bancos de Costa Rica, DiDi y Mercado Libre **no** se agregan a la tabla | Poner IDs supuestos | Regla: no inventar. La pantalla de cada app muestra su ID para agregarlo después |
| 13 | 2.4 | Telegram: se agregan `ph.telegra.Telegraph` (App Store) y se conserva `org.telegram.Telegram` | Reemplazar el viejo | Un ID de más no hace daño |
| 14 | 2.5 | Con no molestar sin «Permitir llamadas», la llamada llega como notificación silenciosa (sin pantalla de llamada) | Descartar la llamada | No perder la información de que llamaron |
| 15 | 2.5 | Si se desactiva la app Teléfono, las llamadas **siguen** sonando | Silenciarlas | Las llamadas se controlan con el horario. Evita perder una llamada por accidente |
| 16 | 2.6/2.7 | El Tile y la complicación van en un solo commit | Dos commits | Comparten `SurfaceState` y la misma petición de actualización |
| 17 | 2.6 | Tile sin `freshnessInterval`: el servicio pide la actualización (agrupada en 1,5 s) | Refresco periódico | Ahorra batería. Wear OS 6 agrupa los eventos de entrada y salida, y no los usamos |
| 18 | 2.6 | «Pendientes» = notificaciones de TandemWear que siguen en el panel del reloj | Entradas del historial | El historial guarda 50 y no baja al leer en el reloj |
| 19 | 2.6 | Con «Ocultar con pantalla bloqueada», el Tile tampoco muestra el texto del mensaje | Mostrarlo siempre | El Tile se ve sin desbloquear |
| 20 | 2.8 | Modo ambiente: se superpone una vista negra con contornos en gris; con protección contra quemado se desplaza unos dp cada minuto | Atenuar la UI interactiva | Menos píxeles encendidos. La navegación queda debajo y conserva su estado |
| 21 | 2.8 | Capturas con Roborazzi en modo grabación (sin comparar con una base); **reemplazado por la #23** | Tests de regresión de capturas | No hay capturas de referencia todavía. Sirven para ver la UI sin el reloj |
| 22 | 2.8 | Roborazzi **1.60.0** | 1.76.0 (la última) | Desde la 1.61.0, Roborazzi está compilado con metadatos de Kotlin 2.3, y el proyecto usa Kotlin 2.1 (CI, intento 1). Subir Kotlin quedaría fuera de alcance |
| 23 | 2.8 | **Capturas con Roborazzi descartadas; se usan `@Preview`** (`ui/preview/Previews.kt`, 11 vistas en reloj redondo grande, incluidas español y fuente 1,3×) | Seguir con Roborazzi | Falló dos veces en CI (Kotlin 2.3 y luego `IllegalAccessException` de Robolectric con gráficos nativos en JDK 17). Era la regla acordada |
