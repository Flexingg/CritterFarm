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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.critterfarm.data.FarmEvent
import com.critterfarm.data.GameRules
import com.critterfarm.data.HarmonyRules
import com.critterfarm.data.HarmonyTier
import com.critterfarm.data.Insight
import com.critterfarm.data.Quest
import com.critterfarm.data.RepairOption
import com.critterfarm.data.SpeciesCatalog
import com.critterfarm.data.StageNames
import com.critterfarm.data.local.CritterEntity
import com.critterfarm.data.local.DailySummaryLogEntity
import com.critterfarm.data.local.FarmInventoryEntity
import com.critterfarm.health.HealthConnectAvailability
import com.critterfarm.ui.model.GameZone
import com.critterfarm.ui.theme.CoinGold
import com.critterfarm.ui.theme.EmberOrange
import com.critterfarm.ui.theme.PastureGreenLight
import com.critterfarm.ui.theme.SlumberLavender
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun FarmScreen(
    uiState: FarmUiState,
    onIntent: (FarmIntent) -> Unit,
    onOpenOnboarding: () -> Unit,
    onOpenShop: () -> Unit,
    onOpenHistory: (GameZone?) -> Unit,
    onOpenBarn: () -> Unit,
    onOpenBadges: () -> Unit,
    onOpenDecor: () -> Unit,
    onOpenRecap: () -> Unit,
    onOpenChallenges: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val haptics = LocalHapticFeedback.current

    // A burst counter rather than a boolean: each celebration should fire its own confetti, so the
    // trigger has to *change*, not just be true.
    var burst by remember { mutableIntStateOf(0) }

    LaunchedEffect(uiState.celebration) {
        if (uiState.celebration != null) {
            burst++
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    LaunchedEffect(uiState.snackbarMessage) {
        val message = uiState.snackbarMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        onIntent(FarmIntent.DismissMessage)
    }

    uiState.celebration?.let { celebration ->
        CelebrationModal(result = celebration, onDismiss = { onIntent(FarmIntent.DismissCelebration) })
    }

    Box(modifier = Modifier.fillMaxSize()) {
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
                streakDays = uiState.streakDays,
                streakMultiplier = uiState.streakMultiplier,
                streakFreezes = uiState.streakFreezes,
                zoneStreaks = uiState.zoneStreaks,
                todayQuests = uiState.todayQuests,
                claimedQuestIds = uiState.claimedQuestIds,
                activeEvent = uiState.activeEvent,
                eventDaysLeft = uiState.eventDaysLeft,
                repairOption = if (uiState.showRepairCard) uiState.repairOption else null,
                harmony = uiState.harmony,
                harmonyTier = uiState.harmonyTier,
                insight = uiState.insight,
                onClaim = { onIntent(FarmIntent.ClaimDailyTurn) },
                onFeed = { onIntent(FarmIntent.FeedCritter) },
                onClaimQuest = { questId -> onIntent(FarmIntent.ClaimQuest(questId)) },
                onRepairStreak = { onIntent(FarmIntent.RepairStreak) },
                onDismissRepairOffer = { onIntent(FarmIntent.DismissRepairOffer) },
                onOpenOnboarding = onOpenOnboarding,
                onOpenShop = onOpenShop,
                onOpenHistory = onOpenHistory,
                onOpenBarn = onOpenBarn,
                onOpenBadges = onOpenBadges,
                onOpenDecor = onOpenDecor,
                onOpenRecap = onOpenRecap,
                onOpenChallenges = onOpenChallenges,
            )
        }
        }

        // Drawn over the whole screen on a big moment, and only then — it renders nothing when idle.
        ConfettiBurst(trigger = burst, modifier = Modifier.fillMaxSize())
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
    streakDays: Int,
    streakMultiplier: Double,
    streakFreezes: Int,
    zoneStreaks: Map<GameZone, Int>,
    todayQuests: List<Quest>,
    claimedQuestIds: Set<String>,
    activeEvent: FarmEvent?,
    eventDaysLeft: Int,
    repairOption: RepairOption?,
    harmony: Int,
    harmonyTier: HarmonyTier,
    insight: Insight?,
    onClaim: () -> Unit,
    onFeed: () -> Unit,
    onClaimQuest: (String) -> Unit,
    onRepairStreak: () -> Unit,
    onDismissRepairOffer: () -> Unit,
    onOpenOnboarding: () -> Unit,
    onOpenShop: () -> Unit,
    onOpenHistory: (GameZone?) -> Unit,
    onOpenBarn: () -> Unit,
    onOpenBadges: () -> Unit,
    onOpenDecor: () -> Unit,
    onOpenRecap: () -> Unit,
    onOpenChallenges: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize().padding(padding)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 120.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                HeaderRow(
                    critter = critter,
                    inventory = inventory,
                    isSyncing = isSyncing,
                    harmony = harmony,
                    harmonyTier = harmonyTier,
                )
            }

            activeEvent?.let { event ->
                item {
                    ActiveEventBanner(event = event, daysLeft = eventDaysLeft, onOpenChallenges = onOpenChallenges)
                }
            }

            repairOption?.let { option ->
                item {
                    StreakRepairCard(
                        option = option,
                        onRepair = onRepairStreak,
                        onLetItGo = onDismissRepairOffer,
                    )
                }
            }

            item {
                StreakCard(
                    streakDays = streakDays,
                    multiplier = streakMultiplier,
                    freezes = streakFreezes,
                    onBuyFreeze = onOpenShop,
                )
            }

            item {
                QuestsCard(
                    items = buildQuestCardItems(todayQuests, todayLog, claimedQuestIds),
                    onClaim = onClaimQuest,
                )
            }

            item {
                CritterStage(
                    critter = critter,
                    hatId = inventory?.equippedHatId,
                    treats = inventory?.treats ?: 0,
                    insight = insight,
                    onFeed = onFeed,
                    onOpenShop = onOpenShop,
                )
            }

            // Targets first: the numbers are the point. Tapping a row opens that metric's history.
            item {
                TodayStatsCard(
                    log = todayLog,
                    dormantZones = dormantZones.toSet(),
                    zoneStreaks = zoneStreaks,
                    onRowClick = { zone -> onOpenHistory(zone) },
                )
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onOpenShop, modifier = Modifier.weight(1f)) {
                        Text("🛍  Shop")
                    }
                    OutlinedButton(onClick = onOpenBarn, modifier = Modifier.weight(1f)) {
                        Text("🐾  Barn")
                    }
                    OutlinedButton(onClick = { onOpenHistory(null) }, modifier = Modifier.weight(1f)) {
                        Text("📊  History")
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onOpenBadges, modifier = Modifier.weight(1f)) {
                        Text("🎖  Badges")
                    }
                    OutlinedButton(onClick = onOpenDecor, modifier = Modifier.weight(1f)) {
                        Text("🌻  Decor")
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onOpenRecap, modifier = Modifier.weight(1f)) {
                        Text("📅  Week")
                    }
                    OutlinedButton(onClick = onOpenChallenges, modifier = Modifier.weight(1f)) {
                        Text("🎯  Challenges")
                    }
                }
            }

            if (healthAvailability !is HealthConnectAvailability.Available || !hasAnyHealthConnection) {
                item { ConnectHealthBanner(healthAvailability, onOpenOnboarding) }
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
private fun HeaderRow(
    critter: CritterEntity,
    inventory: FarmInventoryEntity?,
    isSyncing: Boolean,
    harmony: Int,
    harmonyTier: HarmonyTier,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(critter.name, style = MaterialTheme.typography.headlineMedium)
                val species = SpeciesCatalog.bySpecies(critter.species)
                Text(
                    "Level ${critter.level} · ${species?.displayName ?: critter.species} · " +
                        StageNames.forStage(critter.stage),
                    style = MaterialTheme.typography.bodyMedium,
                )
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
        Spacer(modifier = Modifier.height(8.dp))
        HarmonyChip(harmony = harmony, tier = harmonyTier)
    }
}

/** "Thriving Farm · 7 / 10 harmony" — what getting stronger is actually for, at a glance. */
@Composable
private fun HarmonyChip(harmony: Int, tier: HarmonyTier) {
    Surface(shape = RoundedCornerShape(50), color = SlumberLavender.copy(alpha = 0.18f)) {
        Text(
            "${tier.emoji} ${tier.name} · ${HarmonyRules.progressToNext(harmony)}",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
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

/** The chain, made obvious: what it is, what it pays, and the insurance you hold. */
@Composable
private fun StreakCard(
    streakDays: Int,
    multiplier: Double,
    freezes: Int,
    onBuyFreeze: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(if (streakDays > 0) "🔥" else "🕯", style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (streakDays > 0) "$streakDays-day streak" else "No streak yet",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    when {
                        streakDays == 0 -> "Claim today's chest to start the chain."
                        multiplier > 1.0 -> "Every claim pays ×${trimmed(multiplier)} right now."
                        else -> "Reach 3 days and every claim pays ×1.5."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    if (freezes > 0) {
                        "🧊 $freezes freeze${if (freezes == 1) "" else "s"} ready"
                    } else {
                        "No streak freeze — buy one to protect the chain."
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (freezes > 0) SlumberLavender else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (freezes == 0) {
                OutlinedButton(onClick = onBuyFreeze) { Text("🛍") }
            }
        }
    }
}

private fun trimmed(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()

/** "Sep 19" reads like a day of the week; an ISO string reads like a database row. */
private val REPAIR_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.US)

/** Only shown while a seasonal event is actually running — the common state is no banner at all. */
@Composable
private fun ActiveEventBanner(event: FarmEvent, daysLeft: Int, onOpenChallenges: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SlumberLavender.copy(alpha = 0.18f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(event.emoji, style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(event.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    event.blurb,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    if (daysLeft == 0) "Last day!" else "$daysLeft day${if (daysLeft == 1) "" else "s"} left",
                    style = MaterialTheme.typography.labelMedium,
                    color = SlumberLavender,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            TextButton(onClick = onOpenChallenges) { Text("View") }
        }
    }
}

/**
 * The second chance: what happened, the honest price, and two buttons that are both fine to tap.
 * "Let it go" is never styled or worded as a loss — the whole point of this card is that skipping
 * it is a completely reasonable choice.
 */
@Composable
private fun StreakRepairCard(option: RepairOption, onRepair: () -> Unit, onLetItGo: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🩹", style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Looks like ${option.missedDate.format(REPAIR_DATE)} got missed",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "For ${option.cost} coins you can protect the ${option.streakDaysRestored}-day chain. " +
                    "This only patches the streak — it pays no coins, treats or sparks for the day itself.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onRepair) { Text("Repair the streak") }
                OutlinedButton(onClick = onLetItGo) { Text("Let it go") }
            }
        }
    }
}

@Composable
private fun CritterStage(
    critter: CritterEntity,
    hatId: String?,
    treats: Int,
    insight: Insight?,
    onFeed: () -> Unit,
    onOpenShop: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = PastureGreenLight),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CritterCanvas(
                mood = critter.mood,
                speciesKey = critter.species,
                stage = critter.stage,
                hatId = hatId,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Hunger ${critter.hunger} · Happiness ${critter.happiness}",
                style = MaterialTheme.typography.labelMedium,
            )
            if (insight != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "${critter.name} says: ${insight.text}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onFeed, enabled = treats > 0) {
                    Text(if (treats > 0) "🍬 Feed Sprout ($treats)" else "🍬 No treats yet")
                }
                OutlinedButton(onClick = onOpenShop) { Text("Hats") }
            }
            if (treats == 0) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "One workout earns 5 treats for Sprout.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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
            "Nothing linked yet — the targets above are waiting for real data."
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Zzz... the farm is quiet", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.width(8.dp))
                Text("💤", color = EmberOrange)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(12.dp))
            Button(onClick = onOpenOnboarding) { Text("Connect My Health") }
        }
    }
}
