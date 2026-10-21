package app.tandemwear.watch.ui.nav

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
import app.tandemwear.watch.ui.pairing.PairScreen
import app.tandemwear.watch.ui.recent.NotificationDetailScreen
import app.tandemwear.watch.viewmodel.MainViewModel

object Routes {
    const val HOME = "home"
    const val PAIR = "pair"
    const val ABOUT = "about"
    const val LICENSES = "licenses"
    const val DETAIL = "detail/{id}"
    fun detail(id: Long) = "detail/$id"
}

/**
 * App navigation. Swipe right (and the hardware back button) goes back one screen;
 * SwipeDismissableNavHost handles both.
 */
@Composable
fun TandemNavHost(viewModel: MainViewModel) {
    val navController = rememberSwipeDismissableNavController()
    AppScaffold {
        SwipeDismissableNavHost(navController = navController, startDestination = Routes.HOME) {
            composable(Routes.HOME) {
                HomeScreen(
                    viewModel = viewModel,
                    onPair = { navController.navigate(Routes.PAIR) },
                    onAbout = { navController.navigate(Routes.ABOUT) },
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
