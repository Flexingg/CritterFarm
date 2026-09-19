package com.critterfarm

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.critterfarm.data.Metric
import com.critterfarm.ui.badges.BadgesScreen
import com.critterfarm.ui.badges.BadgesViewModel
import com.critterfarm.ui.badges.BadgesViewModelFactory
import com.critterfarm.ui.barn.BarnScreen
import com.critterfarm.ui.barn.BarnViewModel
import com.critterfarm.ui.barn.BarnViewModelFactory
import com.critterfarm.ui.farm.FarmScreen
import com.critterfarm.ui.farm.FarmViewModel
import com.critterfarm.ui.farm.FarmViewModelFactory
import com.critterfarm.ui.history.HistoryScreen
import com.critterfarm.ui.history.HistoryViewModel
import com.critterfarm.ui.history.HistoryViewModelFactory
import com.critterfarm.ui.onboarding.OnboardingScreen
import com.critterfarm.ui.shop.ShopScreen
import com.critterfarm.ui.shop.ShopViewModel
import com.critterfarm.ui.shop.ShopViewModelFactory
import com.critterfarm.ui.theme.CritterFarmTheme
import com.critterfarm.ui.toMetric

private const val ROUTE_ONBOARDING = "onboarding"
private const val ROUTE_FARM = "farm"
private const val ROUTE_SHOP = "shop"
private const val ROUTE_BARN = "barn"
private const val ROUTE_HISTORY = "history"
private const val ROUTE_BADGES = "badges"
private const val ARG_FOCUS = "focus"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as CritterFarmApplication

        setContent {
            CritterFarmTheme {
                val navController = rememberNavController()
                val startDestination = if (app.onboardingPreferences.hasCompletedOnboarding) {
                    ROUTE_FARM
                } else {
                    ROUTE_ONBOARDING
                }

                NavHost(navController = navController, startDestination = startDestination) {
                    composable(ROUTE_ONBOARDING) {
                        OnboardingScreen(
                            healthConnectManager = app.healthConnectManager,
                            onContinueToFarm = {
                                app.onboardingPreferences.hasCompletedOnboarding = true
                                navController.navigate(ROUTE_FARM) {
                                    popUpTo(ROUTE_ONBOARDING) { inclusive = true }
                                }
                            },
                        )
                    }

                    composable(ROUTE_FARM) {
                        val viewModel: FarmViewModel = viewModel(
                            factory = FarmViewModelFactory(app.gameRepository, app.healthConnectManager),
                        )
                        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                        FarmScreen(
                            uiState = uiState,
                            onIntent = viewModel::onIntent,
                            onOpenOnboarding = { navController.navigate(ROUTE_ONBOARDING) },
                            onOpenShop = { navController.navigate(ROUTE_SHOP) },
                            onOpenHistory = { zone ->
                                val route = zone?.let { "$ROUTE_HISTORY?$ARG_FOCUS=${it.toMetric().name}" }
                                    ?: ROUTE_HISTORY
                                navController.navigate(route)
                            },
                            onOpenBarn = { navController.navigate(ROUTE_BARN) },
                            onOpenBadges = { navController.navigate(ROUTE_BADGES) },
                        )
                    }

                    composable(ROUTE_BADGES) {
                        val viewModel: BadgesViewModel = viewModel(
                            factory = BadgesViewModelFactory(app.gameRepository),
                        )
                        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                        BadgesScreen(
                            uiState = uiState,
                            onBack = { navController.popBackStack() },
                        )
                    }

                    composable(ROUTE_BARN) {
                        val viewModel: BarnViewModel = viewModel(
                            factory = BarnViewModelFactory(app.gameRepository),
                        )
                        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                        BarnScreen(
                            uiState = uiState,
                            onIntent = viewModel::onIntent,
                            onBack = { navController.popBackStack() },
                        )
                    }

                    composable(ROUTE_SHOP) {
                        val viewModel: ShopViewModel = viewModel(
                            factory = ShopViewModelFactory(app.gameRepository),
                        )
                        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                        ShopScreen(
                            uiState = uiState,
                            onIntent = viewModel::onIntent,
                            onBack = { navController.popBackStack() },
                        )
                    }

                    composable(
                        route = "$ROUTE_HISTORY?$ARG_FOCUS={$ARG_FOCUS}",
                        arguments = listOf(
                            navArgument(ARG_FOCUS) {
                                type = NavType.StringType
                                nullable = true
                                defaultValue = null
                            },
                        ),
                    ) { backStackEntry ->
                        val viewModel: HistoryViewModel = viewModel(
                            factory = HistoryViewModelFactory(app.gameRepository),
                        )
                        val focus = backStackEntry.arguments?.getString(ARG_FOCUS)
                            ?.let { name -> Metric.entries.firstOrNull { it.name == name } }
                        // Re-focus whenever the route argument changes (tapping a different row).
                        LaunchedEffect(focus) { viewModel.focusOn(focus) }

                        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                        HistoryScreen(
                            uiState = uiState,
                            onBack = { navController.popBackStack() },
                            onClearFocus = { viewModel.focusOn(null) },
                            onOpenBadges = { navController.navigate(ROUTE_BADGES) },
                        )
                    }
                }
            }
        }
    }
}
