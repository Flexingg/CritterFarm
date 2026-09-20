package com.critterfarm.ui.challenges

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.critterfarm.data.ChallengeSpec
import com.critterfarm.ui.theme.CoinGold
import com.critterfarm.ui.theme.SlumberLavender

/**
 * The medium-term goal screen: an active-event banner up top if one is running, then weekly
 * challenges, then monthly ones. Every card is a countdown, a progress bar, and a Claim button
 * once the target is met — the same layout regardless of which window the card came from, since
 * the engine underneath treats them identically.
 */
@Composable
fun ChallengesScreen(
    uiState: ChallengesUiState,
    onIntent: (ChallengesIntent) -> Unit,
    onBack: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.message) {
        val message = uiState.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        onIntent(ChallengesIntent.DismissMessage)
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(data, shape = MaterialTheme.shapes.large)
            }
        },
    ) { padding ->
        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Rounding up this week's goals...", style = MaterialTheme.typography.titleMedium)
                }
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Challenges", style = MaterialTheme.typography.headlineSmall)
                    TextButton(onClick = onBack) { Text("← Farm") }
                }
            }

            uiState.activeEvent?.let { event ->
                item {
                    EventBanner(
                        name = event.name,
                        emoji = event.emoji,
                        blurb = event.blurb,
                        daysLeft = uiState.eventDaysLeft,
                    )
                }
                uiState.eventChallenge?.let { card ->
                    item {
                        SectionHeader("This event")
                    }
                    item { ChallengeCard(card, onClaim = { onIntent(ChallengesIntent.Claim(it)) }) }
                }
            }

            item { SectionHeader("This week") }
            if (uiState.weekly.isEmpty()) {
                item { EmptySection("No weekly challenges right now — check back soon.") }
            }
            items(uiState.weekly) { card ->
                ChallengeCard(card, onClaim = { onIntent(ChallengesIntent.Claim(it)) })
            }

            item { SectionHeader("This month") }
            if (uiState.monthly.isEmpty()) {
                item { EmptySection("No monthly challenges right now — check back soon.") }
            }
            items(uiState.monthly) { card ->
                ChallengeCard(card, onClaim = { onIntent(ChallengesIntent.Claim(it)) })
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun EmptySection(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun EventBanner(name: String, emoji: String, blurb: String, daysLeft: Int) {
    Card(colors = CardDefaults.cardColors(containerColor = SlumberLavender.copy(alpha = 0.18f))) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(emoji, style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.width(8.dp))
                Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(blurb, style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                if (daysLeft == 0) "Last day!" else "$daysLeft day${if (daysLeft == 1) "" else "s"} left",
                style = MaterialTheme.typography.labelMedium,
                color = SlumberLavender,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun ChallengeCard(card: ChallengeCardState, onClaim: (String) -> Unit) {
    val progress = card.progress
    val spec: ChallengeSpec = progress.spec
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(spec.emoji, style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(spec.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }
                Text(
                    if (progress.daysLeft == 0) "Last day" else "${progress.daysLeft}d left",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(spec.blurb, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { progress.fraction },
                modifier = Modifier.fillMaxWidth().height(6.dp),
                color = if (progress.completed) CoinGold else MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(progress.progressText, style = MaterialTheme.typography.labelMedium)
                when {
                    card.claimed -> Text(
                        "✓ Claimed",
                        style = MaterialTheme.typography.labelMedium,
                        color = CoinGold,
                        fontWeight = FontWeight.Bold,
                    )
                    progress.completed -> Button(onClick = { onClaim(spec.id) }) { Text("Claim") }
                    else -> Text(
                        rewardSummary(spec),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun rewardSummary(spec: ChallengeSpec): String {
    val parts = buildList {
        if (spec.rewardCoins > 0) add("${spec.rewardCoins} 🪙")
        if (spec.rewardTreats > 0) add("${spec.rewardTreats} 🍬")
        if (spec.rewardSparks > 0) add("${spec.rewardSparks} 🔮")
    }
    return "Pays " + parts.joinToString(" ")
}
