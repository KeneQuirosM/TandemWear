package app.tandemwear.watch.screenshots

import androidx.compose.runtime.Composable
import androidx.wear.compose.material3.AppScaffold
import app.tandemwear.watch.data.history.HistoryEntry
import app.tandemwear.watch.ui.about.AboutScreen
import app.tandemwear.watch.ui.about.LicensesScreen
import app.tandemwear.watch.ui.home.AmbientScreen
import app.tandemwear.watch.ui.home.HomeContent
import app.tandemwear.watch.ui.home.HomeStatus
import app.tandemwear.watch.ui.recent.NotificationDetailScreen
import app.tandemwear.watch.ui.theme.TandemWearTheme
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.flow.flowOf
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Screenshots of the main screens on a large round watch (Roborazzi + Robolectric native
 * graphics). PNGs land in app/build/outputs/roborazzi/ and CI uploads them as the
 * "screenshots" artifact. They document the UI; they don't compare against a baseline.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = WEAR_LARGE_ROUND)
class ScreenshotTest {

    private val now = System.currentTimeMillis()

    private val recent = listOf(
        entry(3, "WhatsApp", "net.whatsapp.WhatsApp", 4, "Ana", "¿Vamos al cine hoy a las 7?", 2),
        entry(2, "Gmail", "com.google.Gmail", 6, "Banco", "Su estado de cuenta está disponible", 25),
        entry(1, "Waze", "com.waze.iphone", 8, "Tráfico", "Hay un atasco en la ruta 27", 70)
    )

    private fun entry(id: Long, app: String, appId: String, cat: Int, title: String, message: String, minutesAgo: Int) =
        HistoryEntry(
            id = id, androidId = 1000 + id.toInt(), sessionId = 7, uid = id, appId = appId, appName = app,
            categoryId = cat, title = title, message = message, postedAt = now - minutesAgo * 60_000L,
            positiveLabel = null, negativeLabel = "Borrar"
        )

    private fun shot(name: String, content: @Composable () -> Unit) =
        captureRoboImage("build/outputs/roborazzi/$name.png") {
            TandemWearTheme { AppScaffold { content() } }
        }

    private fun home(name: String, status: HomeStatus, items: List<HistoryEntry> = recent) = shot(name) {
        HomeContent(status, items, {}, {}, {}, {}, {}, {})
    }

    @Test
    fun homeConnected() = home("01_home_connected", HomeStatus.Connected("iPhone de Kene"))

    @Test
    fun homeReconnecting() = home("02_home_reconnecting", HomeStatus.Reconnecting("iPhone de Kene"))

    @Test
    fun homeBluetoothOff() = home("03_home_bluetooth_off", HomeStatus.BluetoothOff)

    @Test
    fun homeRepair() = home("04_home_repair", HomeStatus.RepairNeeded)

    @Test
    fun homeNotPaired() = home("05_home_not_paired", HomeStatus.NotPaired, emptyList())

    @Test
    fun homeSpanish() {
        RuntimeEnvironment.setQualifiers("+es")
        home("06_home_connected_es", HomeStatus.Connected("iPhone de Kene"))
    }

    @Test
    fun detail() = shot("07_detail") {
        NotificationDetailScreen(flowOf(recent.first()), currentSessionId = 7, onAction = { _, _ -> }, onDismiss = {}, onGone = {})
    }

    @Test
    fun about() = shot("08_about") { AboutScreen(version = "1.0", onLicenses = {}) }

    @Test
    fun licenses() = shot("09_licenses") { LicensesScreen() }

    @Test
    fun ambient() = shot("10_ambient") { AmbientScreen(HomeStatus.Connected("iPhone"), burnInProtection = true, shiftStep = 2) }
}

/** Robolectric qualifiers for a large round Wear OS screen (as in Roborazzi's device list). */
const val WEAR_LARGE_ROUND = "w227dp-h227dp-small-notlong-round-watch-xhdpi-keyshidden-nonav"
