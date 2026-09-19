package com.critterfarm.ui.barn

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.critterfarm.data.EvolutionRequirement
import com.critterfarm.data.SpeciesCatalog
import com.critterfarm.data.local.CritterEntity
import com.critterfarm.data.local.CritterMood
import com.critterfarm.ui.theme.DormantGray
import com.critterfarm.ui.theme.PastureGreenLight
import com.critterfarm.ui.theme.SproutGreen

/**
 * The collection loop: the critters you own, the species left to hatch, and the active
 * critter's evolution progress. Works with zero Health Connect permissions — unlock and
 * evolution progress simply read as "0 of N" rather than erroring, and the copy stays
 * encouraging either way.
 */
@Composable
fun BarnScreen(
    uiState: BarnUiState,
    onIntent: (BarnIntent) -> Unit,
    onBack: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.message) {
        val message = uiState.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        onIntent(BarnIntent.DismissMessage)
    }

    uiState.evolutionCelebration?.let { (critter, newStage) ->
        EvolutionCelebrationDialog(
            critter = critter,
            newStage = newStage,
            onDismiss = { onIntent(BarnIntent.DismissCelebration) },
        )
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(snackbarData = data, shape = RoundedCornerShape(16.dp))
            }
        },
    ) { padding ->
        if (uiState.isLoading || uiState.critters.isEmpty()) {
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
                    Text("The Barn", style = MaterialTheme.typography.headlineSmall)
                    TextButton(onClick = onBack) { Text("← Farm") }
                }
            }

            item(span = { GridItemSpan(maxLineSpan) }) { SectionTitle("Your critters") }
            items(uiState.critters, span = { GridItemSpan(1) }) { critter ->
                CritterCard(
                    critter = critter,
                    isActive = critter.id == uiState.activeCritterId,
                    onSetActive = { onIntent(BarnIntent.SetActive(critter.id)) },
                )
            }

            item(span = { GridItemSpan(maxLineSpan) }) { SectionTitle("Evolve") }
            item(span = { GridItemSpan(maxLineSpan) }) {
                EvolveCard(
                    requirement = uiState.evolutionRequirement,
                    onEvolve = { onIntent(BarnIntent.EvolveActive) },
                )
            }

            item(span = { GridItemSpan(maxLineSpan) }) { SectionTitle("Hatch a new critter") }
            items(uiState.hatchCards, span = { GridItemSpan(1) }) { card ->
                HatchCard(card = card, onHatch = { onIntent(BarnIntent.Hatch(card.species.key)) })
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
            Text("Waking up the barn...", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 8.dp),
    )
}

private fun moodEmoji(mood: CritterMood): String = when (mood) {
    CritterMood.CELEBRATING -> "🎉"
    CritterMood.BOUNCING_HAPPY -> "😄"
    CritterMood.SLUGGISH_TIRED -> "😴"
    CritterMood.NEUTRAL -> "🙂"
}

@Composable
private fun CritterCard(critter: CritterEntity, isActive: Boolean, onSetActive: () -> Unit) {
    val species = SpeciesCatalog.bySpecies(critter.species)
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) PastureGreenLight else MaterialTheme.colorScheme.surface,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background((species?.primaryColor ?: SproutGreen).copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(species?.emoji ?: "🟢", style = MaterialTheme.typography.headlineMedium)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(critter.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                "Lv ${critter.level} · Stage ${critter.stage} · ${moodEmoji(critter.mood)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (isActive) {
                Text(
                    "On the farm ✓",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = SproutGreen,
                )
            } else {
                OutlinedButton(onClick = onSetActive) { Text("Put on the farm") }
            }
        }
    }
}

@Composable
private fun EvolveCard(requirement: EvolutionRequirement?, onEvolve: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (requirement == null) {
                Text(
                    "Fully evolved — your active critter has reached its final stage.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                return@Column
            }
            Text(
                "Stage ${requirement.stage} is within reach",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(requirement.progressText, style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(10.dp))
            Button(onClick = onEvolve, enabled = requirement.met) {
                Text(if (requirement.met) "Evolve!" else "Not ready yet")
            }
        }
    }
}

@Composable
private fun HatchCard(card: HatchCardUi, onHatch: () -> Unit) {
    val species = card.species
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(species.emoji, style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(species.displayName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                species.blurb,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(6.dp))
            val costLabel = if (species.hatchCostSparks == 0) "Free" else "${species.hatchCostSparks} 🔮"
            Text(costLabel, style = MaterialTheme.typography.labelLarge)
            Spacer(modifier = Modifier.height(6.dp))
            when {
                !card.unlocked -> {
                    LinearProgressIndicator(
                        progress = { 0f },
                        modifier = Modifier.fillMaxWidth().height(6.dp),
                        color = DormantGray,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        species.unlockNote,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        card.progressText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Button(onClick = {}, enabled = false) { Text("Locked") }
                }

                card.affordable -> Button(onClick = onHatch) { Text("Hatch") }

                else -> Button(onClick = {}, enabled = false) {
                    Text("${card.shortfall} more 🔮")
                }
            }
        }
    }
}

@Composable
private fun EvolutionCelebrationDialog(critter: CritterEntity, newStage: Int, onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { Button(onClick = onDismiss) { Text("Amazing!") } },
        title = {
            Text(
                "Evolved! 🎉",
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        text = {
            Text(
                "${critter.name} just reached Stage $newStage — the consistency behind it is the real prize.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    )
}
