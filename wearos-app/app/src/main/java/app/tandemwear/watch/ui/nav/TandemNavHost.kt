package app.tandemwear.watch.ui.nav

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import app.tandemwear.watch.ui.about.AboutScreen
import app.tandemwear.watch.ui.about.LicensesScreen
import app.tandemwear.watch.ui.home.HomeScreen
import app.tandemwear.watch.ui.home.HomeStatus
import app.tandemwear.watch.ui.pairing.PairScreen
import app.tandemwear.watch.ui.recent.NotificationDetailScreen
import app.tandemwear.watch.ui.settings.AppSettingsScreen
import app.tandemwear.watch.ui.settings.AppsScreen
import app.tandemwear.watch.ui.settings.PrivacyScreen
import app.tandemwear.watch.ui.settings.QuietEdge
import app.tandemwear.watch.ui.settings.QuietHoursScreen
import app.tandemwear.watch.ui.settings.QuietTimePickerScreen
import app.tandemwear.watch.ui.settings.SettingsScreen
import app.tandemwear.watch.viewmodel.MainViewModel
import app.tandemwear.watch.viewmodel.SettingsViewModel
import androidx.lifecycle.viewmodel.compose.viewModel

object Routes {
    const val HOME = "home"
    const val PAIR = "pair"
    const val ABOUT = "about"
    const val LICENSES = "licenses"
    const val DETAIL = "detail/{id}"
    fun detail(id: Long) = "detail/$id"
    const val SETTINGS = "settings"
    const val APPS = "apps"
    const val APP = "app/{appId}"
    fun app(appId: String) = "app/" + Uri.encode(appId)
    const val QUIET = "quiet"
    const val QUIET_TIME = "quiet/{edge}"
    fun quietTime(edge: QuietEdge) = "quiet/" + edge.name
    const val PRIVACY = "privacy"
}

/**
 * App navigation. Swipe right (and the hardware back button) goes back one screen;
 * SwipeDismissableNavHost handles both.
 */
@Composable
fun TandemNavHost(viewModel: MainViewModel) {
    val navController = rememberSwipeDismissableNavController()
    // Activity-scoped: created outside the NavHost, shared by all settings screens
    val settingsViewModel: SettingsViewModel = viewModel()
    AppScaffold {
        SwipeDismissableNavHost(navController = navController, startDestination = Routes.HOME) {
            composable(Routes.HOME) {
                HomeScreen(
                    viewModel = viewModel,
                    onPair = { navController.navigate(Routes.PAIR) },
                    onAbout = { navController.navigate(Routes.ABOUT) },
                    onSettings = { navController.navigate(Routes.SETTINGS) },
                    onOpenNotification = { navController.navigate(Routes.detail(it)) }
                )
            }
            composable(Routes.PAIR) {
                PairScreen(viewModel = viewModel, onDone = { navController.popBackStack() })
            }
            composable(Routes.ABOUT) {
                AboutScreen(
                    version = viewModel.appVersion,
                    onLicenses = { navController.navigate(Routes.LICENSES) }
                )
            }
            composable(Routes.LICENSES) { LicensesScreen() }
            composable(Routes.SETTINGS) {
                val status by viewModel.homeStatus.collectAsState()
                SettingsScreen(
                    viewModel = settingsViewModel,
                    connected = status is HomeStatus.Connected,
                    onApps = { navController.navigate(Routes.APPS) },
                    onQuietHours = { navController.navigate(Routes.QUIET) },
                    onPrivacy = { navController.navigate(Routes.PRIVACY) },
                    onClearNotifications = { viewModel.clearAllNotifications() },
                    onDisconnect = { viewModel.disconnect() }
                )
            }
            composable(Routes.QUIET) {
                QuietHoursScreen(settingsViewModel, onEdit = { navController.navigate(Routes.quietTime(it)) })
            }
            composable(Routes.QUIET_TIME) { backStackEntry ->
                val edge = QuietEdge.entries.firstOrNull { it.name == backStackEntry.arguments?.getString("edge") }
                    ?: QuietEdge.START
                QuietTimePickerScreen(settingsViewModel, edge, onDone = { navController.popBackStack() })
            }
            composable(Routes.PRIVACY) { PrivacyScreen(settingsViewModel) }
            composable(Routes.APPS) {
                AppsScreen(settingsViewModel, onApp = { navController.navigate(Routes.app(it)) })
            }
            composable(Routes.APP) { backStackEntry ->
                val appId = backStackEntry.arguments?.getString("appId").orEmpty()
                val appFlow = remember(appId) { settingsViewModel.app(appId) }
                AppSettingsScreen(settingsViewModel, appId, appFlow)
            }
            composable(Routes.DETAIL) { backStackEntry ->
                val id = backStackEntry.arguments?.getString("id")?.toLongOrNull() ?: -1L
                val entryFlow = remember(id) { viewModel.historyEntry(id) }
                val sessionId by viewModel.currentSessionId.collectAsState()
                NotificationDetailScreen(
                    entryFlow = entryFlow,
                    currentSessionId = sessionId,
                    onAction = { entry, positive -> viewModel.performAction(entry, positive) },
                    onDismiss = { viewModel.dismiss(it) },
                    onGone = { navController.popBackStack() }
                )
            }
        }
    }
}
