package app.tandemwear.watch.ui.nav

import androidx.compose.runtime.Composable
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import app.tandemwear.watch.ui.about.AboutScreen
import app.tandemwear.watch.ui.about.LicensesScreen
import app.tandemwear.watch.ui.home.HomeScreen
import app.tandemwear.watch.ui.pairing.PairScreen
import app.tandemwear.watch.viewmodel.MainViewModel

object Routes {
    const val HOME = "home"
    const val PAIR = "pair"
    const val ABOUT = "about"
    const val LICENSES = "licenses"
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
                    onAbout = { navController.navigate(Routes.ABOUT) }
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
        }
    }
}
