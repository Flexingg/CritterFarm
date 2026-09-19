package com.critterfarm.ui.shop

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.critterfarm.data.ShopCatalog
import com.critterfarm.data.ShopCurrency
import com.critterfarm.data.ShopItem
import com.critterfarm.ui.theme.CoinGold
import com.critterfarm.ui.theme.DormantGray
import com.critterfarm.ui.theme.PastureGreenLight
import com.critterfarm.ui.theme.SlumberLavender
import com.critterfarm.ui.theme.SproutGreen

/**
 * What the currencies are for.
 *
 * The screen is deliberately blunt: coins buy hats and streak insurance, treats feed Sprout,
 * Mana Sparks buy the prestige hat. Nothing on this screen can buy a level, a skipped workout,
 * or an unsafely large deficit — health progress is earned by behaviour or not at all.
 */
@Composable
fun ShopScreen(
    uiState: ShopUiState,
    onIntent: (ShopIntent) -> Unit,
    onBack: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.message) {
        val message = uiState.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        onIntent(ShopIntent.DismissMessage)
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(snackbarData = data, shape = RoundedCornerShape(16.dp))
            }
        },
    ) { padding ->
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
                    Text("The Barn Shop", style = MaterialTheme.typography.headlineSmall)
                    TextButton(onClick = onBack) { Text("← Farm") }
                }
            }

            item(span = { GridItemSpan(maxLineSpan) }) { PursesRow(uiState) }

            item(span = { GridItemSpan(maxLineSpan) }) {
                // The "why" matters as much as the prices — a currency nobody understands is
                // the problem this shop exists to fix.
                Text(
                    "Coins and Mana Sparks buy hats. Treats feed Sprout. " +
                        "Nothing here buys health progress — that has to be earned.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            item(span = { GridItemSpan(maxLineSpan) }) { SectionTitle("Cosmetics") }
            items(ShopCatalog.HATS, span = { GridItemSpan(1) }) { hat ->
                HatCard(item = hat, uiState = uiState, onIntent = onIntent)
            }

            item(span = { GridItemSpan(maxLineSpan) }) { SectionTitle("Streak insurance") }
            item(span = { GridItemSpan(maxLineSpan) }) { FreezeCard(uiState, onIntent) }
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

@Composable
private fun PursesRow(uiState: ShopUiState) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Purse(emoji = "🪙", value = uiState.coins, label = "coins", accent = CoinGold)
        Purse(emoji = "🍬", value = uiState.treats, label = "treats", accent = SlumberLavender)
        Purse(emoji = "🔮", value = uiState.manaSparks, label = "mana", accent = SproutGreen)
    }
}

@Composable
private fun Purse(emoji: String, value: Int, label: String, accent: androidx.compose.ui.graphics.Color) {
    Surface(shape = RoundedCornerShape(16.dp), color = accent.copy(alpha = 0.18f)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(emoji)
            Spacer(modifier = Modifier.padding(horizontal = 3.dp))
            Column {
                Text("$value", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(label, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun HatCard(item: ShopItem, uiState: ShopUiState, onIntent: (ShopIntent) -> Unit) {
    val owned = item.id in uiState.ownedHatIds
    val equipped = uiState.equippedHatId == item.id
    val affordable = uiState.canAfford(item)

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (equipped) PastureGreenLight else MaterialTheme.colorScheme.surface,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(item.emoji, style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(item.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                item.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = priceLabel(item),
                style = MaterialTheme.typography.labelLarge,
                color = if (owned) DormantGray else MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(6.dp))
            when {
                equipped -> OutlinedButton(onClick = { onIntent(ShopIntent.Equip(null)) }) {
                    Text("Wearing ✓")
                }

                owned -> Button(onClick = { onIntent(ShopIntent.Equip(item.id)) }) { Text("Wear") }

                affordable -> Button(onClick = { onIntent(ShopIntent.Buy(item.id)) }) {
                    Text("Buy")
                }

                else -> Button(onClick = {}, enabled = false) {
                    Text("${uiState.shortfallFor(item)} more")
                }
            }
        }
    }
}

private fun priceLabel(item: ShopItem): String = when (item.currency) {
    ShopCurrency.COINS -> "${"%,d".format(item.price)} 🪙"
    ShopCurrency.MANA_SPARKS -> "${item.price} 🔮"
}

@Composable
private fun FreezeCard(uiState: ShopUiState, onIntent: (ShopIntent) -> Unit) {
    val item = ShopCatalog.STREAK_FREEZE
    val affordable = uiState.canAfford(item)
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(item.emoji, style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.padding(horizontal = 6.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(item.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    item.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Holding: ${uiState.streakFreezes} · ${priceLabel(item)} each",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Button(
                onClick = { onIntent(ShopIntent.Buy(item.id)) },
                enabled = affordable,
            ) {
                Text(if (affordable) "Buy" else "Need ${uiState.shortfallFor(item)}")
            }
        }
    }
}
