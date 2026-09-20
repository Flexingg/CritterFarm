package com.critterfarm.ui.decor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.critterfarm.data.DecorCatalog
import com.critterfarm.data.DecorItem
import com.critterfarm.data.EventCatalog
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Buy decorations and lay out the farm.
 *
 * Deliberately one screen: choosing something and seeing where it lands are the same thought, so
 * splitting them across a shop page and a separate editor would just add a step between wanting a
 * pond and having one.
 */
@Composable
fun DecorScreen(
    uiState: DecorUiState,
    onIntent: (DecorIntent) -> Unit,
    onBack: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.message) {
        val message = uiState.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        onIntent(DecorIntent.DismissMessage)
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(data, shape = MaterialTheme.shapes.large)
            }
        },
    ) { padding ->
        if (uiState.isLoading) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
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
                        Text("Decorate the farm", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            "${uiState.coins} coins · ${uiState.placedCount} placed",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = onBack) { Text("← Farm") }
                }
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                FarmScene(
                    critter = uiState.critter,
                    hatId = uiState.equippedHatId,
                    placements = uiState.placements,
                    selectedItemId = uiState.selectedItemId,
                    onCellTap = { onIntent(DecorIntent.TapCell(it)) },
                    rows = uiState.rows,
                )
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = if (uiState.selectedItemId == null) {
                        "Pick something below, then tap a square. Tapping something already standing " +
                            "clears it — clearing is free, but coins are not refunded."
                    } else {
                        val chosen = DecorCatalog.item(uiState.selectedItemId)
                        "Placing: ${chosen?.emoji} ${chosen?.name} (${chosen?.price} coins each). " +
                            "Tap a square to put it down."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                Spacer(Modifier.height(4.dp))
            }

            uiState.eventItems.takeIf { it.isNotEmpty() }?.let { eventItems ->
                val event = EventCatalog.byId(eventItems.first().eventId!!)
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EventSectionHeader(
                        name = event?.name ?: "Event",
                        emoji = event?.emoji ?: "✨",
                        daysLeft = event?.let { ChronoUnit.DAYS.between(LocalDate.now(), it.end).toInt().coerceAtLeast(0) } ?: 0,
                    )
                }
                items(eventItems) { item ->
                    DecorCard(
                        item = item,
                        coins = uiState.coins,
                        standing = uiState.countsByItem[item.id] ?: 0,
                        selected = uiState.selectedItemId == item.id,
                        onSelect = {
                            onIntent(
                                DecorIntent.SelectItem(
                                    if (uiState.selectedItemId == item.id) null else item.id,
                                ),
                            )
                        },
                    )
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text("Everyday decorations", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }
            }

            items(uiState.normalItems) { item ->
                DecorCard(
                    item = item,
                    coins = uiState.coins,
                    standing = uiState.countsByItem[item.id] ?: 0,
                    selected = uiState.selectedItemId == item.id,
                    onSelect = {
                        onIntent(
                            DecorIntent.SelectItem(
                                if (uiState.selectedItemId == item.id) null else item.id,
                            ),
                        )
                    },
                )
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = "Decorations are cosmetic and always will be — a pretty farm is not " +
                        "progress, and nothing here changes what the game rewards.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun EventSectionHeader(name: String, emoji: String, daysLeft: Int) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(emoji, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.width(6.dp))
            Text("$name exclusives", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        }
        Text(
            if (daysLeft == 0) "Last day to grab these!" else "$daysLeft day${if (daysLeft == 1) "" else "s"} left to grab these",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DecorCard(
    item: DecorItem,
    coins: Int,
    standing: Int,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    val affordable = coins >= item.price
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(item.emoji, fontSize = 30.sp)
                if (standing > 0) {
                    Text(
                        text = "×$standing",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Text(item.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                item.blurb,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "${item.price} 🪙",
                style = MaterialTheme.typography.labelLarge,
            )
            when {
                selected -> Button(onClick = onSelect, modifier = Modifier.fillMaxWidth()) {
                    Text("Selected ✓")
                }
                affordable -> OutlinedButton(onClick = onSelect, modifier = Modifier.fillMaxWidth()) {
                    Text("Place this")
                }
                else -> OutlinedButton(
                    onClick = onSelect,
                    enabled = false,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("${item.price - coins} more coins")
                }
            }
        }
    }
}
