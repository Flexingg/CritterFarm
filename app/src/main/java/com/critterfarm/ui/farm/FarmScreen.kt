package com.critterfarm.ui.farm

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.critterfarm.data.GameRules
import com.critterfarm.data.local.CritterEntity
import com.critterfarm.data.local.DailySummaryLogEntity
import com.critterfarm.data.local.FarmInventoryEntity
import com.critterfarm.health.HealthConnectAvailability
import com.critterfarm.ui.model.GameZone
import com.critterfarm.ui.theme.CoinGold
import com.critterfarm.ui.theme.DormantGraySurface
import com.critterfarm.ui.theme.PastureGreenLight

@Composable
fun FarmScreen(
    uiState: FarmUiState,
    onIntent: (FarmIntent) -> Unit,
    onOpenOnboarding: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.snackbarMessage) {
        val message = uiState.snackbarMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        onIntent(FarmIntent.DismissMessage)
    }

    uiState.celebration?.let { celebration ->
        CelebrationModal(result = celebration, onDismiss = { onIntent(FarmIntent.DismissCelebration) })
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(snackbarData = data, shape = RoundedCornerShape(16.dp))
            }
        },
    ) { padding ->
        when {
            uiState.isLoading -> LoadingState(padding)
            uiState.critter == null -> EmptyState(padding, onRetry = { onIntent(FarmIntent.Refresh) })
            else -> FarmContent(
                padding = padding,
                critter = uiState.critter,
                inventory = uiState.inventory,
                todayLog = uiState.todayLog,
                isSyncing = uiState.isSyncing,
                isClaiming = uiState.isClaiming,
                healthAvailability = uiState.healthAvailability,
                hasAnyHealthConnection = uiState.hasAnyHealthConnection,
                dormantZones = uiState.dormantZones,
                onClaim = { onIntent(FarmIntent.ClaimDailyTurn) },
                onOpenOnboarding = onOpenOnboarding,
            )
        }
    }
}

@Composable
private fun LoadingState(padding: PaddingValues) {
    Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text("Waking up the farm...", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun EmptyState(padding: PaddingValues, onRetry: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Text("🌱", style = MaterialTheme.typography.displayLarge)
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "Sprout is still waking up...",
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Something interrupted the farm's first bloom. Let's try that again.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onRetry) { Text("Try Again") }
        }
    }
}

@Composable
private fun FarmContent(
    padding: PaddingValues,
    critter: CritterEntity,
    inventory: FarmInventoryEntity?,
    todayLog: DailySummaryLogEntity?,
    isSyncing: Boolean,
    isClaiming: Boolean,
    healthAvailability: HealthConnectAvailability,
    hasAnyHealthConnection: Boolean,
    dormantZones: List<GameZone>,
    onClaim: () -> Unit,
    onOpenOnboarding: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize().padding(padding)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 120.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { HeaderRow(critter = critter, inventory = inventory, isSyncing = isSyncing) }

            item { CritterStage(critter = critter) }

            if (healthAvailability !is HealthConnectAvailability.Available || !hasAnyHealthConnection) {
                item { ConnectHealthBanner(healthAvailability, onOpenOnboarding) }
            }

            item {
                StatPodsRow(
                    todayLog = todayLog,
                    dormantZones = dormantZones,
                )
            }

            items(supplementaryZones(dormantZones, todayLog)) { rowData ->
                SupplementaryZoneCard(rowData)
            }
        }

        ClaimChestBanner(
            isClaimed = todayLog?.chestClaimed == true,
            isClaiming = isClaiming,
            onClaim = onClaim,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp),
        )
    }
}

@Composable
private fun HeaderRow(critter: CritterEntity, inventory: FarmInventoryEntity?, isSyncing: Boolean) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(critter.name, style = MaterialTheme.typography.headlineMedium)
                Text("Level ${critter.level}", style = MaterialTheme.typography.bodyMedium)
            }
            if (isSyncing) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        val xpForLevel = GameRules.xpRequiredForLevel(critter.level)
        LinearProgressIndicator(
            progress = { (critter.xp.toFloat() / xpForLevel.toFloat()).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(8.dp),
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CurrencyPill(emoji = "🪙", value = inventory?.coins ?: 0)
            CurrencyPill(emoji = "🍬", value = inventory?.treats ?: 0)
            CurrencyPill(emoji = "🔮", value = inventory?.manaSparks ?: 0)
        }
    }
}

@Composable
private fun CurrencyPill(emoji: String, value: Int) {
    Surface(shape = RoundedCornerShape(50), color = CoinGold.copy(alpha = 0.25f)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Text(emoji)
            Spacer(modifier = Modifier.width(6.dp))
            Text("$value", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun CritterStage(critter: CritterEntity) {
    Card(
        colors = CardDefaults.cardColors(containerColor = PastureGreenLight),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
            CritterCanvas(mood = critter.mood)
        }
    }
}

@Composable
private fun ConnectHealthBanner(availability: HealthConnectAvailability, onOpenOnboarding: () -> Unit) {
    val message = when (availability) {
        HealthConnectAvailability.NotInstalled ->
            "Health Connect isn't installed yet, so every zone is resting for now."
        HealthConnectAvailability.UpdateRequired ->
            "Health Connect needs an update before it can share data with the farm."
        HealthConnectAvailability.Unavailable ->
            "This device can't run Health Connect, but Sprout is still happy to hang out."
        HealthConnectAvailability.Available ->
            "No health data linked yet — every zone below is a Dormant Zone until you connect."
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Zzz... the farm is quiet", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(12.dp))
            Button(onClick = onOpenOnboarding) { Text("Connect My Health") }
        }
    }
}

@Composable
private fun StatPodsRow(todayLog: DailySummaryLogEntity?, dormantZones: List<GameZone>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        StatPodOrDormant(zone = GameZone.PASTURE_ROAM, dormantZones = dormantZones) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                StepProgressRing(steps = todayLog?.steps ?: 0L)
                Text("Pasture Roam", style = MaterialTheme.typography.labelSmall)
            }
        }
        StatPodOrDormant(zone = GameZone.GROWTH_SPARK, dormantZones = dormantZones) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CalorieFlame(deficitKcal = todayLog?.deficit?.coerceAtLeast(0.0) ?: 0.0)
                Text("Growth Spark", style = MaterialTheme.typography.labelSmall)
            }
        }
        StatPodOrDormant(zone = GameZone.FRESH_POND, dormantZones = dormantZones) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                PondWaterBar(hydrationMl = todayLog?.hydrationMl ?: 0.0)
                Text("Fresh Pond", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun StatPodOrDormant(zone: GameZone, dormantZones: List<GameZone>, content: @Composable () -> Unit) {
    if (dormantZones.contains(zone)) {
        DormantPod(zone)
    } else {
        content()
    }
}

@Composable
private fun DormantPod(zone: GameZone) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            shape = RoundedCornerShape(50),
            color = DormantGraySurface,
            modifier = Modifier.size(96.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(zone.emoji, style = MaterialTheme.typography.headlineMedium)
            }
        }
        Text(zone.displayName, style = MaterialTheme.typography.labelSmall)
    }
}

private data class SupplementaryZoneRow(val zone: GameZone, val valueText: String)

private fun supplementaryZones(
    dormantZones: List<GameZone>,
    todayLog: DailySummaryLogEntity?,
): List<SupplementaryZoneRow> = listOf(
    SupplementaryZoneRow(GameZone.GYM_BARN, "${todayLog?.workouts ?: 0} workouts logged today"),
    SupplementaryZoneRow(
        GameZone.COZY_BARN,
        "${(todayLog?.sleepMinutes ?: 0) / 60}h ${(todayLog?.sleepMinutes ?: 0) % 60}m of sleep last night",
    ),
    SupplementaryZoneRow(
        GameZone.EVOLUTION_SCALE,
        todayLog?.weightKg?.let { "Latest weigh-in: %.1f kg".format(it) } ?: "No weigh-in yet",
    ),
).map { row ->
    if (dormantZones.contains(row.zone)) row.copy(valueText = row.zone.dormantMessage) else row
}

@Composable
private fun SupplementaryZoneCard(row: SupplementaryZoneRow) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(row.zone.emoji, style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(row.zone.displayName, style = MaterialTheme.typography.titleMedium)
                Text(row.valueText, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
