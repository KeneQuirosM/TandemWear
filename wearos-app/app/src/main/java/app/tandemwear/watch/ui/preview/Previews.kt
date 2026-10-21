package app.tandemwear.watch.ui.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.wear.compose.material3.AppScaffold
import app.tandemwear.watch.data.history.HistoryEntry
import app.tandemwear.watch.ui.about.AboutScreen
import app.tandemwear.watch.ui.about.LicensesScreen
import app.tandemwear.watch.ui.home.AmbientScreen
import app.tandemwear.watch.ui.home.HomeContent
import app.tandemwear.watch.ui.home.HomeStatus
import app.tandemwear.watch.ui.music.PlayerContent
import app.tandemwear.watch.ui.music.PlayerModel
import app.tandemwear.watch.ui.recent.NotificationDetailScreen
import app.tandemwear.watch.ui.theme.TandemWearTheme
import kotlinx.coroutines.flow.flowOf

/*
 * Android Studio previews of the main screens on a large round watch (Roborazzi screenshots
 * failed twice in CI, see docs/DECISIONES.md #23). Open this file and use the Split/Design
 * view. Sample data only; nothing here ships to a screen.
 */

private const val DEVICE = "id:wearos_large_round"

private val sampleRecent = listOf(
    sample(3, "WhatsApp", "net.whatsapp.WhatsApp", 4, "Ana", "¿Vamos al cine hoy a las 7?", 2),
    sample(2, "Gmail", "com.google.Gmail", 6, "Banco", "Su estado de cuenta está disponible", 25),
    sample(1, "Waze", "com.waze.iphone", 8, "Tráfico", "Hay un atasco en la ruta 27", 70)
)

private fun sample(id: Long, app: String, appId: String, cat: Int, sender: String, body: String, minutesAgo: Int) =
    HistoryEntry(
        id = id, androidId = 1000 + id.toInt(), sessionId = 7, uid = id, appId = appId, appName = app,
        categoryId = cat, title = sender, message = body,
        postedAt = System.currentTimeMillis() - minutesAgo * 60_000L,
        positiveLabel = null, negativeLabel = "Borrar"
    )

@Composable
private fun Frame(content: @Composable () -> Unit) = TandemWearTheme { AppScaffold { content() } }

@Composable
private fun Home(status: HomeStatus, recent: List<HistoryEntry> = sampleRecent) = Frame {
    HomeContent(status, recent, {}, {}, {}, {}, {}, {})
}

@Preview(device = DEVICE, showSystemUi = true)
@Composable
private fun HomeConnectedPreview() = Home(HomeStatus.Connected("iPhone de Kene"))

@Preview(device = DEVICE, showSystemUi = true)
@Composable
private fun HomeReconnectingPreview() = Home(HomeStatus.Reconnecting("iPhone de Kene"))

@Preview(device = DEVICE, showSystemUi = true)
@Composable
private fun HomeBluetoothOffPreview() = Home(HomeStatus.BluetoothOff)

@Preview(device = DEVICE, showSystemUi = true)
@Composable
private fun HomeRepairPreview() = Home(HomeStatus.RepairNeeded)

@Preview(device = DEVICE, showSystemUi = true)
@Composable
private fun HomeNotPairedPreview() = Home(HomeStatus.NotPaired, emptyList())

@Preview(device = DEVICE, showSystemUi = true, locale = "es")
@Composable
private fun HomeSpanishPreview() = Home(HomeStatus.Connected("iPhone de Kene"))

@Preview(device = DEVICE, showSystemUi = true, fontScale = 1.3f)
@Composable
private fun HomeLargeFontPreview() = Home(HomeStatus.Connected("iPhone de Kene"))

@Preview(device = DEVICE, showSystemUi = true)
@Composable
private fun DetailPreview() = Frame {
    NotificationDetailScreen(flowOf(sampleRecent.first()), currentSessionId = 7, onAction = { _, _ -> }, onDismiss = {}, onGone = {})
}

@Preview(device = DEVICE, showSystemUi = true)
@Composable
private fun AboutPreview() = Frame { AboutScreen(version = "1.0", onLicenses = {}) }

@Preview(device = DEVICE, showSystemUi = true)
@Composable
private fun LicensesPreview() = Frame { LicensesScreen() }

@Preview(device = DEVICE, showSystemUi = true)
@Composable
private fun AmbientPreview() = Frame { AmbientScreen(HomeStatus.Connected("iPhone"), burnInProtection = true, shiftStep = 2) }

private val samplePlaying = PlayerModel.Playing(
    appName = "Spotify", title = "Labios compartidos", artist = "Maná", album = "Amar es combatir",
    isPlaying = true, positionSeconds = 83f, durationSeconds = 301f, volume = 0.6f,
    canTogglePlay = true, canPrevious = true, canNext = true, canVolume = true
)

@Preview(device = DEVICE, showSystemUi = true)
@Composable
private fun PlayerPlayingPreview() = Frame { PlayerContent(samplePlaying, onCommand = {}) }

@Preview(device = DEVICE, showSystemUi = true, locale = "es")
@Composable
private fun PlayerNothingPlayingPreview() = Frame { PlayerContent(PlayerModel.NothingPlaying, onCommand = {}) }

@Preview(device = DEVICE, showSystemUi = true, fontScale = 1.3f)
@Composable
private fun PlayerLargeFontPreview() = Frame { PlayerContent(samplePlaying.copy(isPlaying = false), onCommand = {}) }
