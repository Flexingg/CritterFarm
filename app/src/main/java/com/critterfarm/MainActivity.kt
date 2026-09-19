package com.critterfarm

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.critterfarm.ui.farm.FarmScreen
import com.critterfarm.ui.farm.FarmViewModel
import com.critterfarm.ui.farm.FarmViewModelFactory
import com.critterfarm.ui.onboarding.OnboardingScreen
import com.critterfarm.ui.theme.CritterFarmTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private const val ROUTE_ONBOARDING = "onboarding"
private const val ROUTE_FARM = "farm"

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
                        )
                    }
                }
            }
        }
    }
}
