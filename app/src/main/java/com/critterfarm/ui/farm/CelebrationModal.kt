package com.critterfarm.ui.farm

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.critterfarm.data.ClaimResult

/**
 * The dopamine moment: today's spoils tallying up with an arcade count-up, shown after
 * "Claim Daily Turn" succeeds.
 */
@Composable
fun CelebrationModal(result: ClaimResult.Claimed, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Nice!")
            }
        },
        title = {
            Text(
                text = "Daily Turn Claimed! 🎉",
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "Sprout tallied up the day's adventures:",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
                SpoilRow(emoji = "✨", label = "XP", target = result.xpEarned)
                SpoilRow(emoji = "🪙", label = "Coins", target = result.coinsEarned)
                SpoilRow(emoji = "🍬", label = "Treats", target = result.treatsEarned)
                SpoilRow(emoji = "🔮", label = "Mana Sparks", target = result.manaSparksEarned)
                result.supportiveMessage?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
    )
}

@Composable
private fun SpoilRow(emoji: String, label: String, target: Int) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(text = "$emoji $label", style = MaterialTheme.typography.bodyLarge)
        AnimatedCounter(target = target, style = MaterialTheme.typography.titleLarge)
    }
}
