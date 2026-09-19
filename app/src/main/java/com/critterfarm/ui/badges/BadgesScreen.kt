package com.critterfarm.ui.badges

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.critterfarm.data.BadgeProgress
import com.critterfarm.data.BadgeTier
import com.critterfarm.ui.theme.CoinGold
import com.critterfarm.ui.theme.DormantGray
import com.critterfarm.ui.theme.DormantGraySurface
import com.critterfarm.ui.theme.SlumberLavender
import com.critterfarm.ui.theme.TreatBrown

private fun BadgeTier.color(): Color = when (this) {
    BadgeTier.BRONZE -> TreatBrown
    BadgeTier.SILVER -> SlumberLavender
    BadgeTier.GOLD -> CoinGold
}

private fun BadgeTier.label(): String = when (this) {
    BadgeTier.BRONZE -> "Bronze"
    BadgeTier.SILVER -> "Silver"
    BadgeTier.GOLD -> "Gold"
}

/**
 * Every trophy the farm can award: how many are unlocked, and — the whole point — exactly how
 * close every locked one is. Works with zero Health Connect permissions: every badge simply
 * reads 0/N rather than erroring, and the copy stays encouraging either way.
 */
@Composable
fun BadgesScreen(uiState: BadgesUiState, onBack: () -> Unit) {
    Scaffold { padding ->
        if (uiState.isLoading) {
            LoadingState(padding)
            return@Scaffold
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text("Badges", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            "${uiState.unlockedCount} / ${uiState.totalCount} badges",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = onBack) { Text("← Farm") }
                }
            }

            if (uiState.badges.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        "No badges yet — every day you log is a step toward the first one.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            items(uiState.badges, span = { GridItemSpan(1) }) { progress ->
                BadgeCard(progress)
            }
        }
    }
}

@Composable
private fun LoadingState(padding: PaddingValues) {
    Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text("Counting up the trophies...", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun BadgeCard(progress: BadgeProgress) {
    val tierColor = progress.badge.tier.color()
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (progress.unlocked) tierColor.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surface,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp).fillMaxWidth()) {
            Text(
                progress.badge.emoji,
                style = MaterialTheme.typography.headlineMedium,
                color = if (progress.unlocked) Color.Unspecified else DormantGray,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                progress.badge.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = if (progress.unlocked) MaterialTheme.colorScheme.onSurface else DormantGray,
            )
            Text(
                progress.badge.blurb,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                progress.badge.tier.label(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = tierColor,
            )
            Spacer(modifier = Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { (progress.current.toFloat() / progress.target.toFloat()).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(6.dp),
                color = if (progress.unlocked) tierColor else DormantGray,
                trackColor = DormantGraySurface,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                if (progress.unlocked) "Unlocked · ${progress.progressText}" else progress.progressText,
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Start,
                color = if (progress.unlocked) tierColor else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
